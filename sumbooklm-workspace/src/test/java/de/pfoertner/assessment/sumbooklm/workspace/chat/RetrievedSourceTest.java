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

package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises what one citable source of an answer is described by.
 *
 * <h2>Why the Number Is Checked</h2>
 * The number is what the answer writes into its citations and what the client resolves back to a
 * source. Numbering starts at one because that is what the instructions tell the model to cite, so a
 * source numbered zero would be a citation nothing can be opened from.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class RetrievedSourceTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    RetrievedSourceTest() {
    }

    /**
     * Verifies that a source keeps the number, the identity and the name it was described with.
     */
    @Test
    void theNumberTheIdentityAndTheNameAreKept() {
        final UUID sourceId = UUID.randomUUID();

        final RetrievedSource source = new RetrievedSource(1, sourceId, "Thermodynamics.pdf");

        assertThat(source.number()).isEqualTo(1);
        assertThat(source.sourceDocumentId()).isEqualTo(sourceId);
        assertThat(source.displayName()).isEqualTo("Thermodynamics.pdf");
    }

    /**
     * Verifies that a source numbered below one is refused, because such a number could not be
     * cited.
     *
     * @param number number the case is run for
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aSourceBelowTheFirstNumberIsRefused(final int number) {
        assertThatThrownBy(() -> new RetrievedSource(number, UUID.randomUUID(), "Name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
    }

    /**
     * Verifies that a source without an identity or without a name is refused, because the client
     * needs both to turn a citation into something a reader can open.
     */
    @Test
    void aSourceWithoutIdentityOrNameIsRefused() {
        assertThatThrownBy(() -> new RetrievedSource(1, null, "Name"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("sourceDocumentId");
        assertThatThrownBy(() -> new RetrievedSource(1, UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("displayName");
    }
}
