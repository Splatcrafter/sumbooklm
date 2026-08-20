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

package de.pfoertner.assessment.sumbooklm.api.v1.security;

import de.pfoertner.assessment.sumbooklm.security.cookie.CookieCryptographyParameters;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The cryptographic parameters a client protects its stored token pair with.
 *
 * <h2>Use</h2>
 * The values map onto the Web Crypto API of a browser: the key is imported for the named algorithm,
 * the vector is used for the next encryption, and the tag length is passed to the cipher. Decryption
 * uses the vector the client stored alongside its ciphertext rather than the one returned here.
 *
 * @param cookieName                 name of the cookie the client stores its token pair in
 * @param algorithm                  name of the cipher the parameters apply to
 * @param keyLength                  length of the key in bits
 * @param initializationVectorLength expected length of an initialization vector in bytes
 * @param authenticationTagLength    length of the authentication tag in bits
 * @param key                        Base64 encoded key of the calling client
 * @param initializationVector       Base64 encoded vector to use for the next encryption
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Parameters for encrypting and decrypting the client side token store.")
public record CookieCryptographyResponse(
        @Schema(description = "Name of the cookie the client stores its token pair in.",
                example = "sumbooklm_auth")
        String cookieName,

        @Schema(description = "Name of the cipher.", example = "AES-GCM")
        String algorithm,

        @Schema(description = "Length of the key in bits.", example = "256")
        int keyLength,

        @Schema(description = "Expected length of an initialization vector in bytes.", example = "12")
        int initializationVectorLength,

        @Schema(description = "Length of the authentication tag in bits.", example = "128")
        int authenticationTagLength,

        @Schema(description = "Base64 encoded key of the calling client.")
        String key,

        @Schema(description = "Base64 encoded vector to use for the next encryption.")
        String initializationVector) {

    /**
     * Converts derived parameters into their transport representation.
     *
     * @param parameters parameters derived by the security module
     * @return the parameters as they are returned to a client
     */
    public static CookieCryptographyResponse from(final CookieCryptographyParameters parameters) {
        return new CookieCryptographyResponse(
                parameters.cookieName(),
                parameters.algorithm(),
                parameters.keyLength(),
                parameters.initializationVectorLength(),
                parameters.authenticationTagLength(),
                parameters.key(),
                parameters.initializationVector());
    }
}
