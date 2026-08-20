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

package de.pfoertner.assessment.sumbooklm.api.v1.source;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
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
 * <h2>When It Was Read</h2>
 * A source carries the moment it was last read, which is absent until it has been. It is what tells a
 * reader how old the material behind an answer is, and it is the reason a page can be read again.
 *
 * <h2>Failure Is a Cause, Not a Message</h2>
 * A source that could not be indexed reports which of a small set of causes stopped it, and the
 * client turns that into a sentence in the language of its user. The text the parser or the HTTP
 * client failed with stays in the log, because it names hosts and file paths.
 *
 * @param id          stable identifier of the source
 * @param notebookId  identifier of the notebook the source belongs to
 * @param displayName name the source is listed under
 * @param kind        way the source entered the notebook
 * @param origin      name of the uploaded file or address of the page
 * @param status      stage the source has reached on its way into the retrieval index
 * @param tokenCount  number of tokens the indexed text was counted as, zero while unknown
 * @param failure     reason the source could not be indexed, {@code NONE} unless it failed
 * @param indexedAt   point in time the source was last read, absent while it never was
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

        @Schema(description = "Reason the source could not be indexed, NONE unless it failed.")
        DocumentFailure failure,

        @Schema(description = "Point in time the source was last read, absent while it never was.")
        Instant indexedAt,

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
                source.failure(),
                source.indexedAt(),
                source.createdAt());
    }
}
