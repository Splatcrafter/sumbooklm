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

package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.io.Serial;
import java.util.UUID;

/**
 * Raised when a notebook already holds a source with the same content.
 *
 * <h2>Scope of the Comparison</h2>
 * The comparison is made within one notebook. The same document may be added to two notebooks,
 * because a notebook is a workspace rather than a library, and the same document can legitimately
 * be part of two pieces of work.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class DuplicateSourceException extends RuntimeException {

    /**
     * Serialization version of the class.
     */
    @Serial
    private static final long serialVersionUID = -3311095642019283440L;

    /**
     * Creates the failure.
     *
     * @param notebookId identifier of the notebook that already holds the content
     */
    public DuplicateSourceException(final UUID notebookId) {
        super("Notebook " + notebookId + " already holds a source with this content");
    }
}
