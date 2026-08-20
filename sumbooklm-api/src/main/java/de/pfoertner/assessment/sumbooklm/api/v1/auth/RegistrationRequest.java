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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of a registration request.
 *
 * <h2>Username</h2>
 * The username is restricted to characters that survive being part of a path, a log line and a
 * database index without escaping, which keeps it usable as an identifier everywhere it appears.
 *
 * @param username  login name to create the account under
 * @param firstName given name of the user
 * @param lastName  family name of the user
 * @param password  clear text password of the new account
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Data required to create an account.")
public record RegistrationRequest(
        @Schema(description = "Login name, unique across all accounts.", example = "erik")
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$")
        String username,

        @Schema(description = "Given name of the user.", example = "Erik")
        @NotBlank @Size(max = 128)
        String firstName,

        @Schema(description = "Family name of the user.", example = "Pfoertner")
        @NotBlank @Size(max = 128)
        String lastName,

        @Schema(description = "Clear text password of the new account.")
        @NotBlank @Size(min = 12, max = 256)
        String password) {
}
