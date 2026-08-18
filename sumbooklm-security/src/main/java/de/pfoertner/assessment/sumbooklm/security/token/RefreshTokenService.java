package de.pfoertner.assessment.sumbooklm.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.persistence.token.RefreshTokenEntity;
import de.pfoertner.assessment.sumbooklm.persistence.token.RefreshTokenRepository;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the lifecycle of refresh tokens and of the sessions they represent.
 *
 * <h2>Two Independent Checks</h2>
 * A presented refresh token has to pass two checks that catch different failures. The signature and
 * the expiry claim are verified cryptographically, which rejects forged and outdated tokens. The
 * database row is then consulted, which rejects tokens that are correctly signed but were rotated or
 * revoked in the meantime.
 *
 * <h2>Reuse Detection</h2>
 * A refresh token that is presented after it has already been consumed is evidence that the token
 * left the client it was issued to. The response is not limited to rejecting that one token: every
 * session of the account is revoked, because the application cannot tell which side of the exchange
 * is the legitimate one.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class RefreshTokenService {

    /**
     * Digest algorithm the stored token hash is produced with.
     */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * Storage of the issued tokens.
     */
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Issuer used to sign the tokens of a newly created pair.
     */
    private final JwtTokenIssuer jwtTokenIssuer;

    /**
     * Verifier that accepts refresh tokens only.
     */
    private final JwtDecoder refreshTokenDecoder;

    /**
     * Source of the current time, so that expiry can be evaluated deterministically in tests.
     */
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param refreshTokenRepository storage of the issued tokens
     * @param jwtTokenIssuer         issuer used to sign new tokens
     * @param refreshTokenDecoder    verifier that accepts refresh tokens only
     * @param clock                  source of the current time
     */
    public RefreshTokenService(final RefreshTokenRepository refreshTokenRepository,
                               final JwtTokenIssuer jwtTokenIssuer,
                               @Qualifier("refreshTokenDecoder") final JwtDecoder refreshTokenDecoder,
                               final Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.clock = clock;
    }

    /**
     * Issues a token pair and opens the session it belongs to.
     *
     * @param user      account the pair is issued for
     * @param ipAddress network address the pair is issued to
     * @return the issued pair
     */
    @Transactional
    public TokenPair issue(final UserAccountEntity user, final String ipAddress) {
        final Instant now = Instant.now(this.clock);
        final UUID sessionId = UUID.randomUUID();

        final IssuedToken refreshToken =
                this.jwtTokenIssuer.issueRefreshToken(user.getId(), user.getUsername(), sessionId, now);
        this.refreshTokenRepository.save(new RefreshTokenEntity(
                sessionId,
                user,
                digestOf(refreshToken.value()),
                refreshToken.issuedAt(),
                refreshToken.expiresAt(),
                ipAddress));

        final IssuedToken accessToken =
                this.jwtTokenIssuer.issueAccessToken(user.getId(), user.getUsername(), sessionId, now);
        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * Exchanges a refresh token for a new pair and consumes the presented token.
     *
     * @param refreshToken refresh token presented by the client
     * @param ipAddress    network address the new pair is issued to
     * @return the newly issued pair
     * @throws InvalidRefreshTokenException if the token fails verification or is no longer usable
     */
    // Rejection is signalled by an unchecked exception, which would otherwise roll back the session
    // wide revocation that reuse detection performs on exactly that path.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenPair rotate(final String refreshToken, final String ipAddress) {
        final Jwt verified = verify(refreshToken);
        final UUID sessionId = sessionIdOf(verified);
        final Instant now = Instant.now(this.clock);

        final RefreshTokenEntity stored = this.refreshTokenRepository.findById(sessionId)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!MessageDigest.isEqual(
                stored.getTokenHash().getBytes(StandardCharsets.UTF_8),
                digestOf(refreshToken).getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidRefreshTokenException();
        }
        if (!stored.isUsableAt(now)) {
            // A token that is presented after it was consumed has left its client. Which of the two
            // holders is the legitimate one cannot be decided here, so the whole session is closed.
            this.refreshTokenRepository.revokeAllOfUser(stored.getUser().getId(), now);
            throw new InvalidRefreshTokenException();
        }

        stored.revoke(now);
        return issue(stored.getUser(), ipAddress);
    }

    /**
     * Reports whether the session behind an access token is still open.
     *
     * @param sessionId identifier taken from the {@code sid} claim of an access token
     * @return {@code true} if the session exists and is neither revoked nor expired
     */
    @Transactional(readOnly = true)
    public boolean isSessionUsable(final UUID sessionId) {
        final Instant now = Instant.now(this.clock);
        return this.refreshTokenRepository.findById(sessionId)
                .map(token -> token.isUsableAt(now))
                .orElse(Boolean.FALSE);
    }

    /**
     * Closes a session, which invalidates its refresh token and every sensitive operation that
     * relies on it.
     *
     * @param sessionId identifier of the session to close
     */
    @Transactional
    public void revokeSession(final UUID sessionId) {
        final Instant now = Instant.now(this.clock);
        this.refreshTokenRepository.findById(sessionId)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.revoke(now));
    }

    /**
     * Removes every token that can no longer be used.
     *
     * @return number of removed rows
     */
    @Transactional
    public int deleteInvalidatedTokens() {
        return this.refreshTokenRepository.deleteInvalidated(Instant.now(this.clock));
    }

    /**
     * Verifies signature, issuer, expiry and kind of a presented refresh token.
     *
     * @param refreshToken refresh token presented by the client
     * @return the verified token
     * @throws InvalidRefreshTokenException if any verification step rejects the token
     */
    private Jwt verify(final String refreshToken) {
        try {
            return this.refreshTokenDecoder.decode(refreshToken);
        } catch (final JwtException e) {
            throw new InvalidRefreshTokenException();
        }
    }

    /**
     * Reads the session identifier a verified refresh token was issued under.
     *
     * @param verified verified refresh token
     * @return identifier of the session the token belongs to
     * @throws InvalidRefreshTokenException if the token carries no usable identifier
     */
    private UUID sessionIdOf(final Jwt verified) {
        try {
            return UUID.fromString(Optional.ofNullable(verified.getId())
                    .orElseThrow(InvalidRefreshTokenException::new));
        } catch (final IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        }
    }

    /**
     * Computes the digest stored in place of a token.
     *
     * @param token token to digest
     * @return hexadecimal SHA-256 digest of the token
     */
    private static String digestOf(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is required but not available", e);
        }
    }
}
