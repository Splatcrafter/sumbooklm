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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules the name of a user is described by.
 *
 * <h2>Descriptive, Not Identifying</h2>
 * Nothing resolves a user by their name, which is why the record accepts names that could never be
 * unique and never trims what it is given. The cases below hold that open on purpose.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class UserProfileTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    UserProfileTest() {
    }

    /**
     * Verifies that both parts of a name are kept exactly as they were given.
     */
    @Test
    void bothPartsAreKept() {
        final UserProfile profile = new UserProfile("Erik", "Pförtner");

        assertThat(profile.firstName()).isEqualTo("Erik");
        assertThat(profile.lastName()).isEqualTo("Pförtner");
    }

    /**
     * Verifies that an empty part of a name is accepted, because a person may go by one name and the
     * record is not the place that decides what a name has to look like.
     */
    @Test
    void anEmptyPartIsAccepted() {
        assertThat(new UserProfile("", "Prince").firstName()).isEmpty();
    }

    /**
     * Verifies that a missing part of a name is refused, because absence and emptiness would
     * otherwise be indistinguishable in the stored payload.
     */
    @Test
    void aMissingPartIsRefused() {
        assertThatThrownBy(() -> new UserProfile(null, "Pfoertner"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("firstName");
        assertThatThrownBy(() -> new UserProfile("Erik", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("lastName");
    }
}
