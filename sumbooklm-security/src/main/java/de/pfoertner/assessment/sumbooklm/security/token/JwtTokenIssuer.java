package de.pfoertner.assessment.sumbooklm.security.token;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.security.config.SecurityProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Signs the access and refresh tokens of the application.
 *
 * <h2>Claims</h2>
 * Both tokens carry the issuer, the account identifier as subject, an identifier of their own, and
 * the login name. They differ in {@link TokenClaims#TOKEN_TYPE} and in their lifetime. The access
 * token additionally carries {@link TokenClaims#SESSION_ID}, which names the refresh token it was
 * issued with.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class JwtTokenIssuer {

    /**
     * Signer the tokens are produced with.
     */
    private final JwtEncoder jwtEncoder;

    /**
     * Issuer and lifetimes written into the tokens.
     */
    private final SecurityProperties.Jwt settings;

    /**
     * Creates the issuer.
     *
     * @param jwtEncoder signer the tokens are produced with
     * @param properties settings the issuer and the lifetimes are read from
     */
    public JwtTokenIssuer(final JwtEncoder jwtEncoder, final SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.settings = properties.jwt();
    }

    /**
     * Issues an access token for a session.
     *
     * @param userId    identifier of the authenticated account
     * @param username  login name of the authenticated account
     * @param sessionId identifier of the refresh token the access token belongs to
     * @param issuedAt  point in time the token is issued at
     * @return the signed access token
     */
    public IssuedToken issueAccessToken(final UUID userId,
                                        final String username,
                                        final UUID sessionId,
                                        final Instant issuedAt) {
        final Instant expiresAt = issuedAt.plus(this.settings.accessTokenValidity());
        final UUID tokenId = UUID.randomUUID();
        final JwtClaimsSet claims = baseClaims(userId, username, tokenId, issuedAt, expiresAt)
                .claim(TokenClaims.TOKEN_TYPE, TokenClaims.ACCESS_TOKEN_TYPE)
                .claim(TokenClaims.SESSION_ID, sessionId.toString())
                .build();
        return new IssuedToken(sign(claims), tokenId, issuedAt, expiresAt);
    }

    /**
     * Issues a refresh token.
     *
     * @param userId   identifier of the authenticated account
     * @param username login name of the authenticated account
     * @param tokenId  identifier the token is issued under, used as the key of its database row
     * @param issuedAt point in time the token is issued at
     * @return the signed refresh token
     */
    public IssuedToken issueRefreshToken(final UUID userId,
                                         final String username,
                                         final UUID tokenId,
                                         final Instant issuedAt) {
        final Instant expiresAt = issuedAt.plus(this.settings.refreshTokenValidity());
        final JwtClaimsSet claims = baseClaims(userId, username, tokenId, issuedAt, expiresAt)
                .claim(TokenClaims.TOKEN_TYPE, TokenClaims.REFRESH_TOKEN_TYPE)
                .build();
        return new IssuedToken(sign(claims), tokenId, issuedAt, expiresAt);
    }

    /**
     * Builds the claims both token kinds share.
     *
     * @param userId    identifier of the authenticated account
     * @param username  login name of the authenticated account
     * @param tokenId   identifier of the token
     * @param issuedAt  point in time the token is issued at
     * @param expiresAt point in time the token stops being accepted
     * @return a claims builder pre-filled with the shared claims
     */
    private JwtClaimsSet.Builder baseClaims(final UUID userId,
                                            final String username,
                                            final UUID tokenId,
                                            final Instant issuedAt,
                                            final Instant expiresAt) {
        return JwtClaimsSet.builder()
                .issuer(this.settings.issuer())
                .subject(userId.toString())
                .id(tokenId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TokenClaims.USERNAME, username);
    }

    /**
     * Signs a claims set.
     *
     * @param claims claims to sign
     * @return the encoded and signed token
     */
    private String sign(final JwtClaimsSet claims) {
        final JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        final Jwt jwt = this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
        return jwt.getTokenValue();
    }
}
