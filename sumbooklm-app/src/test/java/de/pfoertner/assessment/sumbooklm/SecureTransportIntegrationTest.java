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
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises a deployment that declares itself to be served over HTTPS.
 *
 * <h2>What Is Being Stated</h2>
 * Every request this application answers below the API prefix carries a credential: an access token,
 * a password, or the API key of the user. A deployment that says it is reachable over HTTPS therefore
 * refuses the ones that arrived without it, and refuses them rather than redirecting, because a
 * redirect is written after the secret has already crossed the network.
 *
 * <h2>Why the Whole Context</h2>
 * The rule is a filter in the security chain, and whether it is installed at all depends on a
 * property. Both are decisions of the assembled application rather than of a class, so the property
 * is set on a context of its own and every request in it arrives over plain HTTP.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "sumbooklm.security.require-secure-transport=true")
class SecureTransportIntegrationTest {

    /**
     * Response shape of an endpoint returning one object.
     */
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    /**
     * Status an insecure request to the API is refused with.
     */
    private static final HttpStatus REFUSED = HttpStatus.UPGRADE_REQUIRED;

    /**
     * Client bound to the running server, configured to report failing statuses instead of throwing.
     */
    private final RestClient client;

    /**
     * Creates the test class and binds the client to the port the server was started on.
     *
     * @param port port the embedded server listens on
     */
    SecureTransportIntegrationTest(@Autowired @LocalServerPort final int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that a request carrying credentials is refused before it is read, whether it opens a
     * session, uses one, or would have carried the key of a model.
     */
    @Test
    void requestsToTheApiAreRefusedWithoutASecureConnection() {
        final ResponseEntity<Map<String, Object>> registration = this.client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "user-" + UUID.randomUUID(), "firstName", "Ada",
                        "lastName", "Lovelace", "password", "correct-horse-battery-staple"))
                .retrieve()
                .toEntity(JSON_OBJECT);

        assertThat(registration.getStatusCode()).isEqualTo(REFUSED);
        assertThat(statusOf("/api/v1/notebooks")).isEqualTo(REFUSED);
        assertThat(statusOf("/api/v1/notebooks/" + UUID.randomUUID() + "/chat/messages")).isEqualTo(REFUSED);
        assertThat(statusOf("/api/v1/security/cookie-iv/")).isEqualTo(REFUSED);
    }

    /**
     * Verifies that the refusal names a media type a client can read, so that a misconfigured
     * deployment reports what is wrong rather than answering with an empty body.
     */
    @Test
    void theRefusalIsAProblemDocument() {
        final ResponseEntity<Map<String, Object>> response = this.client.get()
                .uri("/api/v1/notebooks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer irrelevant")
                .retrieve()
                .toEntity(JSON_OBJECT);

        assertThat(response.getStatusCode()).isEqualTo(REFUSED);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .matches(type -> type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        final Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("title")).isEqualTo("Insecure transport");
    }

    /**
     * Verifies that the application shell is still served, so that a visitor of a misconfigured
     * deployment reaches something that can tell them rather than a blank refusal.
     */
    @Test
    void theApplicationShellIsStillServed() {
        final ResponseEntity<String> response = this.client.get()
                .uri("/")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Reads the status one path answers with.
     *
     * @param path path to request
     * @return the status the endpoint answered with
     */
    private HttpStatus statusOf(final String path) {
        return HttpStatus.valueOf(this.client.get()
                .uri(path)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value());
    }
}
