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

import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Body of a request that changes a notebook.
 *
 * <h2>Partial Update</h2>
 * Both fields are optional and an omitted field leaves the stored value alone, which is what lets
 * renaming and pinning share one endpoint. The pattern below rejects a title that consists of
 * whitespace only; it does not apply to an omitted title, because a validation constraint of this
 * kind passes on a {@code null} value.
 *
 * @param title  name to store, or {@code null} to keep the current one
 * @param pinned pin state to store, or {@code null} to keep the current one
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Fields of a notebook to change. An omitted field keeps its stored value.")
public record NotebookUpdateRequest(
        @Schema(description = "Name to store, omitted to keep the current one.", example = "Thermodynamics")
        @Size(max = 200) @Pattern(regexp = ".*\\S.*")
        @Nullable String title,

        @Schema(description = "Pin state to store, omitted to keep the current one.")
        @Nullable Boolean pinned) {

    /**
     * Converts the request into the command of the workspace module.
     *
     * @return the requested change as a command
     */
    public NotebookUpdateCommand toCommand() {
        return new NotebookUpdateCommand(this.title, this.pinned);
    }
}
