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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the notebook endpoints against a running server.
 *
 * <h2>Coverage</h2>
 * The test drives the endpoints through HTTP, which covers what only exists in the assembled
 * application: the filter chain requiring an access token, the account being taken from that token
 * rather than from the request, the notebook payload travelling through the CBOR codec and back, and
 * the removal of a notebook being refused once the session behind the token is closed.
 *
 * <h2>Isolation</h2>
 * Every test registers its own account under a generated username, so the tests share the schema and
 * the server but not any data. Ownership is verified with a second account rather than by inspecting
 * the database, because it is the endpoint and not the row that has to enforce it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class NotebookApiIntegrationTest {

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
     * Client bound to the running server, configured to report failing statuses instead of throwing.
     */
    private final RestClient client;

    /**
     * Creates the test class and binds the client to the port the server was started on.
     *
     * @param port port the embedded server listens on
     */
    NotebookApiIntegrationTest(@Autowired @LocalServerPort final int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that a created notebook is returned with its identifier, an empty topic icon, no
     * sources and the location it can be addressed under.
     */
    @Test
    void creationReturnsNotebookAndItsLocation() {
        final String accessToken = registerAccount();

        final ResponseEntity<Map<String, Object>> response = create(accessToken, "Thermodynamics");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        final Map<String, Object> body = requireBody(response);
        assertThat(body.get("title")).isEqualTo("Thermodynamics");
        assertThat(body.get("pinned")).isEqualTo(false);
        assertThat(body.get("topicIcon")).isEqualTo("");
        assertThat(body.get("sourceCount")).isEqualTo(0);
        assertThat(body.get("createdAt")).isEqualTo(body.get("lastActivityAt"));
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/notebooks/" + body.get("id"));
    }

    /**
     * Verifies that a title consisting of whitespace is rejected before it reaches the workspace
     * module.
     */
    @Test
    void creationRejectsBlankTitle() {
        final String accessToken = registerAccount();

        assertThat(create(accessToken, "   ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Verifies that the endpoints are unreachable without an access token.
     */
    @Test
    void endpointsRequireAnAccessToken() {
        final ResponseEntity<List<Map<String, Object>>> response = this.client.get()
                .uri("/api/v1/notebooks")
                .retrieve()
                .toEntity(JSON_ARRAY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that the overview is ordered by the activity timestamp and carries the pin state of
     * every notebook.
     */
    @Test
    void listReturnsNotebooksMostRecentlyActiveFirst() {
        final String accessToken = registerAccount();
        create(accessToken, "First");
        final String second = idOf(create(accessToken, "Second"));
        update(accessToken, second, Map.of("pinned", true));

        final List<Map<String, Object>> notebooks = requireBody(list(accessToken));

        assertThat(notebooks).hasSize(2);
        assertThat(notebooks.getFirst().get("title")).isEqualTo("Second");
        assertThat(notebooks.getFirst().get("pinned")).isEqualTo(true);
        assertThat(notebooks.getLast().get("title")).isEqualTo("First");
        assertThat(notebooks.getLast().get("pinned")).isEqualTo(false);
    }

    /**
     * Verifies that the overview of one account never contains a notebook of another account.
     */
    @Test
    void listReturnsOnlyNotebooksOfTheAuthenticatedAccount() {
        final String owner = registerAccount();
        final String stranger = registerAccount();
        create(owner, "Owned");

        assertThat(requireBody(list(stranger))).isEmpty();
        assertThat(requireBody(list(owner))).hasSize(1);
    }

    /**
     * Verifies that a change carrying only the title leaves the pin state alone and the other way
     * round, and that renaming refreshes the activity timestamp while pinning does not.
     */
    @Test
    void updateChangesOnlyTheSubmittedFields() {
        final String accessToken = registerAccount();
        final Map<String, Object> created = requireBody(create(accessToken, "Draft"));
        final String notebookId = (String) created.get("id");

        final Map<String, Object> pinned = requireBody(update(accessToken, notebookId, Map.of("pinned", true)));
        assertThat(pinned.get("title")).isEqualTo("Draft");
        assertThat(pinned.get("pinned")).isEqualTo(true);
        assertThat(pinned.get("lastActivityAt")).isEqualTo(created.get("lastActivityAt"));

        final Map<String, Object> renamed =
                requireBody(update(accessToken, notebookId, Map.of("title", "  Thermodynamics  ")));
        assertThat(renamed.get("title")).isEqualTo("Thermodynamics");
        assertThat(renamed.get("pinned")).isEqualTo(true);
        assertThat(renamed.get("lastActivityAt")).isNotEqualTo(created.get("lastActivityAt"));
    }

    /**
     * Verifies that a notebook of another account can neither be changed nor removed, and that the
     * answer does not distinguish it from a notebook that does not exist.
     */
    @Test
    void notebooksOfAnotherAccountAreNotReachable() {
        final String owner = registerAccount();
        final String stranger = registerAccount();
        final String notebookId = idOf(create(owner, "Owned"));

        assertThat(update(stranger, notebookId, Map.of("title", "Taken")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(stranger, notebookId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(update(owner, UUID.randomUUID().toString(), Map.of("title", "Absent")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(requireBody(list(owner)).getFirst().get("title")).isEqualTo("Owned");
    }

    /**
     * Verifies that a removed notebook disappears from the overview and cannot be removed twice.
     */
    @Test
    void deleteRemovesTheNotebook() {
        final String accessToken = registerAccount();
        final String notebookId = idOf(create(accessToken, "Temporary"));

        assertThat(delete(accessToken, notebookId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(requireBody(list(accessToken))).isEmpty();
        assertThat(delete(accessToken, notebookId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that removal is refused once the session behind the access token has been closed,
     * while the token itself is still within its lifetime.
     */
    @Test
    void deleteIsRefusedAfterTheSessionWasClosed() {
        final String accessToken = registerAccount();
        final String notebookId = idOf(create(accessToken, "Temporary"));
        this.client.post()
                .uri("/api/v1/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(delete(accessToken, notebookId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
     * Creates a notebook.
     *
     * @param accessToken access token to present
     * @param title       name the notebook is created under
     * @return the response of the creation endpoint
     */
    private ResponseEntity<Map<String, Object>> create(final String accessToken, final String title) {
        return this.client.post()
                .uri("/api/v1/notebooks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title))
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Lists the notebooks of an account.
     *
     * @param accessToken access token to present
     * @return the response of the listing endpoint
     */
    private ResponseEntity<List<Map<String, Object>>> list(final String accessToken) {
        return this.client.get()
                .uri("/api/v1/notebooks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_ARRAY);
    }

    /**
     * Changes a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to change
     * @param body        fields to change
     * @return the response of the change endpoint
     */
    private ResponseEntity<Map<String, Object>> update(final String accessToken,
                                                       final String notebookId,
                                                       final Map<String, Object> body) {
        return this.client.patch()
                .uri("/api/v1/notebooks/" + notebookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Removes a notebook.
     *
     * @param accessToken access token to present
     * @param notebookId  identifier of the notebook to remove
     * @return the response of the removal endpoint
     */
    private ResponseEntity<Void> delete(final String accessToken, final String notebookId) {
        return this.client.delete()
                .uri("/api/v1/notebooks/" + notebookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Reads the identifier out of the response of the creation endpoint.
     *
     * @param response response of a creation
     * @return the identifier of the created notebook
     */
    private static String idOf(final ResponseEntity<Map<String, Object>> response) {
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
