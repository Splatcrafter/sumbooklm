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

/**
 * Names and values of the claims the application writes into its tokens.
 *
 * <h2>Purpose</h2>
 * The claims below are written by the issuer and read by the verifier, which are separate components
 * of the module. Declaring the names once keeps a rename from silently producing tokens nothing
 * accepts.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class TokenClaims {

    /**
     * Claim naming the kind of a token, so that a refresh token cannot be presented where an access
     * token is expected and the other way round.
     */
    public static final String TOKEN_TYPE = "token_type";

    /**
     * Value of {@link #TOKEN_TYPE} in an access token.
     */
    public static final String ACCESS_TOKEN_TYPE = "access";

    /**
     * Value of {@link #TOKEN_TYPE} in a refresh token.
     */
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    /**
     * Claim naming the session a token belongs to. In an access token it carries the identifier of
     * the refresh token that was issued with it, which is what lets an operation check the session
     * of an access token against the database.
     */
    public static final String SESSION_ID = "sid";

    /**
     * Claim carrying the login name of the authenticated user.
     */
    public static final String USERNAME = "username";

    /**
     * Prevents instantiation of this constant holder.
     */
    private TokenClaims() {
        throw new AssertionError("TokenClaims is a constant holder and must not be instantiated");
    }
}
