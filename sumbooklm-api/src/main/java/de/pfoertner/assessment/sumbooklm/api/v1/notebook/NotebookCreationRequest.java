package de.pfoertner.assessment.sumbooklm.api.v1.notebook;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a request that creates a notebook.
 *
 * @param title name the notebook is created under
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Data required to create a notebook.")
public record NotebookCreationRequest(
        @Schema(description = "Name the notebook is created under.", example = "Thermodynamics")
        @NotBlank @Size(max = 200)
        String title) {
}
