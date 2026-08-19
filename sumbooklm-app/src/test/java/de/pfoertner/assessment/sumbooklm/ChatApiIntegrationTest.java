package de.pfoertner.assessment.sumbooklm;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * Exercises the chat endpoints against a running server.
 *
 * <h2>Answering Without a Model</h2>
 * No language model is reachable from a build, so every question is asked against a local address
 * that refuses the connection. What that leaves observable is everything this application is
 * responsible for: the order of the checks, the question that is recorded before the model is asked,
 * the passages the question was allowed to reach, and the failure that ends the stream. Only the
 * generated words themselves are out of reach, and they are the part the application does not write.
 *
 * <h2>Isolation Is the Point</h2>
 * Two notebooks of one account hold different documents, and the sources reported for a question are
 * asserted to be those of the notebook it was asked in. That is the assertion the retrieval filter
 * exists for, and it fails as soon as the filter stops being applied.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ChatApiIntegrationTest {

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
     * Address every question is asked against. The port is the discard port, which refuses a
     * connection immediately, so a failing answer costs no waiting.
     */
    private static final String UNREACHABLE_PROVIDER = "http://127.0.0.1:9";

    /**
     * Text uploaded into the notebook the questions are asked in.
     */
    private static final String THERMODYNAMICS = """
            The second law of thermodynamics introduces entropy. In an isolated system entropy never
            decreases, which is what gives a direction to processes that the first law alone would
            allow to run either way.

            A heat engine converts thermal energy into work. Its efficiency is bounded by the
            temperatures of the reservoirs it operates between, and no engine can exceed that bound.
            """;

    /**
     * Text uploaded into the notebook that must not be reached from the other one.
     */
    private static final String BAKING = """
            A sourdough starter is a culture of wild yeast and lactic acid bacteria. It is kept alive
            by discarding part of it and feeding the rest with flour and water at room temperature.

            The dough is folded rather than kneaded. Each fold builds tension in the gluten network,
            and the rest between two folds is what lets the dough relax enough for the next one.
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
    ChatApiIntegrationTest(@Autowired @LocalServerPort final int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that a notebook nobody has asked anything answers with an empty conversation rather
     * than with a missing one.
     */
    @Test
    void conversationOfAFreshNotebookIsEmpty() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final Map<String, Object> conversation = readConversation(accessToken, notebookId);

        assertThat(conversation.get("title")).isEqualTo("");
        assertThat((List<?>) conversation.get("messages")).isEmpty();
    }

    /**
     * Verifies that an incomplete model selection is refused before the question is stored, so that
     * a misconfigured client does not leave questions in a transcript that nobody could answer.
     */
    @Test
    void aQuestionWithoutAModelIsRejectedAndNotStored() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        assertThat(ask(accessToken, notebookId, "What is entropy?", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ask(accessToken, notebookId, "What is entropy?", "OLLAMA", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ask(accessToken, notebookId, "What is entropy?", "OPENAI", "gpt-4o-mini").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ask(accessToken, notebookId, "What is entropy?", "TAROT", "major-arcana").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat((List<?>) readConversation(accessToken, notebookId).get("messages")).isEmpty();
    }

    /**
     * Verifies that a question whose provider cannot be reached still becomes part of the transcript
     * and ends the stream with the reason, rather than disappearing.
     */
    @Test
    void aQuestionIsRecordedEvenWhenTheProviderFails() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final ResponseEntity<String> response =
                ask(accessToken, notebookId, "What is entropy?", "OLLAMA", "llama3");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventNames(response)).containsExactly("sources", "error");

        final Map<String, Object> conversation = readConversation(accessToken, notebookId);
        assertThat(conversation.get("title")).isEqualTo("What is entropy?");
        final List<Map<String, Object>> messages = messagesOf(conversation);
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().get("role")).isEqualTo("USER");
        assertThat(messages.getFirst().get("text")).isEqualTo("What is entropy?");
    }

    /**
     * Verifies that the passages a question may reach are those of its own notebook, which is what
     * the metadata filter of the retriever exists for.
     */
    @Test
    void retrievedSourcesAreLimitedToTheNotebookOfTheQuestion() {
        final String accessToken = registerAccount();
        final String physics = createNotebook(accessToken);
        final String baking = createNotebook(accessToken);
        indexDocument(accessToken, physics, "thermodynamics.txt", THERMODYNAMICS);
        indexDocument(accessToken, baking, "sourdough.txt", BAKING);

        final List<String> fromPhysics = sourceNamesOf(
                ask(accessToken, physics, "What does the second law say about entropy?", "OLLAMA", "llama3"));
        final List<String> fromBaking = sourceNamesOf(
                ask(accessToken, baking, "What does the second law say about entropy?", "OLLAMA", "llama3"));

        assertThat(fromPhysics).containsExactly("thermodynamics.txt");
        assertThat(fromBaking).isEmpty();
    }

    /**
     * Verifies that a notebook that has nothing indexed still answers through the same stream, so
     * that a client renders one case rather than two.
     */
    @Test
    void aNotebookWithoutSourcesReportsNoSourcesAndStillStreams() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final ResponseEntity<String> response =
                ask(accessToken, notebookId, "What is entropy?", "OLLAMA", "llama3");

        assertThat(sourceNamesOf(response)).isEmpty();
        assertThat(eventNames(response)).containsExactly("sources", "error");
    }

    /**
     * Verifies that the conversation of a notebook of another account can neither be read nor added
     * to, and that the answer does not distinguish it from a notebook that does not exist.
     */
    @Test
    void conversationsOfAnotherAccountAreNotReachable() {
        final String owner = registerAccount();
        final String stranger = registerAccount();
        final String notebookId = createNotebook(owner);

        assertThat(conversationStatus(stranger, notebookId)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ask(stranger, notebookId, "What is entropy?", "OLLAMA", "llama3").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(conversationStatus(owner, UUID.randomUUID().toString())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that the endpoints are unreachable without an access token.
     */
    @Test
    void endpointsRequireAnAccessToken() {
        final ResponseEntity<Map<String, Object>> response = this.client.get()
                .uri("/api/v1/notebooks/" + UUID.randomUUID() + "/chat/messages")
                .retrieve()
                .toEntity(JSON_OBJECT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Asks a question and returns the stream it was answered with.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the question is asked in
     * @param question    question to ask
     * @param provider    value of the provider header, or {@code null} to omit it
     * @param model       value of the model header, or {@code null} to omit it
     * @return the response, whose body is the whole stream once it has ended
     */
    private ResponseEntity<String> ask(final String accessToken,
                                       final String notebookId,
                                       final String question,
                                       final String provider,
                                       final String model) {
        final RestClient.RequestBodySpec request = this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-AI-Base-Url", UNREACHABLE_PROVIDER)
                .contentType(MediaType.APPLICATION_JSON);
        if (provider != null) {
            request.header("X-AI-Provider", provider);
        }
        if (model != null) {
            request.header("X-AI-Model", model);
        }
        return request.body(Map.of("question", question)).retrieve().toEntity(String.class);
    }

    /**
     * Reads the names of the events a stream carried, in the order they arrived.
     *
     * @param response response whose body is a finished stream
     * @return the names of the events, with repetitions collapsed to their first occurrence
     */
    private static List<String> eventNames(final ResponseEntity<String> response) {
        final List<String> names = new ArrayList<>();
        for (final String line : bodyOf(response).split("\n")) {
            if (line.startsWith("event:")) {
                final String name = line.substring("event:".length()).strip();
                if (names.isEmpty() || !names.getLast().equals(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Reads the names of the sources a stream reported before its answer.
     *
     * @param response response whose body is a finished stream
     * @return the names of the reported sources, in the order they were numbered
     */
    private static List<String> sourceNamesOf(final ResponseEntity<String> response) {
        final String body = bodyOf(response);
        final int start = body.indexOf("event:sources");
        assertThat(start).describedAs("the stream reported no sources").isNotNegative();
        final int from = body.indexOf("data:", start);
        final int to = body.indexOf('\n', from);
        final String data = body.substring(from + "data:".length(), to);

        final List<String> names = new ArrayList<>();
        int index = data.indexOf("\"displayName\":\"");
        while (index >= 0) {
            final int valueStart = index + "\"displayName\":\"".length();
            names.add(data.substring(valueStart, data.indexOf('"', valueStart)));
            index = data.indexOf("\"displayName\":\"", valueStart);
        }
        return names;
    }

    /**
     * Returns the body of a response that has to have one.
     *
     * @param response response to read
     * @return the body of the response
     */
    private static String bodyOf(final ResponseEntity<String> response) {
        final String body = response.getBody();
        assertThat(body).describedAs("the response carries no body").isNotNull();
        return body;
    }

    /**
     * Reads the conversation of a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to read the conversation of
     * @return the conversation as the endpoint describes it
     */
    private Map<String, Object> readConversation(final String accessToken, final String notebookId) {
        final ResponseEntity<Map<String, Object>> response = this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final Map<String, Object> body = response.getBody();
        assertThat(body).describedAs("the conversation carries no body").isNotNull();
        return body;
    }

    /**
     * Reads the status of a request for a conversation, without parsing its body.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to read the conversation of
     * @return the status the endpoint answered with
     */
    private HttpStatusCode conversationStatus(final String accessToken, final String notebookId) {
        return this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    /**
     * Reads the messages out of a conversation.
     *
     * @param conversation conversation as the endpoint describes it
     * @return the messages of the conversation, oldest first
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messagesOf(final Map<String, Object> conversation) {
        return (List<Map<String, Object>>) conversation.get("messages");
    }

    /**
     * Uploads a document and waits until it can be retrieved from.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the document is added to
     * @param fileName    name the file is uploaded under
     * @param content     text the file carries
     */
    private void indexDocument(final String accessToken,
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

        final ResponseEntity<Map<String, Object>> response = this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/sources/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        final Map<String, Object> stored = response.getBody();
        assertThat(stored).describedAs("the upload carries no body").isNotNull();
        awaitIndexed(accessToken, notebookId, (String) stored.get("id"));
    }

    /**
     * Waits until a source has become part of the retrieval index.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to wait for
     */
    private void awaitIndexed(final String accessToken, final String notebookId, final String sourceId) {
        final Instant deadline = Instant.now().plus(INDEXING_TIMEOUT);
        Object status = statusOf(accessToken, notebookId, sourceId);
        while (!"READY".equals(status) && !"ERROR".equals(status) && Instant.now().isBefore(deadline)) {
            sleepBriefly();
            status = statusOf(accessToken, notebookId, sourceId);
        }
        assertThat(status).describedAs("indexing did not finish in time").isEqualTo("READY");
    }

    /**
     * Reads the stage one source has reached.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to read
     * @return the stage of the source
     */
    private Object statusOf(final String accessToken, final String notebookId, final String sourceId) {
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
                .orElseThrow(() -> new AssertionError("The notebook holds no source " + sourceId))
                .get("status");
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
        final Map<String, Object> body = response.getBody();
        assertThat(body).describedAs("the registration carries no body").isNotNull();
        return (String) ((Map<?, ?>) body.get("tokens")).get("accessToken");
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
                .body(Map.of("title", "Notebook"))
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        final Map<String, Object> body = response.getBody();
        assertThat(body).describedAs("the notebook carries no body").isNotNull();
        return (String) body.get("id");
    }
}
