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

package de.pfoertner.assessment.sumbooklm.security.access;

import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.security.token.RefreshTokenService;
import de.pfoertner.assessment.sumbooklm.security.token.TokenClaims;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link SensitiveOperation} by checking the session behind the presented access token.
 *
 * <h2>Where the Session Identifier Comes From</h2>
 * An access token carries the identifier of the refresh token it was issued with in its
 * {@code sid} claim. The aspect reads that claim from the authenticated token of the current
 * security context and asks the token service whether the session is still open.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Aspect
@Component
public class SensitiveOperationAspect {

    /**
     * Service that decides whether a session is still open.
     */
    private final RefreshTokenService refreshTokenService;

    /**
     * Creates the aspect.
     *
     * @param refreshTokenService service that decides whether a session is still open
     */
    public SensitiveOperationAspect(final RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Denies the call unless the session of the presented access token is still open.
     *
     * @throws AccessDeniedException if the caller presented no access token, the token names no
     *                               session, or the session is revoked, expired or unknown
     */
    @Before("@annotation(de.pfoertner.assessment.sumbooklm.security.access.SensitiveOperation)")
    public void verifySession() {
        final UUID sessionId = currentSessionId();
        if (sessionId == null || !this.refreshTokenService.isSessionUsable(sessionId)) {
            throw new AccessDeniedException("The session of the presented access token is not open");
        }
    }

    /**
     * Reads the session identifier from the access token of the current security context.
     *
     * @return the identifier, or {@code null} if the context carries no access token that names a
     *         session in a readable form
     */
    private @Nullable UUID currentSessionId() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        final String sessionId = jwtAuthentication.getToken().getClaimAsString(TokenClaims.SESSION_ID);
        if (sessionId == null) {
            return null;
        }
        try {
            return UUID.fromString(sessionId);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
