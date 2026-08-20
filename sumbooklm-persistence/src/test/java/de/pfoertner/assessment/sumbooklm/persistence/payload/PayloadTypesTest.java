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

package de.pfoertner.assessment.sumbooklm.persistence.payload;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import de.splatgames.aether.datafixers.api.TypeReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the names the stored payloads are registered and migrated under.
 *
 * <h2>Why the Names Are Fixed</h2>
 * A type name connects a stored row to the codec that reads it and to the fixes that migrate it.
 * Renaming one leaves the rows behind: they are still written, but nothing claims them any more, and
 * the failure shows up as a payload that cannot be decoded rather than as a build that broke.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class PayloadTypesTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    PayloadTypesTest() {
    }

    /**
     * Verifies the name each payload is registered under.
     */
    @Test
    void everyPayloadKeepsItsName() {
        assertThat(PayloadTypes.USER_ACCOUNT.getId()).isEqualTo("user_account");
        assertThat(PayloadTypes.NOTEBOOK.getId()).isEqualTo("notebook");
        assertThat(PayloadTypes.SOURCE_DOCUMENT.getId()).isEqualTo("source_document");
        assertThat(PayloadTypes.CHAT_SESSION.getId()).isEqualTo("chat_session");
    }

    /**
     * Verifies that the four names differ, because two payloads sharing one would be decoded with
     * the codec of the other.
     */
    @Test
    void theNamesDiffer() {
        final List<TypeReference> types = List.of(PayloadTypes.USER_ACCOUNT, PayloadTypes.NOTEBOOK,
                PayloadTypes.SOURCE_DOCUMENT, PayloadTypes.CHAT_SESSION);

        assertThat(types).extracting(TypeReference::getId).doesNotHaveDuplicates();
    }

    /**
     * Verifies that the holder cannot be instantiated.
     *
     * @throws NoSuchMethodException if the constructor was removed
     */
    @Test
    void theHolderCannotBeInstantiated() throws NoSuchMethodException {
        final Constructor<PayloadTypes> constructor = PayloadTypes.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }
}
