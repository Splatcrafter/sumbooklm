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

package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.Objects;
import java.util.UUID;

/**
 * One source an answer was allowed to draw on, as it is reported to the client.
 *
 * <h2>Why the Client Is Told</h2>
 * An answer cites passages by number, and a number is only meaningful together with the source it
 * stands for. The list is therefore sent before the first part of the answer, so that a citation can
 * be rendered as the name of a document from the moment it appears in the text.
 *
 * @param number           number the answer cites this source under, starting at one
 * @param sourceDocumentId identifier of the source the passage was taken from
 * @param displayName      name the source is listed under
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record RetrievedSource(int number, UUID sourceDocumentId, String displayName) {

    /**
     * Creates the entry.
     *
     * @param number           number the answer cites this source under
     * @param sourceDocumentId identifier of the source the passage was taken from
     * @param displayName      name the source is listed under
     * @throws NullPointerException     if {@code sourceDocumentId} or {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code number} is smaller than one
     */
    public RetrievedSource {
        Objects.requireNonNull(sourceDocumentId, "sourceDocumentId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (number < 1) {
            throw new IllegalArgumentException("number must be at least one");
        }
    }
}
