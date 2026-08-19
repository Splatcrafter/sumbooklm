package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a request that asks a question about the sources of a notebook.
 *
 * <h2>Length</h2>
 * The bound keeps a question a question. Everything longer is a document, and a document belongs in
 * the sources of the notebook rather than in the field that asks about them.
 *
 * @param question question to answer from the sources of the notebook
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "A question about the sources of one notebook.")
public record ChatQuestionRequest(
        @Schema(description = "Question to answer from the sources of the notebook.",
                example = "What does the second chapter say about entropy?")
        @NotBlank @Size(max = 4_000)
        String question) {
}
