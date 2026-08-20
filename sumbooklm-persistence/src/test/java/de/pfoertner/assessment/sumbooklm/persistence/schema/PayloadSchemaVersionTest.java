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

package de.pfoertner.assessment.sumbooklm.persistence.schema;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the version numbers stored payloads are stamped with.
 *
 * <h2>Why the Numbers Are Named</h2>
 * Every row carries the number its payload was written under, and the migration reads that number to
 * decide which fixes to run. Lowering a number or reusing one would therefore make existing rows be
 * migrated as something they are not, and no compiler notices, because the number is an integer.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class PayloadSchemaVersionTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    PayloadSchemaVersionTest() {
    }

    /**
     * Verifies the number each released schema is written under.
     */
    @Test
    void everySchemaKeepsItsNumber() {
        assertThat(PayloadSchemaVersion.V1_0_0).isEqualTo(100);
        assertThat(PayloadSchemaVersion.V1_1_0).isEqualTo(110);
    }

    /**
     * Verifies that the schema payloads are written under is the most recent one, and that the
     * numbers grow, because a migration only ever runs forwards.
     */
    @Test
    void theCurrentSchemaIsTheMostRecentOne() {
        assertThat(PayloadSchemaVersion.CURRENT).isEqualTo(PayloadSchemaVersion.V1_1_0);
        assertThat(PayloadSchemaVersion.V1_1_0).isGreaterThan(PayloadSchemaVersion.V1_0_0);
    }

    /**
     * Verifies that the holder cannot be instantiated.
     *
     * @throws NoSuchMethodException if the constructor was removed
     */
    @Test
    void theHolderCannotBeInstantiated() throws NoSuchMethodException {
        final Constructor<PayloadSchemaVersion> constructor =
                PayloadSchemaVersion.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }
}
