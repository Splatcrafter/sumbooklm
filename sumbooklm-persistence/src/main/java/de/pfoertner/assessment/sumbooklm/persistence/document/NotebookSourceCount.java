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

package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.UUID;

/**
 * Number of sources one notebook holds.
 *
 * <h2>Purpose</h2>
 * The projection is the result shape of the grouped count query of
 * {@link SourceDocumentRepository#countPerNotebook(UUID)}. It exists so that the query can return
 * two values per row without materialising the source rows themselves.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface NotebookSourceCount {

    /**
     * Returns the notebook the count belongs to.
     *
     * @return identifier of the notebook
     */
    UUID getNotebookId();

    /**
     * Returns the number of sources the notebook holds.
     *
     * @return number of sources, always greater than zero
     */
    long getSourceCount();
}
