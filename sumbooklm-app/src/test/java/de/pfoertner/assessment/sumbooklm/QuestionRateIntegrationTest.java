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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the bound on how often one account may ask.
 *
 * <h2>A Deployment of Two Questions</h2>
 * The bound is configured down to two questions for this class, because the case is about what happens
 * at the limit and not about where the limit is. Asking sixty questions to reach the default would say
 * the same thing and would spend a minute of the build saying it.
 *
 * <h2>Its Own Database</h2>
 * The record of asked questions spans every account, as the bound it serves does. The class therefore
 * runs against a database of its own, so that the questions of the other suites are not counted into
 * these and the other way round.
 *
 * <h2>A Client That Does Not Obey</h2>
 * The client of this class is built on the HTTP client of the platform rather than on the one the
 * framework detects, because that one reads {@code Retry-After} and waits for it. Against the refusal
 * these cases provoke it would sleep for the rest of the hour and report whatever came after, which is
 * correct behaviour for a client and useless for a test about the refusal itself.
 *
 * <h2>No Provider Is Needed</h2>
 * The questions are asked against an address that refuses a connection at once. What is being asserted
 * happens before a provider is reached: a question that is admitted is stored and answered as best it
 * can, and a question that is refused never gets that far.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sumbooklm-rate;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "sumbooklm.chat.questions-per-hour=2"
})
class QuestionRateIntegrationTest {

    /**
     * Response shape of an endpoint returning one object.
     */
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    /**
     * Password used for every account the tests create.
     */
    private static final String PASSWORD = "correct-horse-battery-staple";

    /**
     * Address every question is asked against. The port is the discard port, which refuses a
     * connection immediately, so a failing answer costs no waiting.
     */
    private static final String UNREACHABLE_PROVIDER = "http://127.0.0.1:9";

    /**
     * Number of questions one account may ask, as configured for this class.
     */
    private static final int LIMIT = 2;

    /**
     * Client bound to the running server, configured to report failing statuses instead of throwing.
     */
    private final RestClient client;

    /**
     * Creates the test class and binds the client to the port the server was started on.
     *
     * @param port port the embedded server listens on
     */
    QuestionRateIntegrationTest(@Autowired @LocalServerPort final int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that an account may ask as often as the deployment allows, that the question after that
     * is refused, and that the refusal says when asking is possible again.
     */
    @Test
    void anAccountThatAsksTooOftenIsRefusedUntilTheWindowMovesOn() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sessionId = startConversation(accessToken, notebookId);

        for (int question = 1; question <= LIMIT; question += 1) {
            assertThat(ask(accessToken, notebookId, sessionId).getStatusCode())
                    .describedAs("question %s of %s has to be admitted", question, LIMIT)
                    .isEqualTo(HttpStatus.OK);
        }

        final ResponseEntity<String> refused = ask(accessToken, notebookId, sessionId);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(refused.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .describedAs("a refusal that lasts a known time has to say how long")
                .isNotNull()
                .satisfies(value -> assertThat(Long.parseLong(value)).isPositive());
    }

    /**
     * Verifies that a refused question is not stored, so that a bound the caller runs into leaves no
     * trace in the conversation it was asked in.
     */
    @Test
    void aRefusedQuestionIsNotPartOfTheConversation() {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        final String sessionId = startConversation(accessToken, notebookId);
        for (int question = 1; question <= LIMIT; question += 1) {
            ask(accessToken, notebookId, sessionId);
        }

        assertThat(ask(accessToken, notebookId, sessionId).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(messagesOf(accessToken, notebookId, sessionId))
                .describedAs("only the questions that were admitted are in the transcript")
                .hasSize(LIMIT);
    }

    /**
     * Verifies that the bound is counted per account, which is what keeps one client from refusing the
     * questions of everybody else.
     */
    @Test
    void anotherAccountIsNotAffected() {
        final String exhausted = registerAccount();
        final String exhaustedNotebook = createNotebook(exhausted);
        final String exhaustedSession = startConversation(exhausted, exhaustedNotebook);
        for (int question = 1; question <= LIMIT; question += 1) {
            ask(exhausted, exhaustedNotebook, exhaustedSession);
        }
        assertThat(ask(exhausted, exhaustedNotebook, exhaustedSession).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        final String other = registerAccount();
        final String otherNotebook = createNotebook(other);
        final String otherSession = startConversation(other, otherNotebook);

        assertThat(ask(other, otherNotebook, otherSession).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Asks one question against an unreachable provider.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the question is asked in
     * @param sessionId   identifier of the conversation the question continues
     * @return the response, whose body is the whole stream once it has ended
     */
    private ResponseEntity<String> ask(final String accessToken,
                                       final String notebookId,
                                       final String sessionId) {
        return this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/chats/" + sessionId + "/questions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-AI-Provider", "OLLAMA")
                .header("X-AI-Model", "unreachable")
                .header("X-AI-Base-Url", UNREACHABLE_PROVIDER)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What do the sources say?"))
                .retrieve()
                .toEntity(String.class);
    }

    /**
     * Reads the messages of one conversation.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation to read
     * @return the messages of the conversation, oldest first
     */
    private List<?> messagesOf(final String accessToken, final String notebookId, final String sessionId) {
        final ResponseEntity<Map<String, Object>> response = this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/chats/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<?>) requireBody(response).get("messages");
    }

    /**
     * Registers an account and returns its access token.
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
     * Creates a notebook for an account.
     *
     * @param accessToken access token to present
     * @return identifier of the created notebook
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
        return (String) requireBody(response).get("id");
    }

    /**
     * Starts a conversation in a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @return identifier of the started conversation
     */
    private String startConversation(final String accessToken, final String notebookId) {
        final ResponseEntity<Map<String, Object>> response = this.client.post()
                .uri("/api/v1/notebooks/" + notebookId + "/chats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) requireBody(response).get("id");
    }

    /**
     * Reads the body of a response and states that there is one.
     *
     * @param response response to read
     * @return the body of the response
     */
    private static Map<String, Object> requireBody(final ResponseEntity<Map<String, Object>> response) {
        final Map<String, Object> body = response.getBody();
        assertThat(body).describedAs("the response carries no body").isNotNull();
        return body;
    }
}
