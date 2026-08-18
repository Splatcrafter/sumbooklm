package de.pfoertner.assessment.sumbooklm.security.authentication;

import de.pfoertner.assessment.sumbooklm.domain.user.UserAccount;
import de.pfoertner.assessment.sumbooklm.security.token.TokenPair;

/**
 * Outcome of a successful registration or login.
 *
 * <h2>Key Handle</h2>
 * The handle is issued together with the token pair and identifies the key the client encrypts its
 * stored copy of that pair with. It is meaningless on its own: without the configured derivation
 * secret it cannot be turned into a key.
 *
 * @param account        the authenticated account
 * @param tokens         the freshly issued token pair
 * @param cookieKeyHandle handle identifying the encryption key of the client side token store
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record AuthenticationResult(UserAccount account, TokenPair tokens, String cookieKeyHandle) {
}
