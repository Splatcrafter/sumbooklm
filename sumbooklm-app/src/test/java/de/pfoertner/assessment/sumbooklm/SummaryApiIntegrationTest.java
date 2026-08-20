/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.pfoertner.assessment.sumbooklm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import com.sun.net.httpserver.HttpServer;

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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the summary endpoints against a running server.
 *
 * <h2>A Provider That Answers</h2>
 * A summary is one request with one response, so the whole path can be exercised against a provider
 * started by the test: the sources that were read, the instructions they are sent under, the text
 * that comes back and the notebook it is stored in. What the tests assert about the request is
 * therefore what a real provider would receive, which is the part this application is responsible
 * for.
 *
 * <h2>What Staleness Is Asserted Against</h2>
 * A summary is written from a set of sources and stored with a fingerprint of it. The test adds a
 * second source afterwards and reads the summary again, because that is the case the fingerprint
 * exists for and the one a reader is shown a way to act on.
 *
 * <h2>Isolation Is the Point</h2>
 * Every test registers its own account, and the notebook of another account answers as missing rather
 * than as forbidden, so that the endpoint is what enforces ownership.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SummaryApiIntegrationTest {

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
     * Text uploaded into the notebook that is summarised.
     */
    private static final String THERMODYNAMICS = """
            The second law of thermodynamics introduces entropy. In an isolated system entropy never
            decreases, which is what gives a direction to processes that the first law alone would
            allow to run either way.
            """;

    /**
     * Text uploaded as the source that arrives after a summary was written.
     */
    private static final String BAKING = """
            A sourdough starter is a culture of wild yeast and lactic acid bacteria. It is kept alive
            by discarding half of it and feeding the rest with flour and water.
            """;

    /**
     * Summary the provider of these tests writes.
     */
    private static final String WRITTEN_SUMMARY = "Entropy and what it does to the direction of things.";

    /**
     * Client bound to the running server, configured to report failing statuses instead of throwing.
     */
    private final RestClient client;

    /**
     * Creates the test class and binds the client to the port the server was started on.
     *
     * @param port port the embedded server listens on
     */
    SummaryApiIntegrationTest(@Autowired @LocalServerPort final int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that the endpoints are unreachable without an access token.
     */
    @Test
    void endpointsRequireAnAccessToken() {
        final ResponseEntity<Map<String, Object>> response = this.client.get()
                .uri("/api/v1/notebooks/" + UUID.randomUUID() + "/summary")
                .retrieve()
                .toEntity(JSON_OBJECT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that a notebook nobody has summarised answers with an empty text rather than with a
     * missing resource.
     */
    @Test
    void aNotebookWithoutASummaryAnswersWithAnEmptyOne() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final ResponseEntity<Map<String, Object>> response = readSummary(accessToken, notebookId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requireBody(response).get("text")).isEqualTo("");
        assertThat(requireBody(response).get("stale")).isEqualTo(false);
    }

    /**
     * Verifies that the summary of a notebook of another account is reported as missing.
     */
    @Test
    void theSummaryOfAForeignNotebookIsNotFound() {
        final String owner = registerAccount();
        final String notebookId = createNotebook(owner);
        final String stranger = registerAccount();

        assertThat(readSummary(stranger, notebookId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that a request without model settings is refused before anything is read.
     */
    @Test
    void writingWithoutAModelIsRejected() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final ResponseEntity<Map<String, Object>> response = this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("language", "en"))
                .retrieve()
                .toEntity(JSON_OBJECT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Verifies that a notebook whose sources have not been read is refused rather than summarised as
     * nothing.
     */
    @Test
    void writingWithoutAReadSourceIsRefused() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final ResponseEntity<Map<String, Object>> response =
                writeSummary(accessToken, notebookId, "en", "http://127.0.0.1:9");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * Verifies that the summary is written from the text of the sources, in the language the caller
     * named, and that it is kept for the next reader.
     *
     * @throws IOException          if the provider cannot be started
     * @throws InterruptedException if waiting for the indexing run is interrupted
     */
    @Test
    void theSummaryIsWrittenFromTheSourcesAndKept() throws IOException, InterruptedException {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        uploadAndAwait(accessToken, notebookId, "thermodynamics.txt", THERMODYNAMICS);

        final AtomicReference<String> seen = new AtomicReference<>("");
        final HttpServer provider = writingProvider(seen, WRITTEN_SUMMARY);
        try {
            final ResponseEntity<Map<String, Object>> response =
                    writeSummary(accessToken, notebookId, "de", addressOf(provider));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(requireBody(response).get("text")).isEqualTo(WRITTEN_SUMMARY);
            assertThat(requireBody(response).get("stale")).isEqualTo(false);
        } finally {
            provider.stop(0);
        }

        assertThat(seen.get())
                .describedAs("the model is shown the text of the source and the name of the language")
                .contains("entropy")
                .contains("thermodynamics.txt")
                .contains("Write the summary in German.");

        final Map<String, Object> stored = requireBody(readSummary(accessToken, notebookId));
        assertThat(stored.get("text")).isEqualTo(WRITTEN_SUMMARY);
        assertThat(stored.get("stale")).isEqualTo(false);
    }

    /**
     * Verifies that a summary written before a source arrived reports itself as no longer describing
     * the notebook.
     *
     * @throws IOException          if the provider cannot be started
     * @throws InterruptedException if waiting for an indexing run is interrupted
     */
    @Test
    void aSummaryBecomesStaleWhenASourceIsAdded() throws IOException, InterruptedException {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        uploadAndAwait(accessToken, notebookId, "thermodynamics.txt", THERMODYNAMICS);

        final HttpServer provider = writingProvider(new AtomicReference<>(""), WRITTEN_SUMMARY);
        try {
            assertThat(writeSummary(accessToken, notebookId, "en", addressOf(provider)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        } finally {
            provider.stop(0);
        }
        assertThat(requireBody(readSummary(accessToken, notebookId)).get("stale")).isEqualTo(false);

        uploadAndAwait(accessToken, notebookId, "baking.txt", BAKING);

        final Map<String, Object> stale = requireBody(readSummary(accessToken, notebookId));
        assertThat(stale.get("text")).isEqualTo(WRITTEN_SUMMARY);
        assertThat(stale.get("stale")).isEqualTo(true);
    }

    /**
     * Verifies that a provider answering with nothing leaves the summary the notebook had, rather
     * than replacing it with an empty one.
     *
     * @throws IOException          if a provider cannot be started
     * @throws InterruptedException if waiting for the indexing run is interrupted
     */
    @Test
    void aProviderThatWritesNothingLeavesThePreviousSummary() throws IOException, InterruptedException {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        uploadAndAwait(accessToken, notebookId, "thermodynamics.txt", THERMODYNAMICS);

        final HttpServer provider = writingProvider(new AtomicReference<>(""), WRITTEN_SUMMARY);
        try {
            assertThat(writeSummary(accessToken, notebookId, "en", addressOf(provider)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        } finally {
            provider.stop(0);
        }

        final HttpServer silent = writingProvider(new AtomicReference<>(""), "");
        try {
            assertThat(writeSummary(accessToken, notebookId, "en", addressOf(silent)).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_GATEWAY);
        } finally {
            silent.stop(0);
        }

        assertThat(requireBody(readSummary(accessToken, notebookId)).get("text"))
                .isEqualTo(WRITTEN_SUMMARY);
    }

    /**
     * Starts a provider that answers every request with one summary.
     *
     * @param seen    holder the request body of the last request is put into
     * @param summary text the provider answers with
     * @return the started provider
     * @throws IOException if the provider cannot be started
     */
    private static HttpServer writingProvider(final AtomicReference<String> seen, final String summary)
            throws IOException {
        final HttpServer provider =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        provider.setExecutor(Executors.newFixedThreadPool(2));
        provider.createContext("/api/chat", exchange -> {
            seen.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            final byte[] body = ("{\"model\":\"test\",\"message\":{\"role\":\"assistant\",\"content\":\""
                    + summary + "\"},\"done\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(body);
            }
            exchange.close();
        });
        provider.start();
        return provider;
    }

    /**
     * Returns the address a started provider is reached at.
     *
     * @param provider provider that is running
     * @return the base address of the provider
     */
    private static String addressOf(final HttpServer provider) {
        return "http://127.0.0.1:" + provider.getAddress().getPort();
    }

    /**
     * Reads the summary of a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to read the summary of
     * @return the response of the endpoint
     */
    private ResponseEntity<Map<String, Object>> readSummary(final String accessToken,
                                                            final String notebookId) {
        return this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Has the summary of a notebook written by a named provider.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to summarise
     * @param language    language tag the summary is requested in
     * @param baseUrl     address the provider is reached at
     * @return the response of the endpoint
     */
    private ResponseEntity<Map<String, Object>> writeSummary(final String accessToken,
                                                             final String notebookId,
                                                             final String language,
                                                             final String baseUrl) {
        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-AI-Provider", "OLLAMA")
                .header("X-AI-Model", "test-model")
                .header("X-AI-Base-Url", baseUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("language", language))
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Uploads a file into a notebook and waits until it has been read or has failed.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the file is added to
     * @param fileName    name the file is uploaded under
     * @param content     text of the file
     * @throws InterruptedException if the waiting is interrupted
     */
    private void uploadAndAwait(final String accessToken,
                                final String notebookId,
                                final String fileName,
                                final String content) throws InterruptedException {
        // The parts are assembled as a plain multi value map rather than with MultipartBodyBuilder,
        // whose reactive part support drags in a dependency this module does not carry.
        final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        final ResponseEntity<Map<String, Object>> stored = this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(stored.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        final String sourceId = (String) requireBody(stored).get("id");
        final Predicate<Object> running = status -> "UPLOADED".equals(status) || "INDEXING".equals(status);
        final Instant deadline = Instant.now().plus(INDEXING_TIMEOUT);
        Map<String, Object> source = readSource(accessToken, notebookId, sourceId);
        while (running.test(source.get("status")) && Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofMillis(100));
            source = readSource(accessToken, notebookId, sourceId);
        }
        assertThat(source.get("status")).describedAs("indexing did not finish in time").isEqualTo("READY");
    }

    /**
     * Reads one source out of the list of the sources of a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to read
     * @return the source as the endpoint returned it
     */
    private Map<String, Object> readSource(final String accessToken,
                                           final String notebookId,
                                           final String sourceId) {
        final List<Map<String, Object>> sources = requireBody(this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_ARRAY));
        return sources.stream()
                .filter(source -> sourceId.equals(source.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The notebook does not list the source " + sourceId));
    }

    /**
     * Creates a notebook for an account.
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
     * Registers a new account and returns the access token it was issued.
     *
     * @return the access token of the created account
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
     * Returns the body of a response, failing the test when there is none.
     *
     * @param response response to read
     * @param <T>      type of the body
     * @return the body of the response
     */
    private static <T> T requireBody(final ResponseEntity<T> response) {
        final T body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }
}
