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

package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the stored part of a notebook and the ways it is changed.
 *
 * <h2>Why Each Change Is Stated</h2>
 * The payload is written back as a whole on every change. A method that dropped a field while
 * changing another would therefore not fail: it would silently erase a summary when a notebook is
 * renamed, and nothing would notice until somebody opened it again.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookPayloadTest {

    /**
     * Payload the cases change, carrying a value in every field.
     */
    private final NotebookPayload payload =
            new NotebookPayload("Thermodynamics", true, "@", "About entropy.", "abc123");

    /**
     * Creates the test class.
     */
    NotebookPayloadTest() {
    }

    /**
     * Verifies that renaming a notebook changes the name and nothing else.
     */
    @Test
    void renamingKeepsEverythingElse() {
        final NotebookPayload renamed = this.payload.withTitle("Statistical Mechanics");

        assertThat(renamed.title()).isEqualTo("Statistical Mechanics");
        assertThat(renamed.pinned()).isTrue();
        assertThat(renamed.topicIcon()).isEqualTo("@");
        assertThat(renamed.summary()).isEqualTo("About entropy.");
        assertThat(renamed.summaryFingerprint()).isEqualTo("abc123");
    }

    /**
     * Verifies that pinning a notebook changes the pin and nothing else, including the summary that
     * has nothing to do with it.
     */
    @Test
    void pinningKeepsEverythingElse() {
        final NotebookPayload unpinned = this.payload.withPinned(false);

        assertThat(unpinned.pinned()).isFalse();
        assertThat(unpinned.title()).isEqualTo("Thermodynamics");
        assertThat(unpinned.summary()).isEqualTo("About entropy.");
    }

    /**
     * Verifies that a summary is stored together with the fingerprint of the sources it describes,
     * because one without the other could not be judged as current.
     */
    @Test
    void aSummaryIsStoredWithItsFingerprint() {
        final NotebookPayload written = this.payload.withSummary("About heat.", "def456");

        assertThat(written.summary()).isEqualTo("About heat.");
        assertThat(written.summaryFingerprint()).isEqualTo("def456");
        assertThat(written.title()).isEqualTo("Thermodynamics");
        assertThat(written.pinned()).isTrue();
    }

    /**
     * Verifies that a change leaves the payload it was made from untouched, which is what makes it
     * safe to read one and write another in the same transaction.
     */
    @Test
    void theOriginalIsLeftUntouched() {
        this.payload.withTitle("Something else");
        this.payload.withPinned(false);
        this.payload.withSummary("", "");

        assertThat(this.payload.title()).isEqualTo("Thermodynamics");
        assertThat(this.payload.pinned()).isTrue();
        assertThat(this.payload.summary()).isEqualTo("About entropy.");
    }

    /**
     * Verifies that a notebook that was never summarised is described by empty text rather than by
     * absence, because the codec writes strings and has no third state.
     */
    @Test
    void anUnsummarisedNotebookCarriesEmptyText() {
        final NotebookPayload fresh = new NotebookPayload("Thermodynamics", false, "", "", "");

        assertThat(fresh.summary()).isEmpty();
        assertThat(fresh.summaryFingerprint()).isEmpty();
    }

    /**
     * Verifies that no field may be absent, because every one of them is written into the payload as
     * a string and a missing one could not be encoded.
     */
    @Test
    void noFieldMayBeAbsent() {
        assertThatThrownBy(() -> new NotebookPayload(null, false, "", "", ""))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new NotebookPayload("t", false, null, "", ""))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("topicIcon");
        assertThatThrownBy(() -> new NotebookPayload("t", false, "", null, ""))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("summary");
        assertThatThrownBy(() -> new NotebookPayload("t", false, "", "", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("summaryFingerprint");
    }
}
