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

/**
 * Stage a source document has reached on its way into the retrieval index.
 *
 * <h2>Progression</h2>
 * A document enters as {@link #UPLOADED}, becomes {@link #INDEXING} while it is parsed, split and
 * embedded, and ends in {@link #READY} or {@link #ERROR}. Only a document in {@link #READY} may be
 * retrieved from, which is what keeps a partially indexed document out of an answer.
 *
 * <h2>Persistence</h2>
 * The constants are persisted by name rather than by ordinal, so that the order of the declarations
 * below carries no meaning for stored data.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum DocumentStatus {

    /**
     * The document is stored but nothing has been extracted from it yet.
     */
    UPLOADED,

    /**
     * The document is being parsed, split into chunks and embedded.
     */
    INDEXING,

    /**
     * The document is indexed and can be retrieved from.
     */
    READY,

    /**
     * Processing the document failed and it cannot be retrieved from.
     */
    ERROR
}
