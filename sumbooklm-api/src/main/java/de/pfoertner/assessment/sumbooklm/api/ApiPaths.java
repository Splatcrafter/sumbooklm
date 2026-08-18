package de.pfoertner.assessment.sumbooklm.api;

/**
 * Path constants shared by the transport layer and by the components that host it.
 *
 * <h2>Purpose</h2>
 * The prefix below which the application exposes its REST endpoints is referenced by more than one
 * module. Controllers use it to derive their request mappings, and the single page application host
 * uses it to distinguish API requests from navigation requests that have to be answered with the
 * application shell. Keeping the value in one place prevents the two sides from drifting apart.
 *
 * <h2>Versioning</h2>
 * Endpoints live below a version segment rather than directly below {@link #BASE}. A breaking change
 * to a payload or a status code is published as a second version next to the first one, so that a
 * client keeps working until it decides to move.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ApiPaths {

    /**
     * Prefix below which all REST endpoints of the application are exposed, without a trailing slash.
     */
    public static final String BASE = "/api";

    /**
     * Prefix of the first version of the API, without a trailing slash.
     */
    public static final String V1 = BASE + "/v1";

    /**
     * Endpoint that creates an account and authenticates it.
     */
    public static final String V1_REGISTER = V1 + "/register";

    /**
     * Endpoint that exchanges credentials for a token pair.
     */
    public static final String V1_LOGIN = V1 + "/login";

    /**
     * Endpoint that exchanges a refresh token for a new token pair.
     */
    public static final String V1_TOKEN_REFRESH = V1 + "/token/refresh";

    /**
     * Endpoint that closes the session of the presented access token.
     */
    public static final String V1_LOGOUT = V1 + "/logout";

    /**
     * Endpoint that hands a client the parameters its stored token pair is encrypted with. The
     * trailing slash is part of the published path.
     */
    public static final String V1_SECURITY_COOKIE_IV = V1 + "/security/cookie-iv/";

    /**
     * Prevents instantiation of this constant holder.
     */
    private ApiPaths() {
        throw new AssertionError("ApiPaths is a constant holder and must not be instantiated");
    }
}
