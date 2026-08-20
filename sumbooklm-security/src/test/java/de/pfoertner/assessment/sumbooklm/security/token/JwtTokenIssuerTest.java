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

package de.pfoertner.assessment.sumbooklm.security.token;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.security.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises what the two kinds of token claim.
 *
 * <h2>Why the Claims Are Read Rather Than the Token</h2>
 * What a deployment decides from is not the string but the claims inside it: which kind of token was
 * presented, which session it belongs to and how long it is good for. Those are set here and read
 * everywhere else, so a claim that was renamed or a validity that was read from the wrong setting
 * would show up as an access token that never expires or a refresh token accepted as an access
 * token. Signing itself belongs to the framework and is not what these cases are about.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class JwtTokenIssuerTest {

    /**
     * Moment the tokens of the cases are issued at.
     */
    private static final Instant ISSUED = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * How long an access token of the cases is good for.
     */
    private static final Duration ACCESS_VALIDITY = Duration.ofMinutes(5);

    /**
     * How long a refresh token of the cases is good for.
     */
    private static final Duration REFRESH_VALIDITY = Duration.ofDays(90);

    /**
     * Account the tokens of the cases are issued to.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Session the tokens of the cases belong to.
     */
    private final UUID sessionId = UUID.randomUUID();

    /**
     * Signer the issuer hands its claims to.
     */
    private JwtEncoder jwtEncoder;

    /**
     * Issuer under test.
     */
    private JwtTokenIssuer issuer;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    JwtTokenIssuerTest() {
    }

    /**
     * Builds the issuer and the signer it hands its claims to.
     */
    @BeforeEach
    void setUp() {
        this.jwtEncoder = mock(JwtEncoder.class);
        final SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("secret", "sumbooklm", ACCESS_VALIDITY, REFRESH_VALIDITY),
                new SecurityProperties.Cookie("secret", "handle", "payload", false),
                false);
        this.issuer = new JwtTokenIssuer(this.jwtEncoder, properties);
        when(this.jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(Jwt.withTokenValue("signed.token.value")
                        .header("alg", MacAlgorithm.HS256.getName())
                        .claim("sub", this.userId.toString())
                        .build());
    }

    /**
     * Verifies that an access token names its kind, its session, its account and the name of the
     * user, which is everything a request is authorised from.
     */
    @Test
    void anAccessTokenNamesItsSessionAndItsAccount() {
        final IssuedToken token =
                this.issuer.issueAccessToken(this.userId, "erik", this.sessionId, ISSUED);

        final JwtClaimsSet claims = capturedClaims();
        assertThat(claims.getClaimAsString(TokenClaims.TOKEN_TYPE)).isEqualTo(TokenClaims.ACCESS_TOKEN_TYPE);
        assertThat(claims.getClaimAsString(TokenClaims.SESSION_ID)).isEqualTo(this.sessionId.toString());
        assertThat(claims.getClaimAsString(TokenClaims.USERNAME)).isEqualTo("erik");
        assertThat(claims.getSubject()).isEqualTo(this.userId.toString());
        assertThat(claims.getClaimAsString("iss")).isEqualTo("sumbooklm");
        assertThat(token.value()).isEqualTo("signed.token.value");
    }

    /**
     * Verifies that an access token runs out after the span a deployment configured, counted from
     * the moment it was issued rather than from the current clock.
     */
    @Test
    void anAccessTokenRunsOutAfterTheConfiguredSpan() {
        final IssuedToken token =
                this.issuer.issueAccessToken(this.userId, "erik", this.sessionId, ISSUED);

        assertThat(token.issuedAt()).isEqualTo(ISSUED);
        assertThat(token.expiresAt()).isEqualTo(ISSUED.plus(ACCESS_VALIDITY));
        assertThat(capturedClaims().getExpiresAt()).isEqualTo(ISSUED.plus(ACCESS_VALIDITY));
    }

    /**
     * Verifies that a refresh token is issued under the identifier of its session, so that the row
     * describing the session and the token presented for it name the same thing.
     */
    @Test
    void aRefreshTokenIsIssuedUnderTheIdentifierOfItsSession() {
        final IssuedToken token =
                this.issuer.issueRefreshToken(this.userId, "erik", this.sessionId, ISSUED);

        assertThat(token.id()).isEqualTo(this.sessionId);
        assertThat(capturedClaims().getId()).isEqualTo(this.sessionId.toString());
        assertThat(capturedClaims().getClaimAsString(TokenClaims.TOKEN_TYPE))
                .isEqualTo(TokenClaims.REFRESH_TOKEN_TYPE);
    }

    /**
     * Verifies that a refresh token lives far longer than an access token, which is the whole reason
     * there are two of them.
     */
    @Test
    void aRefreshTokenOutlivesAnAccessToken() {
        final IssuedToken refresh =
                this.issuer.issueRefreshToken(this.userId, "erik", this.sessionId, ISSUED);
        final IssuedToken access =
                this.issuer.issueAccessToken(this.userId, "erik", this.sessionId, ISSUED);

        assertThat(refresh.expiresAt()).isEqualTo(ISSUED.plus(REFRESH_VALIDITY));
        assertThat(refresh.expiresAt()).isAfter(access.expiresAt());
    }

    /**
     * Verifies that a refresh token carries no session claim of its own, because the session it
     * belongs to is its identifier and a second name for it could disagree with the first.
     */
    @Test
    void aRefreshTokenCarriesNoSessionClaim() {
        this.issuer.issueRefreshToken(this.userId, "erik", this.sessionId, ISSUED);

        assertThat(capturedClaims().getClaims()).doesNotContainKey(TokenClaims.SESSION_ID);
    }

    /**
     * Verifies that two access tokens of one session are told apart by their own identifier, so that
     * one of them cannot be mistaken for the other.
     */
    @Test
    void twoAccessTokensOfOneSessionDiffer() {
        final IssuedToken first =
                this.issuer.issueAccessToken(this.userId, "erik", this.sessionId, ISSUED);
        final IssuedToken second =
                this.issuer.issueAccessToken(this.userId, "erik", this.sessionId, ISSUED);

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    /**
     * Reads the claims the issuer handed to the signer.
     *
     * @return the claims of the most recently signed token
     */
    private JwtClaimsSet capturedClaims() {
        final ArgumentCaptor<JwtEncoderParameters> parameters =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(this.jwtEncoder, atLeastOnce()).encode(parameters.capture());
        return parameters.getValue().getClaims();
    }
}
