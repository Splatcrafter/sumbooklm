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

import java.util.Objects;
import java.util.UUID;

/**
 * What a model wrote about the sources of one notebook.
 *
 * <h2>Not Part of the Notebook</h2>
 * A summary is read on its own rather than with the notebook it belongs to. Every list of notebooks
 * would otherwise carry a paragraph per entry that no list displays, and the one screen that does
 * display it is also the only one that can have it written.
 *
 * <h2>Written and Current Are Two Questions</h2>
 * An empty text means that nothing has been written yet, which is the state every notebook starts in.
 * A text that is present may still describe sources the notebook no longer holds, and that is what
 * {@code stale} says. The distinction matters because the two lead somewhere different: the first is
 * a summary to write, the second is one to write again, and both are requests to a model that only
 * happen when there is one to ask.
 *
 * @param notebookId identifier of the notebook the summary belongs to, never {@code null}
 * @param text       text a model wrote about the sources, empty while none was written
 * @param stale      whether the sources have changed since the text was written
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record NotebookSummary(UUID notebookId, String text, boolean stale) {

    /**
     * Creates the summary.
     *
     * @param notebookId identifier of the notebook the summary belongs to
     * @param text       text a model wrote about the sources, empty while none was written
     * @param stale      whether the sources have changed since the text was written
     * @throws NullPointerException if any reference argument is {@code null}
     */
    public NotebookSummary {
        Objects.requireNonNull(notebookId, "notebookId must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }

    /**
     * Reports whether a summary has been written at all.
     *
     * @return {@code true} if the notebook carries a text, {@code false} while it carries none
     */
    public boolean isWritten() {
        return !this.text.isEmpty();
    }
}
