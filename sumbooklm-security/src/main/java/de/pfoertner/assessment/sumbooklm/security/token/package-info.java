/**
 * Issuing, rotation and revocation of the token pair.
 *
 * <h2>Token Pair</h2>
 * Authentication produces two tokens with deliberately different trust models. The access token is
 * accepted on its signature alone and therefore expires within minutes. The refresh token is only
 * accepted if the database still holds a usable row for it, which is what makes revocation possible
 * despite a signature that stays valid for months.
 *
 * <h2>Rotation</h2>
 * Every use of a refresh token consumes it: the presented token is revoked and a new pair is issued.
 * A token that is presented after it was consumed is treated as a leaked token, and the whole
 * session of the account is revoked in response.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.security.token;
