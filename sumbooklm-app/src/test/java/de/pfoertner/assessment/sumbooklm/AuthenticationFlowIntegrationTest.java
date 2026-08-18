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
 * Exercises registration, login, token rotation and logout against a running server.
 *
 * <h2>Coverage</h2>
 * The test drives the module through HTTP rather than through its services, which covers the parts
 * that only exist in the assembled application: the filter chain deciding what is reachable without
 * a token, the resource server verifying access tokens, the payload of an account travelling through
 * the CBOR codec and back, and the cookie the key handle is carried in.
 *
 * <h2>Isolation</h2>
 * Every test registers its own account under a generated username, so the tests share the schema and
 * the server but not any data.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AuthenticationFlowIntegrationTest {

    /**
     * Response shape of every endpoint under test.
     */
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
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
    AuthenticationFlowIntegrationTest(@Autowired @LocalServerPort final int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /**
     * Verifies that a registration returns a usable token pair, echoes the stored profile and hands
     * the client the cookie carrying its key handle.
     */
    @Test
    void registrationReturnsTokenPairAndKeyHandleCookie() {
        final String username = uniqueUsername();
        final ResponseEntity<Map<String, Object>> response = register(username);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final Map<String, Object> body = requireBody(response);
        final Map<?, ?> tokens = (Map<?, ?>) body.get("tokens");
        assertThat(tokens.get("tokenType")).isEqualTo("Bearer");
        assertThat((String) tokens.get("accessToken")).isNotBlank();
        assertThat((String) tokens.get("refreshToken")).isNotBlank();
        assertThat(tokens.get("accessTokenExpiresAt")).isNotNull();
        assertThat(tokens.get("refreshTokenExpiresAt")).isNotNull();

        final Map<?, ?> user = (Map<?, ?>) body.get("user");
        assertThat(user.get("username")).isEqualTo(username);
        assertThat(user.get("firstName")).isEqualTo("Ada");
        assertThat(user.get("lastName")).isEqualTo("Lovelace");
        assertThat(user.get("registeredAt")).isEqualTo(user.get("lastLoginAt"));

        assertThat(keyHandleCookie(response)).isNotBlank();
    }

    /**
     * Verifies that a second registration under a taken username is reported as a conflict.
     */
    @Test
    void registrationRejectsDuplicateUsername() {
        final String username = uniqueUsername();
        assertThat(register(username).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(register(username).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * Verifies that a registration with a password below the accepted length is rejected before it
     * reaches the security module.
     */
    @Test
    void registrationRejectsShortPassword() {
        final ResponseEntity<Map<String, Object>> response = this.client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", uniqueUsername(),
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "password", "too-short"))
                .retrieve()
                .toEntity(JSON_OBJECT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Verifies that a login with the correct password succeeds and returns the profile that was
     * written into the payload column during registration.
     */
    @Test
    void loginReturnsTokenPairAndStoredProfile() {
        final String username = uniqueUsername();
        register(username);

        final ResponseEntity<Map<String, Object>> response = login(username, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final Map<?, ?> user = (Map<?, ?>) requireBody(response).get("user");
        assertThat(user.get("firstName")).isEqualTo("Ada");
        assertThat(user.get("lastName")).isEqualTo("Lovelace");
    }

    /**
     * Verifies that a wrong password is rejected and that an unknown username is rejected in exactly
     * the same way.
     */
    @Test
    void loginRejectsWrongCredentials() {
        final String username = uniqueUsername();
        register(username);

        assertThat(login(username, "wrong-password-value").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(uniqueUsername(), PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that an endpoint below the API prefix is unreachable without an access token.
     */
    @Test
    void protectedEndpointRejectsRequestWithoutAccessToken() {
        final ResponseEntity<Void> response = this.client.post()
                .uri("/api/v1/logout")
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that a refresh token cannot be presented where an access token is expected.
     */
    @Test
    void protectedEndpointRejectsRefreshTokenAsBearerCredential() {
        final ResponseEntity<Map<String, Object>> registration = register(uniqueUsername());

        final ResponseEntity<Void> response = this.client.post()
                .uri("/api/v1/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(registration, "refreshToken"))
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that the cookie parameters are refused when the request carries no key handle.
     */
    @Test
    void cookieParametersRequireKeyHandle() {
        final ResponseEntity<Void> response = this.client.get()
                .uri("/api/v1/security/cookie-iv/")
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that a key handle yields a stable key and a fresh initialization vector on every call.
     */
    @Test
    void cookieParametersAreDerivedFromTheKeyHandle() {
        final String keyHandle = keyHandleCookie(register(uniqueUsername()));

        final Map<String, Object> first = requireBody(cookieParameters(keyHandle));
        final Map<String, Object> second = requireBody(cookieParameters(keyHandle));

        assertThat(first.get("cookieName")).isEqualTo("sumbooklm_auth");
        assertThat(first.get("algorithm")).isEqualTo("AES-GCM");
        assertThat(first.get("keyLength")).isEqualTo(256);
        assertThat(first.get("initializationVectorLength")).isEqualTo(12);
        assertThat(first.get("authenticationTagLength")).isEqualTo(128);
        assertThat(first.get("key")).isEqualTo(second.get("key"));
        assertThat(first.get("initializationVector")).isNotEqualTo(second.get("initializationVector"));
    }

    /**
     * Verifies that a refresh yields a new pair and that the presented token cannot be used again.
     */
    @Test
    void refreshRotatesThePairAndConsumesThePresentedToken() {
        final ResponseEntity<Map<String, Object>> registration = register(uniqueUsername());
        final String refreshToken = token(registration, "refreshToken");

        final ResponseEntity<Map<String, Object>> rotated = refresh(refreshToken);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        final Map<String, Object> pair = requireBody(rotated);
        assertThat((String) pair.get("refreshToken")).isNotEqualTo(refreshToken);
        assertThat((String) pair.get("accessToken")).isNotBlank();

        assertThat(refresh(refreshToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that presenting a consumed refresh token closes every session of the account, so that
     * the pair issued by the successful rotation stops working as well.
     */
    @Test
    void reuseOfAConsumedTokenRevokesTheWholeSession() {
        final ResponseEntity<Map<String, Object>> registration = register(uniqueUsername());
        final String refreshToken = token(registration, "refreshToken");
        final String rotatedRefreshToken = (String) requireBody(refresh(refreshToken)).get("refreshToken");

        assertThat(refresh(refreshToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(refresh(rotatedRefreshToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Verifies that a logout closes the session and that the still unexpired access token of that
     * session no longer authorises a sensitive operation.
     */
    @Test
    void logoutClosesTheSessionOfThePresentedAccessToken() {
        final ResponseEntity<Map<String, Object>> registration = register(uniqueUsername());
        final String accessToken = token(registration, "accessToken");

        assertThat(logout(accessToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(logout(accessToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refresh(token(registration, "refreshToken")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Registers an account with a fixed profile and password.
     *
     * @param username login name to register
     * @return the response of the registration endpoint
     */
    private ResponseEntity<Map<String, Object>> register(final String username) {
        return this.client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", username,
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "password", PASSWORD))
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Authenticates with credentials.
     *
     * @param username login name of the account
     * @param password password to present
     * @return the response of the login endpoint
     */
    private ResponseEntity<Map<String, Object>> login(final String username, final String password) {
        return this.client.post()
                .uri("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Exchanges a refresh token for a new pair.
     *
     * @param refreshToken refresh token to present
     * @return the response of the refresh endpoint
     */
    private ResponseEntity<Map<String, Object>> refresh(final String refreshToken) {
        return this.client.post()
                .uri("/api/v1/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Closes the session of an access token.
     *
     * @param accessToken access token to present
     * @return the response of the logout endpoint
     */
    private ResponseEntity<Void> logout(final String accessToken) {
        return this.client.post()
                .uri("/api/v1/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Reads the cookie encryption parameters of a key handle.
     *
     * @param keyHandle handle to present in the key handle cookie
     * @return the response of the cookie parameter endpoint
     */
    private ResponseEntity<Map<String, Object>> cookieParameters(final String keyHandle) {
        return this.client.get()
                .uri("/api/v1/security/cookie-iv/")
                .header(HttpHeaders.COOKIE, "sumbooklm_key_handle=" + keyHandle)
                .retrieve()
                .toEntity(JSON_OBJECT);
    }

    /**
     * Reads one token out of an authentication response.
     *
     * @param response response of a registration or login
     * @param name     name of the token field
     * @return the token value
     */
    private static String token(final ResponseEntity<Map<String, Object>> response, final String name) {
        return (String) ((Map<?, ?>) requireBody(response).get("tokens")).get(name);
    }

    /**
     * Reads the key handle out of the cookie an authentication response sets.
     *
     * @param response response of a registration or login
     * @return the value of the key handle cookie
     */
    private static String keyHandleCookie(final ResponseEntity<Map<String, Object>> response) {
        final List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream()
                .filter(cookie -> cookie.startsWith("sumbooklm_key_handle="))
                .map(cookie -> cookie.substring("sumbooklm_key_handle=".length(), cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The response carries no key handle cookie"));
    }

    /**
     * Returns the body of a response and fails when there is none.
     *
     * @param response response to read
     * @return the parsed body
     */
    private static Map<String, Object> requireBody(final ResponseEntity<Map<String, Object>> response) {
        final Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    /**
     * Produces a username no other test uses.
     *
     * @return a username unique within the run
     */
    private static String uniqueUsername() {
        return "user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
