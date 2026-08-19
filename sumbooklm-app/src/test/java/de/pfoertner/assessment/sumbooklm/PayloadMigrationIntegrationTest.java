package de.pfoertner.assessment.sumbooklm;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookEntity;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookRepository;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;

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
 * Exercises the migration of a stored payload that was written by an earlier version.
 *
 * <h2>Why This Is an Integration Test</h2>
 * A data fix is one expression on a tree and would be trivial to call directly. What is worth testing
 * is the pipeline around it: that the stored version stamp routes a payload through the fix, that the
 * codec of the current version can read what comes out, and that the endpoint answers with a notebook
 * rather than with a failure. None of that exists outside the assembled application.
 *
 * <h2>How the Old Payload Gets There</h2>
 * The row is written through the endpoint and its payload is then replaced with the bytes an earlier
 * version would have written, stamped with the version it wrote them at. That is what a database of a
 * deployment that has been upgraded looks like, and it is the only way to produce one from a build
 * that no longer contains the earlier code.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PayloadMigrationIntegrationTest {

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
     * Serializer of the payload an earlier version would have written.
     */
    private final CBORMapper cborMapper = CBORMapper.builder().build();

    /**
     * Client bound to the running server, configured to report failing statuses instead of throwing.
     */
    private final RestClient client;

    /**
     * Storage of the notebooks, used to put a payload of an earlier version into the database.
     */
    private final NotebookRepository notebookRepository;

    /**
     * Creates the test class.
     *
     * @param port               port the embedded server listens on
     * @param notebookRepository storage of the notebooks
     */
    PayloadMigrationIntegrationTest(@Autowired @LocalServerPort final int port,
                                    @Autowired final NotebookRepository notebookRepository) {
        this.notebookRepository = notebookRepository;
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that a notebook stored at payload schema version {@code 1.0.0} is still readable, and
     * that it arrives with the empty summary the fix gives it.
     *
     * @throws java.io.IOException if the payload of the earlier version cannot be serialized
     */
    @Test
    void aNotebookWrittenBeforeTheSummaryIsStillRead() throws java.io.IOException {
        final String accessToken = registerAccount();
        final String notebookId = createNotebook(accessToken);
        storeAsVersionOne(UUID.fromString(notebookId), "Thermodynamics", true, "*");

        final Map<String, Object> notebook = requireBody(this.client.get()
                .uri("/api/v1/notebooks/" + notebookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT));

        assertThat(notebook.get("title")).isEqualTo("Thermodynamics");
        assertThat(notebook.get("pinned")).isEqualTo(true);
        assertThat(notebook.get("topicIcon")).isEqualTo("*");

        final Map<String, Object> summary = requireBody(this.client.get()
                .uri("/api/v1/notebooks/" + notebookId + "/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JSON_OBJECT));

        assertThat(summary.get("text"))
                .describedAs("a notebook that predates the summary has none rather than a wrong one")
                .isEqualTo("");
        assertThat(summary.get("stale")).isEqualTo(false);
    }

    /**
     * Replaces the payload of a notebook with the bytes and the version stamp of the initial schema.
     *
     * @param notebookId identifier of the notebook to overwrite
     * @param title      title the earlier version would have stored
     * @param pinned     pin state the earlier version would have stored
     * @param topicIcon  topic icon the earlier version would have stored
     * @throws java.io.IOException if the payload cannot be serialized
     */
    private void storeAsVersionOne(final UUID notebookId,
                                   final String title,
                                   final boolean pinned,
                                   final String topicIcon) throws java.io.IOException {
        final ObjectNode payload = this.cborMapper.createObjectNode();
        payload.put("title", title);
        payload.put("pinned", pinned);
        payload.put("topicIcon", topicIcon);

        final NotebookEntity entity = this.notebookRepository.findById(notebookId).orElseThrow();
        entity.setPayload(this.cborMapper.writeValueAsBytes(payload));
        entity.setPayloadVersion(PayloadSchemaVersion.V1_0_0);
        this.notebookRepository.save(entity);
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
                .body(Map.of("title", "Replaced"))
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
