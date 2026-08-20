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

package de.pfoertner.assessment.sumbooklm.api.support;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the step that turns a verified token into the account it stands for.
 *
 * <h2>Why a Verified Token Is Still Questioned</h2>
 * By the time this runs, the signature has already been checked, so the token was issued by this
 * deployment. What has not been checked is whether what it says is usable: a subject that is missing
 * or is not an identifier would otherwise be carried into a query as a filter, and a query filtered
 * by nothing is a query over everybody.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class AuthenticatedUserResolverTest {

    /**
     * Resolver under test.
     */
    private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

    /**
     * Creates the test class.
     */
    AuthenticatedUserResolverTest() {
    }

    /**
     * Verifies that a token naming an account is resolved to it.
     */
    @Test
    void aTokenNamingAnAccountIsResolved() {
        final UUID userId = UUID.randomUUID();

        assertThat(this.resolver.requireUserId(token(userId.toString()))).isEqualTo(userId);
    }

    /**
     * Verifies that a token naming no account at all is refused, rather than resolved to nothing.
     */
    @Test
    void aTokenWithoutAnAccountIsRefused() {
        assertThatThrownBy(() -> this.resolver.requireUserId(token(null)))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("no subject");
    }

    /**
     * Verifies that a token whose subject is not an identifier is refused, which is what a token
     * signed for another purpose by the same secret would carry.
     */
    @Test
    void aTokenWithAMalformedAccountIsRefused() {
        assertThatThrownBy(() -> this.resolver.requireUserId(token("erik")))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("not an account identifier");
    }

    /**
     * Verifies that a subject which is nearly an identifier is refused as well, because a truncated
     * one is what a client that built its own token would send.
     */
    @Test
    void aSubjectThatIsNearlyAnIdentifierIsRefused() {
        assertThatThrownBy(() -> this.resolver.requireUserId(token("0f9c1a2b-3d4e-5f60-7a8b")))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> this.resolver.requireUserId(token("")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    /**
     * Builds a verified token naming one subject.
     *
     * @param subject subject the token names, or {@code null} if it names none
     * @return the verified token
     */
    private static Jwt token(final String subject) {
        final Jwt.Builder builder = Jwt.withTokenValue("access.token.value")
                .header("alg", "HS256")
                .claim("token_type", "access");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
