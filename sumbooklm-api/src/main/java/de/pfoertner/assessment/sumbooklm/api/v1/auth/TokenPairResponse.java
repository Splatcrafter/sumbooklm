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

package de.pfoertner.assessment.sumbooklm.api.v1.auth;

import java.time.Instant;

import de.pfoertner.assessment.sumbooklm.security.token.TokenPair;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The token pair as it is returned to a client.
 *
 * <h2>Expiry Timestamps</h2>
 * Both expiry timestamps are stated explicitly so that a client can schedule a refresh without
 * decoding the tokens. They are informative: the server does not trust them and re-derives every
 * lifetime from the tokens themselves.
 *
 * @param tokenType             scheme the access token is presented with in the authorization header
 * @param accessToken           short lived token to present on subsequent requests
 * @param accessTokenExpiresAt  point in time the access token stops being accepted
 * @param refreshToken          long lived token to exchange for a new pair
 * @param refreshTokenExpiresAt point in time the refresh token stops being accepted
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Pair of an access token and a refresh token.")
public record TokenPairResponse(
        @Schema(description = "Scheme the access token is presented with.", example = "Bearer")
        String tokenType,

        @Schema(description = "Short lived token for subsequent requests.")
        String accessToken,

        @Schema(description = "Expiry of the access token.")
        Instant accessTokenExpiresAt,

        @Schema(description = "Long lived token used to obtain a new pair.")
        String refreshToken,

        @Schema(description = "Expiry of the refresh token.")
        Instant refreshTokenExpiresAt) {

    /**
     * Scheme the access token is presented with in the authorization header.
     */
    private static final String BEARER = "Bearer";

    /**
     * Converts an issued pair into its transport representation.
     *
     * @param tokens pair issued by the security module
     * @return the pair as it is returned to a client
     */
    public static TokenPairResponse from(final TokenPair tokens) {
        return new TokenPairResponse(
                BEARER,
                tokens.accessToken().value(),
                tokens.accessToken().expiresAt(),
                tokens.refreshToken().value(),
                tokens.refreshToken().expiresAt());
    }
}
