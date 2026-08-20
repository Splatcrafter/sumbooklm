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

package de.pfoertner.assessment.sumbooklm.ai.summary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules one piece of summary material is described by.
 *
 * <h2>Why an Empty Text Is Allowed</h2>
 * The budget that shares a request out over the sources may reduce a source to nothing, and the
 * record is what it builds the result from. Refusing an empty text here would turn that reduction
 * into a failure of the whole summary.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SourceExcerptTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SourceExcerptTest() {
    }

    /**
     * Verifies that the name and the text are kept as they were given.
     */
    @Test
    void theNameAndTheTextAreKept() {
        final SourceExcerpt excerpt = new SourceExcerpt("Thermodynamics.pdf", "Entropy never decreases.");

        assertThat(excerpt.displayName()).isEqualTo("Thermodynamics.pdf");
        assertThat(excerpt.text()).isEqualTo("Entropy never decreases.");
    }

    /**
     * Verifies that an excerpt reduced to nothing is accepted.
     */
    @Test
    void anExcerptWithoutTextIsAccepted() {
        assertThat(new SourceExcerpt("Thermodynamics.pdf", "").text()).isEmpty();
    }

    /**
     * Verifies that neither part may be absent, because both are written into the instructions a
     * model is given.
     */
    @Test
    void neitherPartMayBeAbsent() {
        assertThatThrownBy(() -> new SourceExcerpt(null, "t"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("displayName");
        assertThatThrownBy(() -> new SourceExcerpt("n", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
    }
}
