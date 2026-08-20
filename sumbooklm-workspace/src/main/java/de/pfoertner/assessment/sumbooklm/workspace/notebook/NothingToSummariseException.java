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

package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.util.UUID;

/**
 * Raised when a summary is requested for a notebook that has no readable source.
 *
 * <h2>Not the Same as an Empty Notebook</h2>
 * The interface knows how many sources a notebook holds and does not offer a summary of none. What
 * reaches this exception is the case it cannot see: sources that exist but could not be read, or ones
 * that are still on their way into the index. Both are answered as a request that cannot be carried
 * out right now rather than as a summary that is empty, because an empty summary would be stored and
 * shown as the description of the notebook.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class NothingToSummariseException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param notebookId identifier of the notebook that holds nothing readable
     */
    public NothingToSummariseException(final UUID notebookId) {
        super("Notebook " + notebookId + " holds no source that has been read");
    }
}
