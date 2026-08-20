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

package de.pfoertner.assessment.sumbooklm.domain.workspace;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the two questions a summary answers.
 *
 * <h2>Written and Current</h2>
 * Whether a summary exists and whether it still describes the sources are separate, and the layers
 * above lead somewhere different for each. The cases below hold the two apart, including the one
 * combination that looks contradictory: no text that is nonetheless marked as out of date.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookSummaryTest {

    /**
     * Identifier of the notebook the summaries under test belong to.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    NotebookSummaryTest() {
    }

    /**
     * Verifies that a summary carrying a text reports itself as written.
     */
    @Test
    void aSummaryWithATextIsWritten() {
        assertThat(new NotebookSummary(this.notebookId, "The sources describe entropy.", false).isWritten())
                .isTrue();
    }

    /**
     * Verifies that a summary without a text reports itself as not written, which is the state every
     * notebook starts in.
     */
    @Test
    void aSummaryWithoutATextIsNotWritten() {
        assertThat(new NotebookSummary(this.notebookId, "", false).isWritten()).isFalse();
    }

    /**
     * Verifies that a text made of whitespace counts as written, because the emptiness the record
     * asks about is the absence of a stored text rather than the absence of prose.
     */
    @Test
    void aTextOfWhitespaceCountsAsWritten() {
        assertThat(new NotebookSummary(this.notebookId, " ", false).isWritten()).isTrue();
    }

    /**
     * Verifies that being out of date is independent of being written, so that the flag cannot make
     * a summary that does not exist look like one that does.
     */
    @Test
    void beingOutOfDateDoesNotMakeASummaryWritten() {
        assertThat(new NotebookSummary(this.notebookId, "", true).isWritten()).isFalse();
        assertThat(new NotebookSummary(this.notebookId, "", true).stale()).isTrue();
    }

    /**
     * Verifies that the notebook and the text are required, so that a summary always names what it
     * describes.
     */
    @Test
    void theNotebookAndTheTextAreRequired() {
        assertThatThrownBy(() -> new NotebookSummary(null, "t", false))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("notebookId");
        assertThatThrownBy(() -> new NotebookSummary(this.notebookId, null, false))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
    }
}
