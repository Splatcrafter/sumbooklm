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

import java.util.Objects;

/**
 * Plain text read out of a source, together with the name the source called itself.
 *
 * <h2>Title</h2>
 * The title is what the content itself claims to be called, such as the title element of a web page.
 * It is empty whenever the format carries no such name, and a caller that has a better name already,
 * as an upload does in its file name, is free to ignore it.
 *
 * @param title title the content carries, empty when it carries none
 * @param text  extracted text with its paragraph boundaries preserved
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ExtractedContent(String title, String text) {

    /**
     * Creates the result.
     *
     * @param title title the content carries, empty when it carries none
     * @param text  extracted text with its paragraph boundaries preserved
     * @throws NullPointerException if any argument is {@code null}
     */
    public ExtractedContent {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
