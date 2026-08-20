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

package de.pfoertner.assessment.sumbooklm.domain.user;

import java.util.Objects;
import java.util.UUID;

/**
 * A registered user of the application.
 *
 * <h2>Identity</h2>
 * An account is identified by {@code id}, which is stable for the lifetime of the account. The
 * username is unique as well, but it is a login credential rather than an identifier, and nothing
 * outside the authentication flow resolves an account by it.
 *
 * <h2>Absent Data</h2>
 * The account carries no password hash and no tokens. Both exist only where they are needed: the
 * hash in the persistence layer, the tokens in the security layer.
 *
 * @param id       stable identifier of the account, never {@code null}
 * @param username unique login name of the user, never {@code null}
 * @param profile  name the user is addressed by, never {@code null}
 * @param activity audit metadata of the account, never {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record UserAccount(UUID id, String username, UserProfile profile, AccountActivity activity) {

    /**
     * Creates the account.
     *
     * @param id       stable identifier of the account
     * @param username unique login name of the user
     * @param profile  name the user is addressed by
     * @param activity audit metadata of the account
     * @throws NullPointerException if any argument is {@code null}
     */
    public UserAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(activity, "activity must not be null");
    }
}
