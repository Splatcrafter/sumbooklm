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

package de.pfoertner.assessment.sumbooklm.api.v1.notebook;

import de.pfoertner.assessment.sumbooklm.domain.workspace.NotebookSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of the summary of a notebook.
 *
 * <h2>Empty Is a State, Not a Missing Field</h2>
 * A notebook that has no summary answers with an empty text rather than with {@code 404}. The
 * resource exists as soon as the notebook does; what is empty is its content, and a client that has
 * to tell those apart would have to treat a status code as a value.
 *
 * @param text  text a model wrote about the sources, empty while none was written
 * @param stale whether the sources have changed since the text was written
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "The summary written about the sources of one notebook.")
public record NotebookSummaryResponse(
        @Schema(description = "Text a model wrote about the sources, empty while none was written.")
        String text,

        @Schema(description = "Whether the sources have changed since the text was written.")
        boolean stale) {

    /**
     * Converts a summary into its transport representation.
     *
     * @param summary summary produced by the workspace module
     * @return the summary as it is returned to a client
     */
    public static NotebookSummaryResponse from(final NotebookSummary summary) {
        return new NotebookSummaryResponse(summary.text(), summary.stale());
    }
}
