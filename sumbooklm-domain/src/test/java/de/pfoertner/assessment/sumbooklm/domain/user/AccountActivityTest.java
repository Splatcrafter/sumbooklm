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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules the audit metadata of an account is described by.
 *
 * <h2>One Login, Not a History</h2>
 * The record describes the most recent login rather than a series of them, so the registration data
 * and the login data may name the same moment and the same address. The first case below is that
 * state, which is what a freshly registered account carries.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class AccountActivityTest {

    /**
     * Point in time an account was registered at.
     */
    private static final Instant REGISTERED = Instant.parse("2026-01-02T03:04:05Z");

    /**
     * Point in time of the most recent login.
     */
    private static final Instant LOGGED_IN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    AccountActivityTest() {
    }

    /**
     * Verifies that a freshly registered account may report the same moment and address twice.
     */
    @Test
    void aFreshAccountRepeatsItsRegistration() {
        final AccountActivity activity =
                new AccountActivity(REGISTERED, "203.0.113.7", REGISTERED, "203.0.113.7");

        assertThat(activity.registeredAt()).isEqualTo(activity.lastLoginAt());
        assertThat(activity.registrationIpAddress()).isEqualTo(activity.lastLoginIpAddress());
    }

    /**
     * Verifies that the login data may differ from the registration data, which is what happens once
     * an account is used from somewhere else.
     */
    @Test
    void aLaterLoginIsRecordedApart() {
        final AccountActivity activity =
                new AccountActivity(REGISTERED, "203.0.113.7", LOGGED_IN, "198.51.100.4");

        assertThat(activity.lastLoginAt()).isAfter(activity.registeredAt());
        assertThat(activity.lastLoginIpAddress()).isEqualTo("198.51.100.4");
    }

    /**
     * Verifies that every part of the metadata is required, so that no account can be described
     * without saying when and from where it was used.
     */
    @Test
    void everyPartIsRequired() {
        assertThatThrownBy(() -> new AccountActivity(null, "a", LOGGED_IN, "b"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("registeredAt");
        assertThatThrownBy(() -> new AccountActivity(REGISTERED, null, LOGGED_IN, "b"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("registrationIpAddress");
        assertThatThrownBy(() -> new AccountActivity(REGISTERED, "a", null, "b"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("lastLoginAt");
        assertThatThrownBy(() -> new AccountActivity(REGISTERED, "a", LOGGED_IN, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("lastLoginIpAddress");
    }
}
