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

import java.time.Instant;
import java.util.Objects;

/**
 * Audit metadata recorded for an account.
 *
 * <h2>Content</h2>
 * The record answers when an account came into existence and when it was last used, and it keeps the
 * network origin of both events. Registration data is written once; the login data is overwritten on
 * every successful authentication, so the record describes the most recent login rather than a
 * history of logins.
 *
 * @param registeredAt           point in time the account was created, never {@code null}
 * @param registrationIpAddress  network address the registration was requested from, never {@code null}
 * @param lastLoginAt            point in time of the most recent successful login, never {@code null}
 * @param lastLoginIpAddress     network address of the most recent successful login, never {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record AccountActivity(Instant registeredAt,
                              String registrationIpAddress,
                              Instant lastLoginAt,
                              String lastLoginIpAddress) {

    /**
     * Creates the activity record.
     *
     * @param registeredAt          point in time the account was created
     * @param registrationIpAddress network address the registration was requested from
     * @param lastLoginAt           point in time of the most recent successful login
     * @param lastLoginIpAddress    network address of the most recent successful login
     * @throws NullPointerException if any argument is {@code null}
     */
    public AccountActivity {
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
        Objects.requireNonNull(registrationIpAddress, "registrationIpAddress must not be null");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt must not be null");
        Objects.requireNonNull(lastLoginIpAddress, "lastLoginIpAddress must not be null");
    }
}
