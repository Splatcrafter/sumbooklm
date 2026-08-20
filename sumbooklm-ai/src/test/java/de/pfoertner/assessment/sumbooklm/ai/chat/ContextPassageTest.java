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

package de.pfoertner.assessment.sumbooklm.ai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules one retrieved passage is described by.
 *
 * <h2>Why the Number Is Checked</h2>
 * The number is what an answer cites and what the reader clicks. A passage numbered zero would be
 * cited as a source the client cannot resolve, and since the numbers are handed out by a loop, the
 * refusal here is what turns an off by one into a failure at the point it was made.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ContextPassageTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ContextPassageTest() {
    }

    /**
     * Verifies that the first number a passage may carry is accepted, which is the boundary the
     * refusal below sits next to.
     */
    @Test
    void theFirstNumberIsAccepted() {
        final ContextPassage passage = new ContextPassage(1, "Notes.txt", "Heat flows.");

        assertThat(passage.number()).isEqualTo(1);
        assertThat(passage.displayName()).isEqualTo("Notes.txt");
        assertThat(passage.text()).isEqualTo("Heat flows.");
    }

    /**
     * Verifies that a passage numbered below one is refused, whether the number is zero or negative.
     *
     * @param number number the case is run for
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void aPassageBelowTheFirstNumberIsRefused(final int number) {
        assertThatThrownBy(() -> new ContextPassage(number, "Notes.txt", "Heat flows."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
    }

    /**
     * Verifies that a passage without a name or without a text is refused, because both are written
     * into the instructions a model is given.
     */
    @Test
    void aPassageWithoutNameOrTextIsRefused() {
        assertThatThrownBy(() -> new ContextPassage(1, null, "Heat flows."))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("displayName");
        assertThatThrownBy(() -> new ContextPassage(1, "Notes.txt", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
    }

    /**
     * Verifies that an empty text is accepted, because a segment may be reduced to nothing by the
     * splitter and dropping it here would renumber the passages after it.
     */
    @Test
    void anEmptyTextIsAccepted() {
        assertThatCode(() -> new ContextPassage(1, "Notes.txt", "")).doesNotThrowAnyException();
    }
}
