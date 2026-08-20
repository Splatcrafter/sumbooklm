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

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

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
 * <h2>One Case Needs an Answer That Does Not Arrive</h2>
 * The bound on how many answers an account may have at once can only be observed while several are
 * in flight, and an answer against a refused connection is over before the next request is sent. That
 * case therefore runs a provider that accepts the request and holds it until the test lets go.
 *
 * <h2>Stopping Needs an Answer That Is Arriving</h2>
 * A stop can only be observed on a stream that has started producing, because the provider offers a
 * handle to cancel only once it has. The case that covers it therefore runs a provider that keeps
 * writing until it is stopped, which is the shape of a model that is generating a long answer.
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
     * Longest a stop may take. A stop that waited for the provider would take as long as the answer,
     * so the value only has to be far below that and far above a request to this machine.
     */
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Longest a test waits for the provider to notice that its answer is no longer being read.
     */
    private static final Duration CUT_OFF_TIMEOUT = Duration.ofSeconds(15);

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
     * Number of answers one account may have in flight, matching the value the workspace module is
     * configured with.
     */
    private static final int ANSWER_LIMIT = 3;

    /**
     * Longest a test waits for the answers it started to reach the provider.
     */
    private static final Duration START_TIMEOUT = Duration.ofSeconds(30);

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
     * Verifies that a notebook nobody has asked anything holds no conversations, and that reading it
     * creates none.
     */
    @Test
    void aFreshNotebookHoldsNoConversations() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        assertThat(listConversations(accessToken, notebookId)).isEmpty();
        assertThat(listConversations(accessToken, notebookId)).isEmpty();
    }

    /**
     * Verifies that a notebook holds as many conversations as its user starts, that each keeps its own
     * transcript, and that removing one leaves the others alone.
     */
    @Test
    void aNotebookHoldsSeveralConversations() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);

        final String first = startConversation(accessToken, notebookId);
        final String second = startConversation(accessToken, notebookId);
        assertThat(first).isNotEqualTo(second);
        assertThat(listConversations(accessToken, notebookId)).hasSize(2);

        ask(accessToken, notebookId, first, "What is entropy?", "OLLAMA", "llama3");
        ask(accessToken, notebookId, second, "What is a sourdough starter?", "OLLAMA", "llama3");

        assertThat(messagesOf(readConversation(accessToken, notebookId, first)))
                .singleElement()
                .extracting(message -> message.get("text"))
                .isEqualTo("What is entropy?");
        assertThat(readConversation(accessToken, notebookId, second).get("title"))
                .isEqualTo("What is a sourdough starter?");

        assertThat(deleteConversation(accessToken, notebookId, first)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listConversations(accessToken, notebookId)).singleElement()
                .extracting(conversation -> conversation.get("id"))
                .isEqualTo(second);
        assertThat(deleteConversation(accessToken, notebookId, first)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that a conversation of another notebook of the same account cannot be asked in, so
     * that the two identifiers of a question have to agree.
     */
    @Test
    void aConversationBelongsToOneNotebook() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String otherNotebookId = createNotebook(accessToken);
        final String sessionId = startConversation(accessToken, notebookId);

        assertThat(ask(accessToken, otherNotebookId, sessionId, "What is entropy?", "OLLAMA", "llama3")
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ask(accessToken, notebookId, UUID.randomUUID().toString(), "What is entropy?",
                "OLLAMA", "llama3").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that an incomplete model selection is refused before the question is stored, so that
     * a misconfigured client does not leave questions in a transcript that nobody could answer.
     */
    @Test
    void aQuestionWithoutAModelIsRejectedAndNotStored() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sessionId = startConversation(accessToken, notebookId);

        assertThat(ask(accessToken, notebookId, sessionId, "What is entropy?", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ask(accessToken, notebookId, sessionId, "What is entropy?", "OLLAMA", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ask(accessToken, notebookId, sessionId, "What is entropy?", "OPENAI", "gpt-4o-mini")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ask(accessToken, notebookId, sessionId, "What is entropy?", "TAROT", "major-arcana")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat((List<?>) readConversation(accessToken, notebookId, sessionId).get("messages")).isEmpty();
    }

    /**
     * Verifies that a question whose provider cannot be reached still becomes part of the transcript
     * and ends the stream with the reason, rather than disappearing.
     */
    @Test
    void aQuestionIsRecordedEvenWhenTheProviderFails() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        indexDocument(accessToken, notebookId, "thermodynamics.txt", THERMODYNAMICS);
        final String sessionId = startConversation(accessToken, notebookId);

        final ResponseEntity<String> response =
                ask(accessToken, notebookId, sessionId, "What is entropy?", "OLLAMA", "llama3");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventNames(response)).containsExactly("sources", "error");

        final Map<String, Object> conversation = readConversation(accessToken, notebookId, sessionId);
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

        final List<String> fromPhysics = sourceNamesOf(ask(accessToken, physics,
                startConversation(accessToken, physics),
                "What does the second law say about entropy?", "OLLAMA", "llama3"));
        final List<String> fromBaking = sourceNamesOf(ask(accessToken, baking,
                startConversation(accessToken, baking),
                "What does the second law say about entropy?", "OLLAMA", "llama3"));

        assertThat(fromPhysics).containsExactly("thermodynamics.txt");
        assertThat(fromBaking)
                .describedAs("a notebook answers from its own sources and from no others")
                .doesNotContain("thermodynamics.txt");
    }

    /**
     * Verifies that a question a notebook has no passages for is never put to a provider, and that the
     * stream still ends as finished with an empty answer, which is what the interface turns into a
     * sentence of its own.
     *
     * <p>The provider of this case would answer if it were asked, so the assertion that it was not
     * asked is the whole point. Telling a model that it has no sources and trusting it to say so
     * produced a complete answer about a document that does not exist.
     *
     * @throws Exception if the provider cannot be started
     */
    @Test
    void aQuestionWithoutPassagesIsNeverPutToAProvider() throws Exception {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sessionId = startConversation(accessToken, notebookId);

        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger arrived = new AtomicInteger();
        final HttpServer provider = writingProvider(arrived, release, new AtomicBoolean());
        final String address = "http://127.0.0.1:" + provider.getAddress().getPort();
        try {
            final ResponseEntity<String> response = ask(accessToken, notebookId, sessionId,
                    "Summarise everything.", "OLLAMA", "writing", address);

            assertThat(sourceNamesOf(response)).isEmpty();
            assertThat(eventNames(response)).containsExactly("sources", "done");
            assertThat(bodyOf(response))
                    .describedAs("nothing may be generated for a question with nothing to ground it")
                    .doesNotContain("tick");
            assertThat(arrived.get())
                    .describedAs("the provider must not be asked at all")
                    .isZero();
        } finally {
            release.countDown();
            provider.stop(0);
        }
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
        final String sessionId = startConversation(owner, notebookId);

        assertThat(conversationStatus(stranger, notebookId)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ask(stranger, notebookId, sessionId, "What is entropy?", "OLLAMA", "llama3")
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteConversation(stranger, notebookId, sessionId)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(conversationStatus(owner, UUID.randomUUID().toString())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that an account which already has as many answers in flight as it may have is refused,
     * that the refused question is not added to its transcript, and that the answers which were
     * accepted all reach it once they arrive.
     *
     * @throws Exception if the provider cannot be started or the waiting is interrupted
     */
    @Test
    void anAccountIsRefusedBeyondTheAnswersItMayHaveInFlight() throws Exception {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        indexDocument(accessToken, notebookId, "thermodynamics.txt", THERMODYNAMICS);
        final List<String> sessions = List.of(
                startConversation(accessToken, notebookId),
                startConversation(accessToken, notebookId),
                startConversation(accessToken, notebookId));
        final String extra = startConversation(accessToken, notebookId);

        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger arrived = new AtomicInteger();
        final HttpServer provider = stallingProvider(arrived, release);
        final String address = "http://127.0.0.1:" + provider.getAddress().getPort();
        final ExecutorService askers = Executors.newFixedThreadPool(ANSWER_LIMIT);
        try {
            for (int question = 0; question < ANSWER_LIMIT; question += 1) {
                final String text = "What is entropy, take " + question + "?";
                final String session = sessions.get(question);
                askers.submit(() ->
                        ask(accessToken, notebookId, session, text, "OLLAMA", "stalling", address));
            }
            awaitArrival(arrived, ANSWER_LIMIT);

            final ResponseEntity<String> refused = ask(accessToken, notebookId, extra,
                    "One question too many?", "OLLAMA", "stalling", address);

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(messagesOf(readConversation(accessToken, notebookId, extra)))
                    .describedAs("a refused question is not written")
                    .isEmpty();

            release.countDown();
            askers.shutdown();
            assertThat(askers.awaitTermination(1, TimeUnit.MINUTES)).isTrue();

            for (final String session : sessions) {
                assertThat(awaitTranscript(accessToken, notebookId, session, 2))
                        .describedAs("every accepted answer has to reach its transcript")
                        .hasSize(2);
            }
        } finally {
            release.countDown();
            askers.shutdown();
            provider.stop(0);
        }
    }

    /**
     * Waits until the transcript of a notebook has reached a length.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation to read
     * @param messages    number of messages to wait for
     * @return the transcript as it is once the length is reached or the wait is given up on
     * @throws InterruptedException if the waiting is interrupted
     */
    private List<Map<String, Object>> awaitTranscript(final String accessToken,
                                                      final String notebookId,
                                                      final String sessionId,
                                                      final int messages) throws InterruptedException {
        final Instant deadline = Instant.now().plus(START_TIMEOUT);
        List<Map<String, Object>> transcript = messagesOf(readConversation(accessToken, notebookId, sessionId));
        while (transcript.size() < messages && Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofMillis(50));
            transcript = messagesOf(readConversation(accessToken, notebookId, sessionId));
        }
        return transcript;
    }

    /**
     * Removes a conversation and returns the status of the request.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation to remove
     * @return the status the endpoint answered with
     */
    private HttpStatusCode deleteConversation(final String accessToken,
                                              final String notebookId,
                                              final String sessionId) {
        return this.client.delete()
                .uri("/api/v1/notebooks/" + notebookId + "/chats/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    /**
     * Starts a provider that holds every request until it is let go.
     *
     * @param arrived counter raised as each request reaches the provider
     * @param release latch the provider waits on before it answers
     * @return the started provider
     * @throws java.io.IOException if the provider cannot be started
     */
    private static HttpServer stallingProvider(final AtomicInteger arrived, final CountDownLatch release)
            throws java.io.IOException {
        final HttpServer provider =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        provider.setExecutor(Executors.newFixedThreadPool(ANSWER_LIMIT + 1));
        provider.createContext("/api/chat", exchange -> {
            arrived.incrementAndGet();
            try {
                release.await(1, TimeUnit.MINUTES);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final byte[] body = ("{\"message\":{\"role\":\"assistant\",\"content\":\"done\"},\"done\":false}\n"
                    + "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
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
     * Waits until every started answer has reached the provider and is therefore in flight.
     *
     * @param arrived  counter the provider raises
     * @param expected number of answers that have to have reached it
     * @throws InterruptedException if the waiting is interrupted
     */
    private static void awaitArrival(final AtomicInteger arrived, final int expected)
            throws InterruptedException {
        final Instant deadline = Instant.now().plus(START_TIMEOUT);
        while (arrived.get() < expected && Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofMillis(50));
        }
        assertThat(arrived.get()).describedAs("the answers did not reach the provider in time")
                .isEqualTo(expected);
    }

    /**
     * Verifies that an answer can be stopped, that stopping does not wait for the provider, that the
     * request to the provider is abandoned rather than read to its end, that what was generated before
     * is kept rather than discarded, and that the stream ends as finished rather than as failed.
     *
     * @throws Exception if the provider cannot be started or the waiting is interrupted
     */
    @Test
    void anAnswerCanBeStoppedAndWhatArrivedIsKept() throws Exception {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        indexDocument(accessToken, notebookId, "thermodynamics.txt", THERMODYNAMICS);
        final String sessionId = startConversation(accessToken, notebookId);

        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger arrived = new AtomicInteger();
        final AtomicBoolean cutOff = new AtomicBoolean();
        final HttpServer provider = writingProvider(arrived, release, cutOff);
        final String address = "http://127.0.0.1:" + provider.getAddress().getPort();
        final ExecutorService asker = Executors.newSingleThreadExecutor();
        try {
            final Future<ResponseEntity<String>> answering = asker.submit(() ->
                    ask(accessToken, notebookId, sessionId, "Explain entropy at length.",
                            "OLLAMA", "writing", address));
            awaitArrival(arrived, 1);
            Thread.sleep(Duration.ofMillis(500));

            final Instant asked = Instant.now();
            assertThat(stopAnswer(accessToken, notebookId, sessionId)).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(Duration.between(asked, Instant.now()))
                    .describedAs("stopping must not wait for the provider to finish the answer")
                    .isLessThan(STOP_TIMEOUT);

            assertThat(awaitCutOff(cutOff))
                    .describedAs("the request to the provider has to be abandoned, not merely ignored")
                    .isTrue();

            final ResponseEntity<String> stopped = answering.get(1, TimeUnit.MINUTES);
            assertThat(eventNames(stopped)).endsWith("done");
            assertThat(bodyOf(stopped)).contains("tick").doesNotContain("the ending nobody reads");

            final List<Map<String, Object>> transcript =
                    awaitTranscript(accessToken, notebookId, sessionId, 2);
            assertThat(transcript).hasSize(2);
            assertThat((String) transcript.get(1).get("text")).contains("tick");
        } finally {
            release.countDown();
            asker.shutdownNow();
            provider.stop(0);
        }
    }

    /**
     * Verifies that stopping a conversation nothing is being generated in is not an error, because an
     * answer that has just finished and one that never started are the same thing to ask about.
     */
    @Test
    void stoppingNothingIsNotAnError() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sessionId = startConversation(accessToken, notebookId);

        assertThat(stopAnswer(accessToken, notebookId, sessionId)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(stopAnswer(accessToken, notebookId, UUID.randomUUID().toString()))
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * Waits for a provider to notice that nobody is reading what it writes.
     *
     * @param cutOff flag the provider raises when writing fails
     * @return {@code true} if the provider noticed within the time a stop is given
     * @throws InterruptedException if the waiting is interrupted
     */
    private static boolean awaitCutOff(final AtomicBoolean cutOff) throws InterruptedException {
        final Instant deadline = Instant.now().plus(CUT_OFF_TIMEOUT);
        while (!cutOff.get() && Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofMillis(50));
        }
        return cutOff.get();
    }

    /**
     * Asks for the answer of a conversation to stop.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation whose answer is to stop
     * @return the status the endpoint answered with
     */
    private HttpStatusCode stopAnswer(final String accessToken,
                                      final String notebookId,
                                      final String sessionId) {
        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/chats/" + sessionId + "/stop")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    /**
     * Starts a provider that keeps writing parts of an answer until it is let go.
     *
     * @param arrived counter raised as each request reaches the provider
     * @param release latch the provider stops writing at
     * @param cutOff  flag raised when writing fails because the client abandoned the request
     * @return the started provider
     * @throws java.io.IOException if the provider cannot be started
     */
    private static HttpServer writingProvider(final AtomicInteger arrived,
                                              final CountDownLatch release,
                                              final AtomicBoolean cutOff)
            throws java.io.IOException {
        final HttpServer provider =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        provider.setExecutor(Executors.newFixedThreadPool(2));
        provider.createContext("/api/chat", exchange -> {
            arrived.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream stream = exchange.getResponseBody()) {
                while (!release.await(50, TimeUnit.MILLISECONDS)) {
                    stream.write(part("tick "));
                    stream.flush();
                }
                stream.write(part("the ending nobody reads"));
                stream.write(("{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}\n")
                        .getBytes(StandardCharsets.UTF_8));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final java.io.IOException e) {
                // The client abandoned the request, which is what the case asserts. Writing the rest
                // of an answer nobody is waiting for is not part of it.
                cutOff.set(true);
            }
            exchange.close();
        });
        provider.start();
        return provider;
    }

    /**
     * Builds one part of a streamed answer in the form the provider speaks.
     *
     * @param text text the part carries
     * @return the encoded line
     */
    private static byte[] part(final String text) {
        return ("{\"message\":{\"role\":\"assistant\",\"content\":\"" + text + "\"},\"done\":false}\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verifies that the endpoints are unreachable without an access token.
     */
    @Test
    void endpointsRequireAnAccessToken() {
        final ResponseEntity<Map<String, Object>> response = this.client.get()
                .uri("/api/v1/notebooks/" + UUID.randomUUID() + "/chats")
                .retrieve()
                .toEntity(JSON_OBJECT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Asks a question and returns the stream it was answered with.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the question is asked in
     * @param sessionId   identifier of the conversation the question continues
     * @param question    question to ask
     * @param provider    value of the provider header, or {@code null} to omit it
     * @param model       value of the model header, or {@code null} to omit it
     * @return the response, whose body is the whole stream once it has ended
     */
    private ResponseEntity<String> ask(final String accessToken,
                                       final String notebookId,
                                       final String sessionId,
                                       final String question,
                                       final String provider,
                                       final String model) {
        return ask(accessToken, notebookId, sessionId, question, provider, model, UNREACHABLE_PROVIDER);
    }

    /**
     * Asks a question against a named provider and returns the stream it was answered with.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the question is asked in
     * @param sessionId   identifier of the conversation the question continues
     * @param question    question to ask
     * @param provider    value of the provider header, or {@code null} to omit it
     * @param model       value of the model header, or {@code null} to omit it
     * @param address     address the provider is reached at
     * @return the response, whose body is the whole stream once it has ended
     */
    private ResponseEntity<String> ask(final String accessToken,
                                       final String notebookId,
                                       final String sessionId,
                                       final String question,
                                       final String provider,
                                       final String model,
                                       final String address) {
        final RestClient.RequestBodySpec request = this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/chats/" + sessionId + "/questions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-AI-Base-Url", address)
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
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation to read
     * @return the conversation as the endpoint describes it
     */
    private Map<String, Object> readConversation(final String accessToken,
                                                 final String notebookId,
                                                 final String sessionId) {
        final ResponseEntity<Map<String, Object>> response = this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/chats/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final Map<String, Object> body = response.getBody();
        assertThat(body).describedAs("the conversation carries no body").isNotNull();
        return body;
    }

    /**
     * Reads the status of a request for the conversations of a notebook, without parsing its body.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to read the conversation of
     * @return the status the endpoint answered with
     */
    private HttpStatusCode conversationStatus(final String accessToken, final String notebookId) {
        return this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/chats")
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
     * Starts a conversation and returns its identifier.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @return the identifier of the started conversation
     */
    private String startConversation(final String accessToken, final String notebookId) {
        final ResponseEntity<Map<String, Object>> response = this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/chats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        final Map<String, Object> body = response.getBody();
        assertThat(body).describedAs("the conversation carries no body").isNotNull();
        return (String) body.get("id");
    }

    /**
     * Lists the conversations of a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to list the conversations of
     * @return the conversations as the endpoint describes them
     */
    private List<Map<String, Object>> listConversations(final String accessToken, final String notebookId) {
        final ResponseEntity<List<Map<String, Object>>> response = this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/chats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_ARRAY);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final List<Map<String, Object>> body = response.getBody();
        assertThat(body).describedAs("the conversations carry no body").isNotNull();
        return body;
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
