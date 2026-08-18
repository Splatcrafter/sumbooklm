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
