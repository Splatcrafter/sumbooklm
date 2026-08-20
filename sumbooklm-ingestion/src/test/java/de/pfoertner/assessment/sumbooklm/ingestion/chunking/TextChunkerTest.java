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

package de.pfoertner.assessment.sumbooklm.ingestion.chunking;

import java.util.List;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the step that cuts a text into the pieces a question is answered from.
 *
 * <h2>Why the Sizes Are Checked</h2>
 * Every piece is embedded on its own and several of them are put into one request. A piece that grew
 * past the size the splitter was configured with would therefore not fail here but at a provider,
 * halfway through a deployment, and only for the users whose sources happen to be written without
 * paragraphs. The cases below hold the bound where it can be seen.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class TextChunkerTest {

    /**
     * Largest number of characters one piece may hold, matching the value the splitter is built with.
     */
    private static final int MAX_SEGMENT_CHARS = 1_000;

    /**
     * Splitter under test.
     */
    private final TextChunker chunker = new TextChunker();

    /**
     * Creates the test class.
     */
    TextChunkerTest() {
    }

    /**
     * Verifies that a text nobody could read anything out of produces no pieces at all, rather than
     * one empty piece that would be embedded and stored.
     *
     * @param text text the case is run for
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\n\n", "\t \r\n "})
    void aTextWithoutContentProducesNoPieces(final String text) {
        assertThat(this.chunker.chunk(text)).isEmpty();
    }

    /**
     * Verifies that a short text stays one piece, so that a small source is not scattered over
     * several entries of the index.
     */
    @Test
    void aShortTextStaysOnePiece() {
        final List<TextSegment> segments = this.chunker.chunk("Entropy never decreases.");

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().text()).contains("Entropy never decreases.");
    }

    /**
     * Verifies that a text of many paragraphs is cut into several pieces and that none of them grows
     * past the size the splitter was built with.
     */
    @Test
    void aLongTextIsCutIntoBoundedPieces() {
        final StringBuilder text = new StringBuilder();
        for (int paragraph = 0; paragraph < 40; paragraph += 1) {
            text.append("Paragraph ").append(paragraph).append(": ")
                    .append("Entropy never decreases in an isolated system. ".repeat(4))
                    .append("\n\n");
        }

        final List<TextSegment> segments = this.chunker.chunk(text.toString());

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments).allSatisfy(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(MAX_SEGMENT_CHARS));
    }

    /**
     * Verifies that a single paragraph beyond the size of a piece is still cut, which is the worst
     * case of a page that carries one long run of prose without a blank line in it.
     */
    @Test
    void aSingleOverlongParagraphIsStillCut() {
        final String paragraph = "Entropy never decreases in an isolated system. ".repeat(200);

        final List<TextSegment> segments = this.chunker.chunk(paragraph);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments).allSatisfy(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(MAX_SEGMENT_CHARS));
    }

    /**
     * Verifies that a word longer than a whole piece is still cut rather than dropped, which is what
     * a page of one unbroken run of characters amounts to.
     */
    @Test
    void aWordLongerThanAPieceIsStillCut() {
        final List<TextSegment> segments = this.chunker.chunk("x".repeat(5_000));

        assertThat(segments).isNotEmpty();
        assertThat(segments).allSatisfy(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(MAX_SEGMENT_CHARS));
        assertThat(segments.stream().mapToInt(segment -> segment.text().length()).sum())
                .isGreaterThanOrEqualTo(5_000);
    }

    /**
     * Verifies that the pieces of a text keep the order they were read in, so that a passage cited
     * in an answer stands where the source has it.
     */
    @Test
    void thePiecesKeepTheirOrder() {
        final StringBuilder text = new StringBuilder();
        for (int paragraph = 0; paragraph < 30; paragraph += 1) {
            text.append("Marker").append(paragraph).append(' ')
                    .append("filler ".repeat(60)).append("\n\n");
        }

        final List<TextSegment> segments = this.chunker.chunk(text.toString());
        final String joined = String.join(" ", segments.stream().map(TextSegment::text).toList());

        assertThat(joined.indexOf("Marker0")).isLessThan(joined.indexOf("Marker1"));
        assertThat(joined.indexOf("Marker1")).isLessThan(joined.indexOf("Marker2"));
    }
}
