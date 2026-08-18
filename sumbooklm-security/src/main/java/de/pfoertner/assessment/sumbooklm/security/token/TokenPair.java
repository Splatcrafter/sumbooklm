package de.pfoertner.assessment.sumbooklm.security.token;

/**
 * The pair of tokens a successful authentication produces.
 *
 * <h2>Relation Between the Two</h2>
 * The access token names the refresh token in its {@code sid} claim. The pair is therefore not just
 * two independent tokens: an access token can be traced back to the session row that decides whether
 * it may still be used for a sensitive operation.
 *
 * @param accessToken  short lived token presented on every request
 * @param refreshToken long lived token exchanged for a new pair
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record TokenPair(IssuedToken accessToken, IssuedToken refreshToken) {
}
