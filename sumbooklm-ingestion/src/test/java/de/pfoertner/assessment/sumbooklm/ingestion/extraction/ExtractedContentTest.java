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

package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules read content is described by.
 *
 * <h2>Why the Title May Be Empty</h2>
 * A file has no title, only a name the user uploaded it under, and the extractor of files therefore
 * returns none. The emptiness is what the pipeline reads as "keep the name the source already has",
 * so it is a value rather than a gap.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ExtractedContentTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ExtractedContentTest() {
    }

    /**
     * Verifies that content without a title is accepted, which is what every read file produces.
     */
    @Test
    void contentWithoutATitleIsAccepted() {
        final ExtractedContent content = new ExtractedContent("", "Entropy never decreases.");

        assertThat(content.title()).isEmpty();
        assertThat(content.text()).isEqualTo("Entropy never decreases.");
    }

    /**
     * Verifies that neither part may be absent, because the pipeline decides the name of a source
     * from the title and stores the text.
     */
    @Test
    void neitherPartMayBeAbsent() {
        assertThatThrownBy(() -> new ExtractedContent(null, "t"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new ExtractedContent("t", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
    }
}
