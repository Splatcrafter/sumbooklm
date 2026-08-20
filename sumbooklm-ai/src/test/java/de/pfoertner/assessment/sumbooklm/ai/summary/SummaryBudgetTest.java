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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the rule that decides how much of each source goes into one summary.
 *
 * <h2>What Is Worth Asserting</h2>
 * The rule shares a budget out, so the cases that matter are the ones where the shares differ: a set
 * that fits whole, a set of equals that has to be cut, and a set in which one short source keeps its
 * whole text while the long ones are cut around it. Everything else follows from those.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SummaryBudgetTest {

    /**
     * Creates the test class.
     */
    SummaryBudgetTest() {
    }

    /**
     * Verifies that a set of sources small enough to be sent whole is left alone.
     */
    @Test
    void aSmallSetIsSentWhole() {
        final List<SourceExcerpt> sources = List.of(
                new SourceExcerpt("first.txt", "The second law introduces entropy."),
                new SourceExcerpt("second.txt", "A starter is a culture of wild yeast."));

        assertThat(SummaryBudget.fit(sources)).isEqualTo(sources);
    }

    /**
     * Verifies that every source of a set that is too large is still represented, and that none of
     * them is dropped for the sake of the others.
     */
    @Test
    void everySourceOfALargeSetIsStillRepresented() {
        final List<SourceExcerpt> sources = new ArrayList<>();
        for (int source = 0; source < 10; source += 1) {
            sources.add(new SourceExcerpt("source-" + source + ".txt", "x".repeat(30_000)));
        }

        final List<SourceExcerpt> fitted = SummaryBudget.fit(sources);

        assertThat(fitted).hasSameSizeAs(sources);
        assertThat(fitted).allSatisfy(source -> assertThat(source.text()).isNotEmpty());
        assertThat(fitted.stream().mapToInt(source -> source.text().length()).sum())
                .isLessThanOrEqualTo(20_000);
        assertThat(fitted.getFirst().displayName()).isEqualTo("source-0.txt");
    }

    /**
     * Verifies that a source which needs less than its share keeps its whole text, and that what it
     * did not need is given to the source that did.
     */
    @Test
    void aShortSourceKeepsItsTextAndHandsTheRestOn() {
        final String shortText = "Entropy never decreases in an isolated system.";
        final List<SourceExcerpt> sources = List.of(
                new SourceExcerpt("short.txt", shortText),
                new SourceExcerpt("long.txt", "y".repeat(100_000)));

        final List<SourceExcerpt> fitted = SummaryBudget.fit(sources);

        assertThat(fitted.getFirst().text()).isEqualTo(shortText);
        assertThat(fitted.getLast().text())
                .describedAs("the long source is given everything the short one did not need")
                .hasSizeGreaterThan(19_000);
        assertThat(fitted.getLast().text()).endsWith("[...]");
    }

    /**
     * Verifies that the sources come back in the order they were given in, because that is the order
     * a notebook lists them in and the order the instructions name them in.
     */
    @Test
    void theOrderOfTheSourcesIsKept() {
        final List<SourceExcerpt> sources = List.of(
                new SourceExcerpt("long.txt", "y".repeat(40_000)),
                new SourceExcerpt("short.txt", "A short note."),
                new SourceExcerpt("middle.txt", "z".repeat(5_000)));

        assertThat(SummaryBudget.fit(sources).stream().map(SourceExcerpt::displayName))
                .containsExactly("long.txt", "short.txt", "middle.txt");
    }
}
