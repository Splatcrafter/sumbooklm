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

package de.pfoertner.assessment.sumbooklm.persistence.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the stored part of an account.
 *
 * <h2>Why It Holds No Secret</h2>
 * The payload carries the name and the two addresses an account was used from. The password hash is
 * a column of its own, and the case below states what the payload does hold so that a field added
 * to it later has to be answered here.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class UserAccountPayloadTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    UserAccountPayloadTest() {
    }

    /**
     * Verifies that the payload carries the name of the user and the two addresses.
     */
    @Test
    void theNameAndTheAddressesAreCarried() {
        final UserAccountPayload payload =
                new UserAccountPayload("Erik", "Pfoertner", "203.0.113.7", "198.51.100.4");

        assertThat(payload.firstName()).isEqualTo("Erik");
        assertThat(payload.lastName()).isEqualTo("Pfoertner");
        assertThat(payload.registrationIpAddress()).isEqualTo("203.0.113.7");
        assertThat(payload.lastLoginIpAddress()).isEqualTo("198.51.100.4");
    }

    /**
     * Verifies that an address which could not be determined may be stored as a word rather than as
     * nothing, because the field is written as a string and has no absent state.
     */
    @Test
    void anUnknownAddressIsStoredAsText() {
        assertThat(new UserAccountPayload("Erik", "Pfoertner", "unknown", "unknown")
                .registrationIpAddress()).isEqualTo("unknown");
    }

    /**
     * Verifies that no field may be absent.
     */
    @Test
    void noFieldMayBeAbsent() {
        assertThatThrownBy(() -> new UserAccountPayload(null, "l", "r", "i"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("firstName");
        assertThatThrownBy(() -> new UserAccountPayload("f", null, "r", "i"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("lastName");
        assertThatThrownBy(() -> new UserAccountPayload("f", "l", null, "i"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("registrationIpAddress");
        assertThatThrownBy(() -> new UserAccountPayload("f", "l", "r", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("lastLoginIpAddress");
    }
}
