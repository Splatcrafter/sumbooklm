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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the instructions a question is asked under.
 *
 * <h2>Why It Is Tested Alone</h2>
 * The instructions are the only thing keeping an answer inside the sources of one notebook, and they
 * are a string. What can be stated about them without a model is what they carry: every passage that
 * was retrieved, under the number the answer is required to cite it by. A passage that is dropped or
 * numbered differently here produces an answer citing a source the reader cannot open.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class GroundedPromptTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    GroundedPromptTest() {
    }

    /**
     * Verifies that every passage appears under its own number, together with the name of the source
     * it was taken from.
     */
    @Test
    void everyPassageIsCarriedUnderItsNumber() {
        final String prompt = GroundedPrompt.of(List.of(
                new ContextPassage(1, "Thermodynamics.pdf", "Entropy never decreases."),
                new ContextPassage(2, "Notes.txt", "Heat flows from hot to cold.")));

        assertThat(prompt)
                .contains("[1] Thermodynamics.pdf", "Entropy never decreases.")
                .contains("[2] Notes.txt", "Heat flows from hot to cold.");
        assertThat(prompt.indexOf("[1]")).isLessThan(prompt.indexOf("[2]"));
    }

    /**
     * Verifies that the rules the answer is bound by are stated before the material, so that nothing
     * a source contains can be read as an instruction that came first.
     */
    @Test
    void theRulesAreStatedBeforeTheMaterial() {
        final String prompt = GroundedPrompt.of(List.of(
                new ContextPassage(1, "Thermodynamics.pdf", "Entropy never decreases.")));

        assertThat(prompt).contains("[n](#source-n)");
        assertThat(prompt.indexOf("Rules:")).isLessThan(prompt.indexOf("Sources:"));
        assertThat(prompt.indexOf("Sources:")).isLessThan(prompt.indexOf("Entropy never decreases."));
    }

    /**
     * Verifies that two passages of one source both appear, because a source is cited once but may
     * be quoted from more than once.
     */
    @Test
    void twoPassagesOfOneSourceBothAppear() {
        final String prompt = GroundedPrompt.of(List.of(
                new ContextPassage(1, "Thermodynamics.pdf", "First passage."),
                new ContextPassage(1, "Thermodynamics.pdf", "Second passage.")));

        assertThat(prompt).contains("First passage.").contains("Second passage.");
    }

    /**
     * Verifies that a question without passages is refused rather than asked, because a model given
     * no material and told to use nothing else has nothing left to do but invent.
     */
    @Test
    void aQuestionWithoutPassagesIsRefused() {
        assertThatThrownBy(() -> GroundedPrompt.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without passages");
    }

    /**
     * Verifies that a passage holding no text still appears under its number, so that the numbering
     * of the passages after it is not shifted by it.
     */
    @Test
    void anEmptyPassageStillHoldsItsNumber() {
        final String prompt = GroundedPrompt.of(List.of(
                new ContextPassage(1, "Empty.txt", ""),
                new ContextPassage(2, "Notes.txt", "Heat flows from hot to cold.")));

        assertThat(prompt).contains("[1] Empty.txt").contains("[2] Notes.txt");
    }

    /**
     * Verifies that the class cannot be instantiated, which is what keeps a holder of static
     * behaviour from being handed around as if it were a component.
     *
     * @throws NoSuchMethodException if the constructor was removed
     */
    @Test
    void theClassCannotBeInstantiated() throws NoSuchMethodException {
        final Constructor<GroundedPrompt> constructor = GroundedPrompt.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }
}
