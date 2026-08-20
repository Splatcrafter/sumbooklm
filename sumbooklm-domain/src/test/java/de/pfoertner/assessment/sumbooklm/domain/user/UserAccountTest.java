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
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules an account is described by.
 *
 * <h2>What an Account Does Not Carry</h2>
 * The record holds no password hash and no token, and the cases below state that by building an
 * account from everything it does hold. A field added there later would have to be answered here,
 * which is the point at which somebody has to decide whether a secret belongs in a record that
 * travels into a response.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class UserAccountTest {

    /**
     * Point in time every case is built with, chosen so that nothing depends on the current clock.
     */
    private static final Instant WHEN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Name the user of the account under test is addressed by.
     */
    private final UserProfile profile = new UserProfile("Erik", "Pfoertner");

    /**
     * Audit metadata of the account under test.
     */
    private final AccountActivity activity = new AccountActivity(WHEN, "203.0.113.7", WHEN, "203.0.113.7");

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    UserAccountTest() {
    }

    /**
     * Verifies that an account keeps the identity and the metadata it was built from.
     */
    @Test
    void completeDataIsKept() {
        final UUID id = UUID.randomUUID();

        final UserAccount account = new UserAccount(id, "erik", this.profile, this.activity);

        assertThat(account.id()).isEqualTo(id);
        assertThat(account.username()).isEqualTo("erik");
        assertThat(account.profile()).isEqualTo(this.profile);
        assertThat(account.activity()).isEqualTo(this.activity);
    }

    /**
     * Verifies that every part of an account is required, so that no account can exist without an
     * identity, a login name or its audit metadata.
     */
    @Test
    void everyPartIsRequired() {
        final UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new UserAccount(null, "erik", this.profile, this.activity))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> new UserAccount(id, null, this.profile, this.activity))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("username");
        assertThatThrownBy(() -> new UserAccount(id, "erik", null, this.activity))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("profile");
        assertThatThrownBy(() -> new UserAccount(id, "erik", this.profile, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("activity");
    }

    /**
     * Verifies that two accounts are told apart by their identifier rather than by their login name,
     * which is what makes the identifier the one thing a caller may compare.
     */
    @Test
    void accountsAreToldApartByTheirIdentifier() {
        final UUID id = UUID.randomUUID();

        assertThat(new UserAccount(id, "erik", this.profile, this.activity))
                .isEqualTo(new UserAccount(id, "erik", this.profile, this.activity))
                .isNotEqualTo(new UserAccount(UUID.randomUUID(), "erik", this.profile, this.activity));
    }
}
