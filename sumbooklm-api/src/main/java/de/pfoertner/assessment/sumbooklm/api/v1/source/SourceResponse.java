package de.pfoertner.assessment.sumbooklm.api.v1.source;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentStatus;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceDocument;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of a source document.
 *
 * <h2>Status Is Part of the Representation</h2>
 * A client polls this representation while a source is being indexed, so the stage is returned like
 * any other field rather than through a separate endpoint. The token count is zero until the stage
 * reaches {@code READY}, which is what tells the client that the value is not merely small.
 *
 * @param id          stable identifier of the source
 * @param notebookId  identifier of the notebook the source belongs to
 * @param displayName name the source is listed under
 * @param kind        way the source entered the notebook
 * @param origin      name of the uploaded file or address of the page
 * @param status      stage the source has reached on its way into the retrieval index
 * @param tokenCount  number of tokens the indexed text was counted as, zero while unknown
 * @param createdAt   point in time the source was added to its notebook
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "A source of one notebook of the authenticated account.")
public record SourceResponse(
        @Schema(description = "Stable identifier of the source.")
        UUID id,

        @Schema(description = "Identifier of the notebook the source belongs to.")
        UUID notebookId,

        @Schema(description = "Name the source is listed under.", example = "Thermodynamics.pdf")
        String displayName,

        @Schema(description = "Way the source entered the notebook.")
        SourceKind kind,

        @Schema(description = "Name of the uploaded file or address of the page.")
        String origin,

        @Schema(description = "Stage the source has reached on its way into the retrieval index.")
        DocumentStatus status,

        @Schema(description = "Number of tokens the indexed text was counted as, zero while unknown.")
        int tokenCount,

        @Schema(description = "Point in time the source was added to its notebook.")
        Instant createdAt) {

    /**
     * Converts a source into its transport representation.
     *
     * @param source source produced by the workspace module
     * @return the source as it is returned to a client
     */
    public static SourceResponse from(final SourceDocument source) {
        return new SourceResponse(
                source.id(),
                source.notebookId(),
                source.displayName(),
                source.kind(),
                source.origin(),
                source.status(),
                source.tokenCount(),
                source.createdAt());
    }
}
