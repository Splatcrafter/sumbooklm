package de.pfoertner.assessment.sumbooklm.api.v1.notebook;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.Notebook;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of a notebook.
 *
 * <h2>Topic Icon</h2>
 * The icon is passed through as the string it is stored as and is empty while it has not been
 * derived yet. The client decides what to show in its place, because a placeholder chosen here would
 * be a presentation decision made in the wrong layer.
 *
 * @param id             stable identifier of the notebook
 * @param title          name the user gave the notebook
 * @param pinned         whether the notebook is pinned to the top of the overview
 * @param topicIcon      characters standing for the subject of the notebook, empty while unknown
 * @param createdAt      point in time the notebook was created
 * @param lastActivityAt point in time the notebook was last opened or changed
 * @param sourceCount    number of sources currently belonging to the notebook
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "A notebook of the authenticated account.")
public record NotebookResponse(
        @Schema(description = "Stable identifier of the notebook.")
        UUID id,

        @Schema(description = "Name the user gave the notebook.", example = "Thermodynamics")
        String title,

        @Schema(description = "Whether the notebook is pinned to the top of the overview.")
        boolean pinned,

        @Schema(description = "Characters standing for the subject of the notebook, empty while unknown.")
        String topicIcon,

        @Schema(description = "Point in time the notebook was created.")
        Instant createdAt,

        @Schema(description = "Point in time the notebook was last opened or changed.")
        Instant lastActivityAt,

        @Schema(description = "Number of sources currently belonging to the notebook.")
        long sourceCount) {

    /**
     * Converts a notebook into its transport representation.
     *
     * @param notebook notebook produced by the workspace module
     * @return the notebook as it is returned to a client
     */
    public static NotebookResponse from(final Notebook notebook) {
        return new NotebookResponse(
                notebook.id(),
                notebook.title(),
                notebook.pinned(),
                notebook.topicIcon(),
                notebook.createdAt(),
                notebook.lastActivityAt(),
                notebook.sourceCount());
    }
}
