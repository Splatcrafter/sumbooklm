package de.pfoertner.assessment.sumbooklm;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the source endpoints and the indexing pipeline against a running server.
 *
 * <h2>Coverage</h2>
 * The test drives an upload from the request that stores it to the state it reaches once the
 * background run has finished, which is the only way to observe that the run is triggered after the
 * storing transaction commits rather than during it. It also covers the rules that reject a source
 * before it is stored: an empty upload, content the notebook already holds, and a notebook that
 * belongs to somebody else.
 *
 * <h2>Waiting Instead of Sleeping</h2>
 * Indexing runs on another thread, so the assertions that describe its outcome poll the source until
 * it has left the stages that mean the run is still going. A fixed pause would either make the test
 * slow or make it depend on how fast the machine happens to be.
 *
 * <h2>Failures Without a Network</h2>
 * The failing path is provoked with an address inside the loopback range rather than with an
 * unreachable host, so the test asserts on the guard that refuses such addresses and needs no
 * network of its own.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SourceApiIntegrationTest {

    /**
     * Response shape of an endpoint returning one object.
     */
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    /**
     * Response shape of an endpoint returning a collection of objects.
     */
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {
            };

    /**
     * Password used for every account the tests create.
     */
    private static final String PASSWORD = "correct-horse-battery-staple";

    /**
     * Longest a test waits for a background indexing run to finish.
     */
    private static final Duration INDEXING_TIMEOUT = Duration.ofMinutes(2);

    /**
     * Text uploaded as a document. Two paragraphs are enough for the splitter to have a boundary to
     * cut on and for the model to report a token count above zero.
     */
    private static final String DOCUMENT_TEXT = """
            The first law of thermodynamics states that the internal energy of an isolated system is
            constant. Energy is neither created nor destroyed, only transferred between a system and
            its surroundings.

            The second law introduces entropy. In an isolated system entropy never decreases, which
            is what gives a direction to processes that the first law alone would allow to run either
            way.
            """;

    /**
     * Client bound to the running server, configured to report failing statuses instead of throwing.
     */
    private final RestClient client;

    /**
     * Creates the test class and binds the client to the port the server was started on.
     *
     * @param port port the embedded server listens on
     */
    SourceApiIntegrationTest(@Autowired @LocalServerPort final int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that an uploaded file is stored as an unindexed source, is counted by its notebook,
     * and reaches the indexed stage with a token count on its own.
     */
    @Test
    void uploadedFileIsStoredAndThenIndexed() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final ResponseEntity<Map<String, Object>> response =
                uploadFile(accessToken, notebookId, "thermodynamics.txt", DOCUMENT_TEXT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        final Map<String, Object> stored = requireBody(response);
        assertThat(stored.get("displayName")).isEqualTo("thermodynamics.txt");
        assertThat(stored.get("kind")).isEqualTo("FILE");
        assertThat(stored.get("origin")).isEqualTo("thermodynamics.txt");
        assertThat(stored.get("tokenCount")).isEqualTo(0);
        assertThat(stored.get("status")).isIn("UPLOADED", "INDEXING", "READY");
        assertThat(response.getHeaders().getLocation())
                .hasToString("/api/v1/notebooks/" + notebookId + "/sources/" + stored.get("id"));

        final Map<String, Object> indexed = awaitSettled(accessToken, notebookId, (String) stored.get("id"));
        assertThat(indexed.get("status")).isEqualTo("READY");
        assertThat((Integer) indexed.get("tokenCount")).isPositive();
        assertThat(indexed.get("displayName")).isEqualTo("thermodynamics.txt");
        assertThat(readNotebook(accessToken, notebookId).get("sourceCount")).isEqualTo(1);
    }

    /**
     * Verifies that adding a source refreshes the activity timestamp of its notebook, so that the
     * overview orders a notebook that received a source before one that did not.
     */
    @Test
    void addingASourceRefreshesTheActivityTimestampOfItsNotebook() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final Object before = readNotebook(accessToken, notebookId).get("lastActivityAt");

        uploadFile(accessToken, notebookId, "notes.txt", DOCUMENT_TEXT);

        assertThat(readNotebook(accessToken, notebookId).get("lastActivityAt")).isNotEqualTo(before);
    }

    /**
     * Verifies that an upload without bytes is refused instead of being stored as a source that
     * could only ever fail to be indexed.
     */
    @Test
    void emptyUploadIsRejected() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        assertThat(uploadFile(accessToken, notebookId, "empty.txt", "").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(requireBody(listSources(accessToken, notebookId))).isEmpty();
    }

    /**
     * Verifies that the same content is refused within one notebook and accepted in another one,
     * because a notebook is a workspace rather than a library.
     */
    @Test
    void theSameContentIsRefusedTwiceInOneNotebook() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String otherNotebookId = createNotebook(accessToken);

        assertThat(uploadFile(accessToken, notebookId, "first.txt", DOCUMENT_TEXT).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(uploadFile(accessToken, notebookId, "renamed.txt", DOCUMENT_TEXT).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(uploadFile(accessToken, otherNotebookId, "first.txt", DOCUMENT_TEXT).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Verifies that the same address is refused twice regardless of how it is spelled, and that an
     * address which is not an HTTP address is refused by the request validation.
     */
    @Test
    void theSameAddressIsRefusedTwiceAndNonHttpAddressesAreRejected() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        assertThat(addWebPage(accessToken, notebookId, "https://Example.org/article").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(addWebPage(accessToken, notebookId, "https://example.org/article#section").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(addWebPage(accessToken, notebookId, "file:///etc/passwd").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Verifies that an address inside the loopback range is stored but never retrieved, and that the
     * source it produced says that the address was refused rather than merely that something failed.
     */
    @Test
    void anAddressInsideThePrivateRangeEndsAsFailed() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final String sourceId = idOf(addWebPage(accessToken, notebookId, "http://127.0.0.1:9/secret"));

        final Map<String, Object> failed = awaitSettled(accessToken, notebookId, sourceId);
        assertThat(failed.get("status")).isEqualTo("ERROR");
        assertThat(failed.get("failure")).isEqualTo("BLOCKED");
    }

    /**
     * Verifies that a name which does not resolve is reported as unreachable rather than as refused,
     * because the two are different things for the user to act on.
     */
    @Test
    void anAddressThatCannotBeResolvedEndsAsUnreachable() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final String sourceId =
                idOf(addWebPage(accessToken, notebookId, "http://sumbooklm-no-such-host.invalid/page"));

        final Map<String, Object> failed = awaitSettled(accessToken, notebookId, sourceId);
        assertThat(failed.get("status")).isEqualTo("ERROR");
        assertThat(failed.get("failure")).isEqualTo("UNREACHABLE");
    }

    /**
     * Verifies that a file which parses but holds no text is reported as empty, and that a source
     * which was indexed reports no failure at all.
     */
    @Test
    void aFileWithoutTextEndsAsEmptyAndAnIndexedOneReportsNoFailure() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final String blank = idOf(uploadFile(accessToken, notebookId, "blank.txt", "   \n   \n  "));
        final Map<String, Object> failed = awaitSettled(accessToken, notebookId, blank);
        assertThat(failed.get("status")).isEqualTo("ERROR");
        assertThat(failed.get("failure")).isEqualTo("EMPTY");

        final String readable = idOf(uploadFile(accessToken, notebookId, "readable.txt", DOCUMENT_TEXT));
        final Map<String, Object> indexed = awaitSettled(accessToken, notebookId, readable);
        assertThat(indexed.get("status")).isEqualTo("READY");
        assertThat(indexed.get("failure")).isEqualTo("NONE");
    }

    /**
     * Verifies that a notebook of another account can neither be listed nor added to, and that the
     * answer does not distinguish it from a notebook that does not exist.
     */
    @Test
    void sourcesOfAnotherAccountAreNotReachable() {
        final String owner = registerAccount();
        final String stranger = registerAccount();
        final String notebookId = createNotebook(owner);
        final String sourceId = idOf(uploadFile(owner, notebookId, "owned.txt", DOCUMENT_TEXT));

        assertThat(listSourcesStatus(stranger, notebookId)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(uploadFile(stranger, notebookId, "taken.txt", DOCUMENT_TEXT).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteSource(stranger, notebookId, sourceId).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(listSourcesStatus(owner, UUID.randomUUID().toString())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that the endpoints are unreachable without an access token.
     */
    @Test
    void endpointsRequireAnAccessToken() {
        final ResponseEntity<List<Map<String, Object>>> response = this.client.get()
                .uri("/api/v1/notebooks/" + UUID.randomUUID() + "/sources")
                .retrieve()
                .toEntity(JSON_ARRAY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that a removed source disappears from its notebook, stops being counted and cannot be
     * removed twice.
     */
    @Test
    void deleteRemovesTheSource() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(uploadFile(accessToken, notebookId, "temporary.txt", DOCUMENT_TEXT));
        awaitSettled(accessToken, notebookId, sourceId);

        assertThat(deleteSource(accessToken, notebookId, sourceId).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(requireBody(listSources(accessToken, notebookId))).isEmpty();
        assertThat(readNotebook(accessToken, notebookId).get("sourceCount")).isEqualTo(0);
        assertThat(deleteSource(accessToken, notebookId, sourceId).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that removing a notebook removes its sources with it.
     */
    @Test
    void deletingANotebookRemovesItsSources() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(uploadFile(accessToken, notebookId, "temporary.txt", DOCUMENT_TEXT));
        awaitSettled(accessToken, notebookId, sourceId);

        final ResponseEntity<Void> deleted = this.client.delete()
                .uri("/api/v1/notebooks/" + notebookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listSourcesStatus(accessToken, notebookId)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Waits until a source has left the stages that mean an indexing run is still going.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to wait for
     * @return the source in the stage it settled in
     */
    private Map<String, Object> awaitSettled(final String accessToken,
                                             final String notebookId,
                                             final String sourceId) {
        final Predicate<Object> running = status -> "UPLOADED".equals(status) || "INDEXING".equals(status);
        final Instant deadline = Instant.now().plus(INDEXING_TIMEOUT);
        Map<String, Object> source = requireSource(accessToken, notebookId, sourceId);
        while (running.test(source.get("status")) && Instant.now().isBefore(deadline)) {
            sleepBriefly();
            source = requireSource(accessToken, notebookId, sourceId);
        }
        assertThat(source.get("status")).describedAs("indexing did not finish in time").isNotIn("UPLOADED", "INDEXING");
        return source;
    }

    /**
     * Reads one source out of the collection of its notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to read
     * @return the source as the collection describes it
     */
    private Map<String, Object> requireSource(final String accessToken,
                                              final String notebookId,
                                              final String sourceId) {
        return requireBody(listSources(accessToken, notebookId)).stream()
                .filter(source -> sourceId.equals(source.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The notebook holds no source " + sourceId));
    }

    /**
     * Pauses between two polls of a source.
     */
    private static void sleepBriefly() {
        try {
            Thread.sleep(Duration.ofMillis(200));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Waiting for the indexing run was interrupted", e);
        }
    }

    /**
     * Registers a new account and returns the access token it was issued.
     *
     * @return the access token of a freshly registered account
     */
    private String registerAccount() {
        final ResponseEntity<Map<String, Object>> response = this.client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", "user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "password", PASSWORD))
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<?, ?>) requireBody(response).get("tokens")).get("accessToken");
    }

    /**
     * Creates a notebook and returns its identifier.
     *
     * @param accessToken access token to present
     * @return the identifier of the created notebook
     */
    private String createNotebook(final String accessToken) {
        final ResponseEntity<Map<String, Object>> response = this.client.post()
                .uri("/api/v1/notebooks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Thermodynamics"))
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) requireBody(response).get("id");
    }

    /**
     * Reads one notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to read
     * @return the notebook as the endpoint describes it
     */
    private Map<String, Object> readNotebook(final String accessToken, final String notebookId) {
        return requireBody(this.client.get()
                .uri("/api/v1/notebooks/" + notebookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT));
    }

    /**
     * Uploads a file as a source.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the file is added to
     * @param fileName    name the file is uploaded under
     * @param content     text the file carries
     * @return the response of the upload endpoint
     */
    private ResponseEntity<Map<String, Object>> uploadFile(final String accessToken,
                                                           final String notebookId,
                                                           final String fileName,
                                                           final String content) {
        // The parts are assembled as a plain multi value map rather than with MultipartBodyBuilder,
        // whose reactive part support drags in a dependency this module does not carry.
        final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Adds a web page as a source.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the page is added to
     * @param address     address of the page
     * @return the response of the endpoint that adds a page
     */
    private ResponseEntity<Map<String, Object>> addWebPage(final String accessToken,
                                                           final String notebookId,
                                                           final String address) {
        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("url", address))
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Lists the sources of a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to list the sources of
     * @return the response of the listing endpoint
     */
    private ResponseEntity<List<Map<String, Object>>> listSources(final String accessToken,
                                                                  final String notebookId) {
        return this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_ARRAY);
    }

    /**
     * Reads only the status of the listing endpoint.
     *
     * <p>A refused listing answers with a problem detail rather than with a collection, so a caller
     * that expects a refusal must not ask for the body to be read as one.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to list the sources of
     * @return the status the listing endpoint answered with
     */
    private HttpStatusCode listSourcesStatus(final String accessToken, final String notebookId) {
        return this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    /**
     * Removes a source.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to remove
     * @return the response of the removal endpoint
     */
    private ResponseEntity<Void> deleteSource(final String accessToken,
                                              final String notebookId,
                                              final String sourceId) {
        return this.client.delete()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/" + sourceId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Reads the identifier out of the response of an endpoint that added a source.
     *
     * @param response response of an addition
     * @return the identifier of the added source
     */
    private static String idOf(final ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) requireBody(response).get("id");
    }

    /**
     * Returns the body of a response and fails when there is none.
     *
     * @param response response to read
     * @param <T>      shape of the body
     * @return the parsed body
     */
    private static <T> T requireBody(final ResponseEntity<T> response) {
        final T body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }
}
