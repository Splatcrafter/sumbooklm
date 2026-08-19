package de.pfoertner.assessment.sumbooklm;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.embedding.SegmentMetadata;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentRepository;
import de.pfoertner.assessment.sumbooklm.workspace.source.IndexRestoreJob;
import de.pfoertner.assessment.sumbooklm.workspace.source.OrphanSegmentCollector;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises what keeps the retrieval index and the database saying the same thing.
 *
 * <h2>Simulating a Restart</h2>
 * A restart cannot be performed inside a test that runs in the application it would restart. What a
 * restart does to this application is empty the vector store while the database keeps every source,
 * and that is reproduced exactly: the store is emptied through the bean, and the rebuild is asked to
 * put it back.
 *
 * <h2>Its Own Database</h2>
 * The class runs against a database of its own, because the rebuild deliberately spans every account
 * there is. Sharing the database of the other suites would make it rebuild their sources as well,
 * including the addresses they use to provoke failures.
 *
 * <h2>Counting Segments</h2>
 * The assertions count the segments stored for one source rather than asking a question about it.
 * That is the state these mechanisms are responsible for; whether an answer can be produced from it
 * is what the chat suite already covers.
 *
 * <h2>A Row That Went Without Its Segments</h2>
 * The pass that collects segments whose source is gone is exercised by removing a row through the
 * repository rather than through the interface. That is precisely the situation it exists for: the
 * removal of the segments is what follows a deletion through the service, so a deletion that never
 * went through it leaves exactly the state nothing else would clean up.
 *
 * <h2>Deletions Belong Here Too</h2>
 * Removing the segments of a deleted source happens after the transaction that deleted it has
 * committed, through an event. Nothing about that is visible in a response, so the only way to state
 * that it happens at all is to count what the store holds afterwards.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:sumbooklm-restore;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class IndexRestoreIntegrationTest {

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
     * Greatest number of segments one search returns, chosen far above what the documents produce.
     */
    private static final int SEARCH_LIMIT = 1_000;

    /**
     * Text uploaded as a document. Three paragraphs give the splitter boundaries to cut on, so that
     * the source produces more than one segment and a duplicated run would be visible as a count.
     */
    private static final String DOCUMENT_TEXT = """
            The first law of thermodynamics states that the internal energy of an isolated system is
            constant. Energy is neither created nor destroyed, only transferred between a system and
            its surroundings.

            The second law introduces entropy. In an isolated system entropy never decreases, which
            is what gives a direction to processes that the first law alone would allow to run either
            way.

            A heat engine converts thermal energy into work. Its efficiency is bounded by the
            temperatures of the reservoirs it operates between, and no engine can exceed that bound.
            """;

    /**
     * Client bound to the running server, configured to report failing statuses instead of throwing.
     */
    private final RestClient client;

    /**
     * Store the segments of every source live in, emptied to reproduce a restart.
     */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Model the search embedding is computed with.
     */
    private final EmbeddingModel embeddingModel;

    /**
     * Rebuild the tests ask to run.
     */
    private final IndexRestoreJob indexRestoreJob;

    /**
     * Pass over the store the tests ask to run.
     */
    private final OrphanSegmentCollector orphanSegmentCollector;

    /**
     * Data access used to remove a row the way nothing in the application does.
     */
    private final SourceDocumentRepository sourceDocumentRepository;

    /**
     * Creates the test class and binds the client to the port the server was started on.
     *
     * @param port                     port the embedded server listens on
     * @param embeddingStore           store the segments of every source live in
     * @param embeddingModel           model the search embedding is computed with
     * @param indexRestoreJob          rebuild the tests ask to run
     * @param orphanSegmentCollector   pass over the store the tests ask to run
     * @param sourceDocumentRepository data access used to remove a row the way nothing in the
     *                                 application does
     */
    IndexRestoreIntegrationTest(@Autowired @LocalServerPort final int port,
                                @Autowired final EmbeddingStore<TextSegment> embeddingStore,
                                @Autowired final EmbeddingModel embeddingModel,
                                @Autowired final IndexRestoreJob indexRestoreJob,
                                @Autowired final OrphanSegmentCollector orphanSegmentCollector,
                                @Autowired final SourceDocumentRepository sourceDocumentRepository) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.indexRestoreJob = indexRestoreJob;
        this.orphanSegmentCollector = orphanSegmentCollector;
        this.sourceDocumentRepository = sourceDocumentRepository;
    }

    /**
     * Verifies that the segments of a source come back after the store has lost them, that the source
     * keeps reporting the same stage and token count throughout, and that nothing had to be uploaded
     * or read again for it.
     */
    @Test
    void segmentsAreRebuiltAfterTheStoreHasLostThem() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(uploadFile(accessToken, notebookId, "thermodynamics.txt", DOCUMENT_TEXT));
        final Map<String, Object> indexed = awaitSettled(accessToken, notebookId, sourceId);
        assertThat(indexed.get("status")).isEqualTo("READY");

        final int segments = segmentsOf(sourceId);
        assertThat(segments).isPositive();

        this.embeddingStore.removeAll();
        assertThat(segmentsOf(sourceId)).isZero();
        assertThat(readSource(accessToken, notebookId, sourceId).get("status"))
                .describedAs("a source whose segments are gone still reports the stage it reached")
                .isEqualTo("READY");

        assertThat(this.indexRestoreJob.restore())
                .describedAs("the rebuild spans every source there is, so it reports at least this one")
                .isPositive();

        assertThat(segmentsOf(sourceId)).isEqualTo(segments);
        final Map<String, Object> rebuilt = readSource(accessToken, notebookId, sourceId);
        assertThat(rebuilt.get("status")).isEqualTo("READY");
        assertThat(rebuilt.get("tokenCount")).isEqualTo(indexed.get("tokenCount"));
        assertThat(rebuilt.get("displayName")).isEqualTo("thermodynamics.txt");
        assertThat(rebuilt.get("indexedAt"))
                .describedAs("a rebuild is not a reading, so the moment it was read stays")
                .isEqualTo(indexed.get("indexedAt"));
    }

    /**
     * Verifies that reading a source that is already indexed replaces its segments instead of adding
     * a second copy of every paragraph, and that the moment it was read moves with the reading.
     */
    @Test
    void readingASourceAgainReplacesItsSegments() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(uploadFile(accessToken, notebookId, "physics.txt", DOCUMENT_TEXT));
        final Object readAt = awaitSettled(accessToken, notebookId, sourceId).get("indexedAt");
        final int segments = segmentsOf(sourceId);
        assertThat(readAt).isNotNull();

        final ResponseEntity<Map<String, Object>> response = refresh(accessToken, notebookId, sourceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(requireBody(response).get("status")).isEqualTo("UPLOADED");
        final Map<String, Object> reread = awaitSettled(accessToken, notebookId, sourceId);
        assertThat(reread.get("status")).isEqualTo("READY");
        assertThat(reread.get("indexedAt")).isNotEqualTo(readAt);
        assertThat(segmentsOf(sourceId)).isEqualTo(segments);
    }

    /**
     * Verifies that a source which could not be read is read again when it is asked for, and that it
     * ends in the failed stage once more while the address stays unreachable.
     */
    @Test
    void aFailedSourceIsReadAgainWhenItIsRefreshed() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(addWebPage(accessToken, notebookId, "http://127.0.0.1:9/unreachable"));
        assertThat(awaitSettled(accessToken, notebookId, sourceId).get("status")).isEqualTo("ERROR");

        assertThat(refresh(accessToken, notebookId, sourceId).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        final Map<String, Object> failed = awaitSettled(accessToken, notebookId, sourceId);
        assertThat(failed.get("status")).isEqualTo("ERROR");
        assertThat(failed.get("failure")).isEqualTo("BLOCKED");
        assertThat(segmentsOf(sourceId)).isZero();
    }

    /**
     * Verifies that a source of another account cannot be indexed again, and that a source of another
     * notebook of the same account cannot either.
     */
    @Test
    void sourcesOfAnotherAccountCannotBeReadAgain() {
        final String owner = registerAccount();
        final String stranger = registerAccount();
        final String notebookId = createNotebook(owner);
        final String otherNotebookId = createNotebook(owner);
        final String sourceId = idOf(uploadFile(owner, notebookId, "owned.txt", DOCUMENT_TEXT));

        assertThat(refresh(stranger, notebookId, sourceId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refresh(owner, otherNotebookId, sourceId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refresh(owner, notebookId, UUID.randomUUID().toString()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that deleting a source takes its segments out of the store, which happens after the
     * deleting transaction has committed rather than inside it.
     */
    @Test
    void deletingASourceRemovesItsSegments() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(uploadFile(accessToken, notebookId, "removable.txt", DOCUMENT_TEXT));
        awaitSettled(accessToken, notebookId, sourceId);
        assertThat(segmentsOf(sourceId)).isPositive();

        final ResponseEntity<Void> response = this.client.delete()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/" + sourceId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(segmentsOf(sourceId)).isZero();
    }

    /**
     * Verifies that deleting a notebook takes the segments of every source it held out of the store.
     */
    @Test
    void deletingANotebookRemovesTheSegmentsOfItsSources() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(uploadFile(accessToken, notebookId, "doomed.txt", DOCUMENT_TEXT));
        awaitSettled(accessToken, notebookId, sourceId);
        assertThat(segmentsOf(sourceId)).isPositive();

        final ResponseEntity<Void> response = this.client.delete()
                .uri("/api/v1/notebooks/" + notebookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(segmentsOf(sourceId)).isZero();
    }

    /**
     * Verifies that a pass leaves the segments of a source that exists alone, and removes the segments
     * of one whose row is gone without anything having removed them.
     */
    @Test
    void segmentsOfARowThatIsGoneAreCollected() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sourceId = idOf(uploadFile(accessToken, notebookId, "orphans.txt", DOCUMENT_TEXT));
        awaitSettled(accessToken, notebookId, sourceId);
        final int segments = segmentsOf(sourceId);
        assertThat(segments).isPositive();

        this.orphanSegmentCollector.collect();
        assertThat(segmentsOf(sourceId))
                .describedAs("a pass removes nothing as long as the source is one of those that exist")
                .isEqualTo(segments);

        this.sourceDocumentRepository.deleteById(UUID.fromString(sourceId));
        assertThat(segmentsOf(sourceId))
                .describedAs("a row removed this way takes nothing with it")
                .isEqualTo(segments);

        this.orphanSegmentCollector.collect();
        assertThat(segmentsOf(sourceId)).isZero();
    }

    /**
     * Counts the segments the store holds for one source.
     *
     * @param sourceId identifier of the source to count the segments of
     * @return number of stored segments carrying that source in their metadata
     */
    private int segmentsOf(final String sourceId) {
        return this.embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(this.embeddingModel.embed("thermodynamics").content())
                        .maxResults(SEARCH_LIMIT)
                        .minScore(0.0)
                        .filter(MetadataFilterBuilder.metadataKey(SegmentMetadata.SOURCE_DOCUMENT_ID)
                                .isEqualTo(UUID.fromString(sourceId)))
                        .build())
                .matches()
                .size();
    }

    /**
     * Asks for one source to be read again.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to read again
     * @return the response of the endpoint
     */
    private ResponseEntity<Map<String, Object>> refresh(final String accessToken,
                                                        final String notebookId,
                                                        final String sourceId) {
        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/" + sourceId + "/refresh")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT);
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
        final Instant deadline = Instant.now().plus(INDEXING_TIMEOUT);
        Map<String, Object> source = readSource(accessToken, notebookId, sourceId);
        while (isRunning(source.get("status")) && Instant.now().isBefore(deadline)) {
            sleepBriefly();
            source = readSource(accessToken, notebookId, sourceId);
        }
        assertThat(source.get("status")).describedAs("indexing did not finish in time")
                .isNotIn("UPLOADED", "INDEXING");
        return source;
    }

    /**
     * Reports whether a stage means that a run is still going.
     *
     * @param status stage as the endpoint reported it
     * @return {@code true} while the source is waiting or being indexed
     */
    private static boolean isRunning(final Object status) {
        return "UPLOADED".equals(status) || "INDEXING".equals(status);
    }

    /**
     * Reads one source out of the collection of its notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to read
     * @return the source as the collection describes it
     */
    private Map<String, Object> readSource(final String accessToken,
                                           final String notebookId,
                                           final String sourceId) {
        final ResponseEntity<List<Map<String, Object>>> response = this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_ARRAY);
        final List<Map<String, Object>> sources = response.getBody();
        assertThat(sources).describedAs("the source collection carries no body").isNotNull();
        return sources.stream()
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
     * Uploads a file as a source.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the file is added to
     * @param fileName    name the file is uploaded under
     * @param content     text the file carries
     * @return the response of the endpoint
     */
    private ResponseEntity<Map<String, Object>> uploadFile(final String accessToken,
                                                           final String notebookId,
                                                           final String fileName,
                                                           final String content) {
        final MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Adds a web page as a source.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the page is added to
     * @param url         address of the page
     * @return the response of the endpoint
     */
    private ResponseEntity<Map<String, Object>> addWebPage(final String accessToken,
                                                           final String notebookId,
                                                           final String url) {
        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("url", url))
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Reads the identifier out of the response of an endpoint that stored a source.
     *
     * @param response response of the endpoint
     * @return the identifier of the stored source
     */
    private static String idOf(final ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) requireBody(response).get("id");
    }

    /**
     * Returns the body of a response that has to have one.
     *
     * @param response response to read
     * @return the body of the response
     */
    private static Map<String, Object> requireBody(final ResponseEntity<Map<String, Object>> response) {
        final Map<String, Object> body = response.getBody();
        assertThat(body).describedAs("the response carries no body").isNotNull();
        return body;
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
}
