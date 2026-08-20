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

import java.nio.charset.StandardCharsets;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises what an uploaded file is read as.
 *
 * <h2>Why the Bad Cases Matter Most</h2>
 * A user uploads whatever they have, and the parser is a large library that answers a damaged file
 * with whatever its format happened to break on. What the application shows for such a file is
 * decided here, and the two answers it may give are far apart: a file that holds no text is one the
 * user can replace, a file that cannot be parsed is one they can convert. Everything else would have
 * to be shown as an internal failure.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class FileTextExtractorTest {

    /**
     * Extractor under test.
     */
    private final FileTextExtractor extractor = new FileTextExtractor();

    /**
     * Creates the test class.
     */
    FileTextExtractorTest() {
    }

    /**
     * Verifies that a plain text file is read as its text, and that a file carries no title of its
     * own, which is what leaves the name the user uploaded it under in place.
     */
    @Test
    void aPlainTextFileIsReadAsItsText() {
        final ExtractedContent content = this.extractor.extract(
                "Entropy never decreases.".getBytes(StandardCharsets.UTF_8), "notes.txt");

        assertThat(content.text()).contains("Entropy never decreases.");
        assertThat(content.title()).isEmpty();
    }

    /**
     * Verifies that the whitespace a file is framed by is removed, so that the stored text begins
     * where the content does.
     */
    @Test
    void theFramingWhitespaceIsRemoved() {
        final ExtractedContent content = this.extractor.extract(
                "\n\n  Entropy never decreases.  \n\n".getBytes(StandardCharsets.UTF_8), "notes.txt");

        assertThat(content.text()).startsWith("Entropy").endsWith(".");
    }

    /**
     * Verifies that characters outside the ASCII range survive being read, because a source in any
     * other language would otherwise be indexed as something else.
     */
    @Test
    void charactersOutsideAsciiSurvive() {
        final ExtractedContent content = this.extractor.extract(
                "Die Entropie nimmt nie ab. Größe: 42 µm".getBytes(StandardCharsets.UTF_8), "notiz.txt");

        assertThat(content.text()).contains("Größe").contains("µm");
    }

    /**
     * Verifies that a file holding no bytes is reported as empty rather than as damaged, because the
     * user can answer the first and not the second.
     */
    @Test
    void aFileWithoutBytesIsReportedAsEmpty() {
        assertThatThrownBy(() -> this.extractor.extract(new byte[0], "empty.txt"))
                .isInstanceOf(TextExtractionException.class)
                .hasMessageContaining("empty.txt")
                .extracting(failure -> ((TextExtractionException) failure).failure())
                .isEqualTo(DocumentFailure.EMPTY);
    }

    /**
     * Verifies that a file holding nothing but whitespace is reported as empty as well, which is
     * what an exported document of empty pages amounts to.
     */
    @Test
    void aFileOfWhitespaceIsReportedAsEmpty() {
        assertThatThrownBy(() -> this.extractor.extract(
                "   \n\t\r\n   ".getBytes(StandardCharsets.UTF_8), "blank.txt"))
                .isInstanceOf(TextExtractionException.class)
                .extracting(failure -> ((TextExtractionException) failure).failure())
                .isEqualTo(DocumentFailure.EMPTY);
    }

    /**
     * Verifies that a file which claims a format it does not hold is reported as unreadable, which
     * is the state a truncated or damaged upload arrives in.
     */
    @Test
    void aDamagedFileIsReportedAsUnreadable() {
        final byte[] brokenPdf = "%PDF-1.7\nthis is not actually a document".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> this.extractor.extract(brokenPdf, "broken.pdf"))
                .isInstanceOf(TextExtractionException.class)
                .hasMessageContaining("broken.pdf")
                .extracting(failure -> ((TextExtractionException) failure).failure())
                .isEqualTo(DocumentFailure.UNREADABLE);
    }

    /**
     * Verifies that a large file is read whole rather than to some bound of the parser, because the
     * text is what every later answer is retrieved from.
     */
    @Test
    void aLargeFileIsReadWhole() {
        final String text = "Entropy never decreases in an isolated system.\n".repeat(20_000);

        final ExtractedContent content =
                this.extractor.extract(text.getBytes(StandardCharsets.UTF_8), "large.txt");

        assertThat(content.text().length()).isGreaterThan(900_000);
    }
}
