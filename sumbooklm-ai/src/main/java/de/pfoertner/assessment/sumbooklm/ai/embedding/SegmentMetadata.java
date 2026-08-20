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

package de.pfoertner.assessment.sumbooklm.ai.embedding;

/**
 * Metadata keys every stored segment carries.
 *
 * <h2>Purpose</h2>
 * The keys are what separates the notebooks inside a shared vector store. A write that used another
 * spelling than a read would produce segments no filter matches, and, worse, a filter that spelled a
 * key wrong would match nothing rather than fail, so both sides name the same constant.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class SegmentMetadata {

    /**
     * Identifier of the notebook a segment belongs to.
     */
    public static final String NOTEBOOK_ID = "notebookId";

    /**
     * Identifier of the source document a segment was extracted from.
     */
    public static final String SOURCE_DOCUMENT_ID = "sourceDocumentId";

    /**
     * Prevents instantiation of this constant holder.
     */
    private SegmentMetadata() {
        throw new AssertionError("SegmentMetadata is a constant holder and must not be instantiated");
    }
}
