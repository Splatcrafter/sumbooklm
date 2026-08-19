package de.pfoertner.assessment.sumbooklm.security.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalized settings of authentication, bound from the {@code sumbooklm.security} namespace.
 *
 * <h2>Secrets</h2>
 * Neither secret has a default. A missing or too short secret fails the startup rather than falling
 * back to a value that would be identical across installations.
 *
 * <h2>Transport</h2>
 * Whether the application is reached over a secure connection is a fact about the deployment rather
 * than something it can detect, which is why it is configured. It defaults to false so that a
 * developer running the application locally is not locked out, and every deployment that is reachable
 * from elsewhere has to set it.
 *
 * @param jwt                    settings of the issued tokens
 * @param cookie                 settings of the client side token storage
 * @param requireSecureTransport whether requests to the API are refused unless they arrived over a
 *                               secure connection
 * @author Erik Pförtner
 * @since 0.1.0
 */
@ConfigurationProperties("sumbooklm.security")
public record SecurityProperties(@DefaultValue Jwt jwt,
                                 @DefaultValue Cookie cookie,
                                 @DefaultValue("false") boolean requireSecureTransport) {

    /**
     * Settings of the issued tokens.
     *
     * <h2>Validity</h2>
     * The access token is short lived because it is accepted on its signature alone. The refresh
     * token is long lived because every use of it is checked against the database, where it can be
     * revoked.
     *
     * @param secret               signing key of both token kinds, at least 32 characters
     * @param issuer               value written to and expected in the {@code iss} claim
     * @param accessTokenValidity  lifetime of an access token
     * @param refreshTokenValidity lifetime of a refresh token
     */
    public record Jwt(@DefaultValue("") String secret,
                      @DefaultValue("sumbooklm") String issuer,
                      @DefaultValue("PT5M") Duration accessTokenValidity,
                      @DefaultValue("P90D") Duration refreshTokenValidity) {
    }

    /**
     * Settings of the client side token storage.
     *
     * <h2>Key Handle</h2>
     * The client stores its token pair encrypted. The key is never sent to the client as part of a
     * durable value: the browser only holds an opaque handle in a cookie it cannot read from script,
     * and the key is derived from that handle and the secret below whenever the client asks for it.
     *
     * @param secret       key derivation secret, at least 32 characters
     * @param handleName   name of the cookie carrying the opaque key handle
     * @param payloadName  name of the cookie the client stores its encrypted token pair in
     * @param secure       whether the key handle cookie is restricted to secure connections
     */
    public record Cookie(@DefaultValue("") String secret,
                         @DefaultValue("sumbooklm_key_handle") String handleName,
                         @DefaultValue("sumbooklm_auth") String payloadName,
                         @DefaultValue("false") boolean secure) {
    }
}
