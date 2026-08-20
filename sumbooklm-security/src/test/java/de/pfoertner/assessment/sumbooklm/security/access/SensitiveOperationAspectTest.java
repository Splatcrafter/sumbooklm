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

import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.security.token.RefreshTokenService;
import de.pfoertner.assessment.sumbooklm.security.token.TokenClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the second question a sensitive operation is asked.
 *
 * <h2>Why an Access Token Is Not Enough</h2>
 * An access token stays good for as long as it says, which is minutes, and nothing about it changes
 * when the session it came from is closed. For an operation that cannot be taken back, that window
 * is too wide, so the session is looked up as well. Everything below is a way of arriving at the
 * operation without an open session, and each of them has to end in the same refusal.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SensitiveOperationAspectTest {

    /**
     * Store the openness of a session is read from.
     */
    private RefreshTokenService refreshTokenService;

    /**
     * Rule under test.
     */
    private SensitiveOperationAspect aspect;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SensitiveOperationAspectTest() {
    }

    /**
     * Builds the rule and the store it asks.
     */
    @BeforeEach
    void setUp() {
        this.refreshTokenService = mock(RefreshTokenService.class);
        this.aspect = new SensitiveOperationAspect(this.refreshTokenService);
        SecurityContextHolder.clearContext();
    }

    /**
     * Leaves no authentication behind for the next case.
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Verifies that an operation reached with an open session is allowed to run.
     */
    @Test
    void anOpenSessionIsAllowed() {
        final UUID sessionId = UUID.randomUUID();
        authenticateWith(sessionId.toString());
        when(this.refreshTokenService.isSessionUsable(sessionId)).thenReturn(true);

        assertThatCode(() -> this.aspect.verifySession()).doesNotThrowAnyException();
    }

    /**
     * Verifies that an operation reached with a session that was closed is refused, which is the
     * case the rule exists for: a token that is still good for a session that is not.
     */
    @Test
    void aClosedSessionIsRefused() {
        final UUID sessionId = UUID.randomUUID();
        authenticateWith(sessionId.toString());
        when(this.refreshTokenService.isSessionUsable(sessionId)).thenReturn(false);

        assertThatThrownBy(() -> this.aspect.verifySession())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not open");
    }

    /**
     * Verifies that an operation reached without an authentication at all is refused without the
     * store being asked, because there is no session to ask about.
     */
    @Test
    void anUnauthenticatedCallerIsRefused() {
        assertThatThrownBy(() -> this.aspect.verifySession())
                .isInstanceOf(AccessDeniedException.class);
        verify(this.refreshTokenService, never()).isSessionUsable(any());
    }

    /**
     * Verifies that an authentication which is not an access token is refused, which is what a
     * caller authenticated by some other means would arrive as.
     */
    @Test
    void anAuthenticationOfAnotherKindIsRefused() {
        final Authentication other =
                new UsernamePasswordAuthenticationToken("erik", "secret", List.of());
        SecurityContextHolder.getContext().setAuthentication(other);

        assertThatThrownBy(() -> this.aspect.verifySession())
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Verifies that an access token naming no session is refused, which is what a token signed for
     * another purpose looks like.
     */
    @Test
    void aTokenWithoutASessionIsRefused() {
        authenticateWith(null);

        assertThatThrownBy(() -> this.aspect.verifySession())
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Verifies that an access token whose session is not an identifier is refused rather than
     * reaching the store as something it cannot look up.
     */
    @Test
    void aTokenWithAMalformedSessionIsRefused() {
        authenticateWith("not-a-uuid");

        assertThatThrownBy(() -> this.aspect.verifySession())
                .isInstanceOf(AccessDeniedException.class);
        verify(this.refreshTokenService, never()).isSessionUsable(any());
    }

    /**
     * Puts an access token naming one session into the context of the current thread.
     *
     * @param sessionId session the token names, or {@code null} if it names none
     */
    private static void authenticateWith(final String sessionId) {
        final Jwt.Builder builder = Jwt.withTokenValue("access.token.value")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_TYPE, TokenClaims.ACCESS_TOKEN_TYPE);
        if (sessionId != null) {
            builder.claim(TokenClaims.SESSION_ID, sessionId);
        }
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(builder.build()));
    }
}
