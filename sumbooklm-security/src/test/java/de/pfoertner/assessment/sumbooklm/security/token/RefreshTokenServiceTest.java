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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.persistence.token.RefreshTokenEntity;
import de.pfoertner.assessment.sumbooklm.persistence.token.RefreshTokenRepository;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises what a presented refresh token is answered with.
 *
 * <h2>Why Every Case Is an Attack or a Mistake</h2>
 * A refresh token is the one credential that outlives a session, so the interesting states are the
 * ones nobody reaches by using the application: a token that was forged, one that names a session
 * that no longer exists, one whose bytes do not match what was stored, and one that is presented a
 * second time. The last of those is the case the whole design exists for, because it means the token
 * has left its client, and it has to close the session rather than merely refuse the request.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class RefreshTokenServiceTest {

    /**
     * Moment every case is answered at.
     */
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Token every case presents.
     */
    private static final String PRESENTED = "presented.refresh.token";

    /**
     * Store of the sessions.
     */
    private RefreshTokenRepository refreshTokenRepository;

    /**
     * Source of the tokens a rotation issues.
     */
    private JwtTokenIssuer jwtTokenIssuer;

    /**
     * Reader that verifies a presented token.
     */
    private JwtDecoder refreshTokenDecoder;

    /**
     * Service under test.
     */
    private RefreshTokenService service;

    /**
     * Account the sessions of the cases belong to.
     */
    private UserAccountEntity account;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    RefreshTokenServiceTest() {
    }

    /**
     * Builds the service, its store and the account the sessions belong to.
     */
    @BeforeEach
    void setUp() {
        this.refreshTokenRepository = mock(RefreshTokenRepository.class);
        this.jwtTokenIssuer = mock(JwtTokenIssuer.class);
        this.refreshTokenDecoder = mock(JwtDecoder.class);
        this.service = new RefreshTokenService(this.refreshTokenRepository, this.jwtTokenIssuer,
                this.refreshTokenDecoder, Clock.fixed(NOW, ZoneOffset.UTC));
        this.account = new UserAccountEntity(UUID.randomUUID(), "erik", "hash", NOW, NOW,
                new byte[]{1}, 110);

        when(this.jwtTokenIssuer.issueRefreshToken(any(), any(), any(), any())).thenAnswer(invocation ->
                new IssuedToken("new.refresh.token", invocation.getArgument(2, UUID.class),
                        NOW, NOW.plusSeconds(7_776_000)));
        when(this.jwtTokenIssuer.issueAccessToken(any(), any(), any(), any())).thenAnswer(invocation ->
                new IssuedToken("new.access.token", UUID.randomUUID(), NOW, NOW.plusSeconds(300)));
    }

    /**
     * Verifies that issuing a session stores the token as a digest rather than as itself, so that
     * reading the table does not hand out credentials.
     */
    @Test
    void anIssuedSessionIsStoredAsADigest() {
        final TokenPair pair = this.service.issue(this.account, "203.0.113.7");

        final ArgumentCaptor<RefreshTokenEntity> stored =
                ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(this.refreshTokenRepository).save(stored.capture());

        assertThat(stored.getValue().getTokenHash())
                .isEqualTo(digestOf("new.refresh.token"))
                .isNotEqualTo("new.refresh.token");
        assertThat(stored.getValue().getIssuedToIpAddress()).isEqualTo("203.0.113.7");
        assertThat(stored.getValue().getId()).isEqualTo(pair.refreshToken().id());
    }

    /**
     * Verifies that a token which does not verify is refused without the store being asked at all,
     * because a forged token names nothing worth looking up.
     */
    @Test
    void aForgedTokenIsRefusedWithoutALookup() {
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenThrow(new BadJwtException("bad signature"));

        assertThatThrownBy(() -> this.service.rotate(PRESENTED, "203.0.113.7"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(this.refreshTokenRepository, never()).findById(any());
    }

    /**
     * Verifies that a token naming no session is refused, which is what a token signed for another
     * purpose looks like.
     */
    @Test
    void aTokenWithoutASessionIsRefused() {
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenReturn(jwtWithId(null));

        assertThatThrownBy(() -> this.service.rotate(PRESENTED, "203.0.113.7"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    /**
     * Verifies that a token whose session is not an identifier at all is refused rather than
     * reaching the store as something it cannot look up.
     */
    @Test
    void aTokenWithAMalformedSessionIsRefused() {
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenReturn(jwtWithId("not-a-uuid"));

        assertThatThrownBy(() -> this.service.rotate(PRESENTED, "203.0.113.7"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(this.refreshTokenRepository, never()).findById(any());
    }

    /**
     * Verifies that a token naming a session the store does not hold is refused, which is what a
     * token presented after the weekly cleanup removed its session amounts to.
     */
    @Test
    void aTokenOfAnUnknownSessionIsRefused() {
        final UUID sessionId = UUID.randomUUID();
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenReturn(jwtWithId(sessionId.toString()));
        when(this.refreshTokenRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.rotate(PRESENTED, "203.0.113.7"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    /**
     * Verifies that a token which verifies but does not match what was stored is refused, and that
     * the session it named is left open, because the session did nothing wrong.
     */
    @Test
    void aTokenThatDoesNotMatchTheStoredOneIsRefused() {
        final UUID sessionId = UUID.randomUUID();
        final RefreshTokenEntity session = session(sessionId, "another.token");
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenReturn(jwtWithId(sessionId.toString()));
        when(this.refreshTokenRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> this.service.rotate(PRESENTED, "203.0.113.7"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(session.getRevokedAt()).isNull();
        verify(this.refreshTokenRepository, never()).revokeAllOfUser(any(), any());
    }

    /**
     * Verifies that a token presented a second time closes every session of its account, because a
     * token that is used twice has left the client it was issued to and neither holder can be told
     * from the other.
     */
    @Test
    void aTokenPresentedTwiceClosesEverySessionOfTheAccount() {
        final UUID sessionId = UUID.randomUUID();
        final RefreshTokenEntity session = session(sessionId, PRESENTED);
        session.revoke(NOW.minusSeconds(60));
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenReturn(jwtWithId(sessionId.toString()));
        when(this.refreshTokenRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> this.service.rotate(PRESENTED, "203.0.113.7"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(this.refreshTokenRepository).revokeAllOfUser(this.account.getId(), NOW);
    }

    /**
     * Verifies that a token of a session which ran out is refused the same way, and that it closes
     * the sessions of the account as well, because the state is indistinguishable from a reuse.
     */
    @Test
    void aTokenOfASessionThatRanOutIsRefused() {
        final UUID sessionId = UUID.randomUUID();
        final RefreshTokenEntity expired = new RefreshTokenEntity(sessionId, this.account,
                digestOf(PRESENTED), NOW.minusSeconds(7_200), NOW.minusSeconds(60), "203.0.113.7");
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenReturn(jwtWithId(sessionId.toString()));
        when(this.refreshTokenRepository.findById(sessionId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> this.service.rotate(PRESENTED, "203.0.113.7"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(this.refreshTokenRepository).revokeAllOfUser(this.account.getId(), NOW);
    }

    /**
     * Verifies that a rotation ends the session that was presented and opens a new one, so that a
     * refresh token is good exactly once.
     */
    @Test
    void aRotationEndsThePresentedSessionAndOpensANewOne() {
        final UUID sessionId = UUID.randomUUID();
        final RefreshTokenEntity session = session(sessionId, PRESENTED);
        when(this.refreshTokenDecoder.decode(PRESENTED)).thenReturn(jwtWithId(sessionId.toString()));
        when(this.refreshTokenRepository.findById(sessionId)).thenReturn(Optional.of(session));

        final TokenPair pair = this.service.rotate(PRESENTED, "198.51.100.4");

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        assertThat(pair.refreshToken().id()).isNotEqualTo(sessionId);
        assertThat(pair.accessToken().value()).isEqualTo("new.access.token");
        verify(this.refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    /**
     * Verifies that a session is judged as open only while it is neither ended nor ran out, and that
     * a session nobody stored is not open either.
     */
    @Test
    void aSessionIsOpenOnlyWhileItIsUsable() {
        final UUID open = UUID.randomUUID();
        final UUID ended = UUID.randomUUID();
        final RefreshTokenEntity closed = session(ended, PRESENTED);
        closed.revoke(NOW.minusSeconds(1));
        when(this.refreshTokenRepository.findById(open)).thenReturn(Optional.of(session(open, PRESENTED)));
        when(this.refreshTokenRepository.findById(ended)).thenReturn(Optional.of(closed));

        assertThat(this.service.isSessionUsable(open)).isTrue();
        assertThat(this.service.isSessionUsable(ended)).isFalse();
        assertThat(this.service.isSessionUsable(UUID.randomUUID())).isFalse();
    }

    /**
     * Verifies that ending a session records the moment it was ended, and that ending one twice
     * keeps the first moment, so that a repeated logout cannot rewrite when the session closed.
     */
    @Test
    void endingASessionTwiceKeepsTheFirstMoment() {
        final UUID sessionId = UUID.randomUUID();
        final RefreshTokenEntity session = session(sessionId, PRESENTED);
        when(this.refreshTokenRepository.findById(sessionId)).thenReturn(Optional.of(session));

        this.service.revokeSession(sessionId);
        final Instant first = session.getRevokedAt();
        this.service.revokeSession(sessionId);

        assertThat(first).isEqualTo(NOW);
        assertThat(session.getRevokedAt()).isEqualTo(first);
    }

    /**
     * Verifies that ending a session nobody stored is answered with nothing rather than with a
     * failure, because a client may log out with a session that has already been cleaned up.
     */
    @Test
    void endingAnUnknownSessionChangesNothing() {
        when(this.refreshTokenRepository.findById(any())).thenReturn(Optional.empty());

        this.service.revokeSession(UUID.randomUUID());
    }

    /**
     * Verifies that the cleanup removes what is no longer usable as of now, which is what keeps the
     * table from growing without bound.
     */
    @Test
    void theCleanupRemovesWhatIsNoLongerUsable() {
        when(this.refreshTokenRepository.deleteInvalidated(NOW)).thenReturn(7);

        assertThat(this.service.deleteInvalidatedTokens()).isEqualTo(7);
        verify(this.refreshTokenRepository).deleteInvalidated(eq(NOW));
    }

    /**
     * Builds a stored session of the account of the cases.
     *
     * @param sessionId identifier the session is stored under
     * @param token     token the session was issued with
     * @return the stored session
     */
    private RefreshTokenEntity session(final UUID sessionId, final String token) {
        return new RefreshTokenEntity(sessionId, this.account, digestOf(token),
                NOW.minusSeconds(60), NOW.plusSeconds(7_776_000), "203.0.113.7");
    }

    /**
     * Builds a verified token naming one session.
     *
     * @param id identifier the token names, or {@code null} if it names none
     * @return the verified token
     */
    private static Jwt jwtWithId(final String id) {
        final Jwt.Builder builder = Jwt.withTokenValue(PRESENTED)
                .header("alg", "HS256")
                .claim(TokenClaims.TOKEN_TYPE, TokenClaims.REFRESH_TOKEN_TYPE)
                .subject(UUID.randomUUID().toString());
        if (id != null) {
            builder.jti(id);
        }
        return builder.build();
    }

    /**
     * Computes the digest a token is stored under.
     *
     * @param token token to digest
     * @return the digest of the token, as the store holds it
     */
    private static String digestOf(final String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but not available", e);
        }
    }
}
