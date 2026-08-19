package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.workspace.chat.RetrievedSource;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of one source an answer may cite.
 *
 * <h2>Sent Before the Answer</h2>
 * The list of these is the first event of a stream, so that a citation appearing in the text can be
 * rendered as the name of a document immediately rather than after the answer has finished.
 *
 * @param number           number the answer cites this source under
 * @param sourceDocumentId identifier of the source, so that a client can address it
 * @param displayName      name the source is listed under
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "A source the answer of one question may cite.")
public record ChatSourceResponse(
        @Schema(description = "Number the answer cites this source under.")
        int number,

        @Schema(description = "Identifier of the source.")
        UUID sourceDocumentId,

        @Schema(description = "Name the source is listed under.")
        String displayName) {

    /**
     * Converts a retrieved source into its transport representation.
     *
     * @param source source produced by the workspace module
     * @return the source as it is sent to a client
     */
    public static ChatSourceResponse from(final RetrievedSource source) {
        return new ChatSourceResponse(source.number(), source.sourceDocumentId(), source.displayName());
    }
}
