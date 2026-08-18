package de.pfoertner.assessment.sumbooklm.api.support;

import java.util.Optional;

import de.pfoertner.assessment.sumbooklm.security.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Writes and reads the cookie that carries the key handle of a client.
 *
 * <h2>Cookie Attributes</h2>
 * The cookie is marked {@code HttpOnly}, so that the page which stores the encrypted token pair
 * cannot read the handle that unlocks it, and {@code SameSite=Strict}, so that it is not attached to
 * requests a third party site triggers. Its lifetime matches the refresh token, because the handle
 * becomes useless once the session it belongs to can no longer be refreshed.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class KeyHandleCookieFactory {

    /**
     * Settings the cookie name, the secure flag and the lifetime are read from.
     */
    private final SecurityProperties properties;

    /**
     * Creates the factory.
     *
     * @param properties settings of the cookie
     */
    public KeyHandleCookieFactory(final SecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds the cookie that carries a key handle.
     *
     * @param keyHandle handle issued for the client
     * @return the cookie to attach to the response
     */
    public ResponseCookie create(final String keyHandle) {
        return ResponseCookie.from(this.properties.cookie().handleName(), keyHandle)
                .httpOnly(true)
                .secure(this.properties.cookie().secure())
                .sameSite("Strict")
                .path("/")
                .maxAge(this.properties.jwt().refreshTokenValidity())
                .build();
    }

    /**
     * Builds the cookie that removes a previously issued key handle.
     *
     * @return the cookie to attach to the response
     */
    public ResponseCookie expire() {
        return ResponseCookie.from(this.properties.cookie().handleName(), "")
                .httpOnly(true)
                .secure(this.properties.cookie().secure())
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    /**
     * Reads the key handle a request carries.
     *
     * @param request request to read the cookie from
     * @return the handle, or {@code null} if the request carries no key handle cookie
     */
    public @Nullable String read(final HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, this.properties.cookie().handleName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }
}
