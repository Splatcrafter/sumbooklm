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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the instructions a summary is written under.
 *
 * <h2>What Is Worth Asserting</h2>
 * The wording of the rules is a matter of taste and is not asserted here. What is asserted is what
 * the rest of the application depends on: that the material reaches the model with the names of its
 * sources, that a named language becomes a rule in a form a model can act on, and that a request
 * without material is refused rather than sent.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookSummaryPromptTest {

    /**
     * Sources every case is built from.
     */
    private static final List<SourceExcerpt> SOURCES = List.of(
            new SourceExcerpt("thermodynamics.txt", "The second law introduces entropy."),
            new SourceExcerpt("baking.txt", "A starter is a culture of wild yeast."));

    /**
     * Creates the test class.
     */
    NotebookSummaryPromptTest() {
    }

    /**
     * Verifies that every source reaches the model with its name and its text.
     */
    @Test
    void theSourcesAreNamedAndCarried() {
        final String prompt = NotebookSummaryPrompt.of(SOURCES, "");

        assertThat(prompt)
                .contains("thermodynamics.txt")
                .contains("The second law introduces entropy.")
                .contains("baking.txt")
                .contains("A starter is a culture of wild yeast.");
    }

    /**
     * Verifies that a language tag becomes a rule naming the language rather than the tag.
     */
    @Test
    void aNamedLanguageBecomesARule() {
        assertThat(NotebookSummaryPrompt.of(SOURCES, "ja")).contains("Write the summary in Japanese.");
        assertThat(NotebookSummaryPrompt.of(SOURCES, "de-DE")).contains("Write the summary in German.");
    }

    /**
     * Verifies that a tag naming no language leaves the rule out, so that the model falls back to the
     * language of the sources instead of to a guess.
     */
    @Test
    void anUnknownLanguageIsLeftOut() {
        assertThat(NotebookSummaryPrompt.of(SOURCES, "")).doesNotContain("Write the summary in");
        assertThat(NotebookSummaryPrompt.of(SOURCES, "qq")).doesNotContain("Write the summary in");
        assertThat(NotebookSummaryPrompt.of(SOURCES, null)).doesNotContain("Write the summary in");
    }

    /**
     * Verifies that a summary of nothing is refused here rather than asked of a model.
     */
    @Test
    void aSummaryWithoutSourcesIsRefused() {
        assertThatThrownBy(() -> NotebookSummaryPrompt.of(List.of(), "en"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
