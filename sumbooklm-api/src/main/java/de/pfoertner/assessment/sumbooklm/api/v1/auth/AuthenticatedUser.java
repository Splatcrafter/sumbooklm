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
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.user.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The account a token pair was issued for.
 *
 * <h2>Omissions</h2>
 * The representation deliberately leaves out the network addresses the domain model records. They
 * are audit data and have no use in the client that produced them.
 *
 * @param id           stable identifier of the account
 * @param username     login name of the account
 * @param firstName    given name of the user
 * @param lastName     family name of the user
 * @param registeredAt point in time the account was created
 * @param lastLoginAt  point in time of the most recent successful login
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Account a token pair belongs to.")
public record AuthenticatedUser(
        @Schema(description = "Stable identifier of the account.")
        UUID id,

        @Schema(description = "Login name of the account.", example = "erik")
        String username,

        @Schema(description = "Given name of the user.", example = "Erik")
        String firstName,

        @Schema(description = "Family name of the user.", example = "Pfoertner")
        String lastName,

        @Schema(description = "Point in time the account was created.")
        Instant registeredAt,

        @Schema(description = "Point in time of the most recent successful login.")
        Instant lastLoginAt) {

    /**
     * Converts a domain account into its transport representation.
     *
     * @param account account as the domain model describes it
     * @return the account as it is returned to a client
     */
    public static AuthenticatedUser from(final UserAccount account) {
        return new AuthenticatedUser(
                account.id(),
                account.username(),
                account.profile().firstName(),
                account.profile().lastName(),
                account.activity().registeredAt(),
                account.activity().lastLoginAt());
    }
}
