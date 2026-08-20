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
