package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.Objects;
import java.util.UUID;

/**
 * One source an answer was allowed to draw on, as it is reported to the client.
 *
 * <h2>Why the Client Is Told</h2>
 * An answer cites passages by number, and a number is only meaningful together with the source it
 * stands for. The list is therefore sent before the first part of the answer, so that a citation can
 * be rendered as the name of a document from the moment it appears in the text.
 *
 * @param number           number the answer cites this source under, starting at one
 * @param sourceDocumentId identifier of the source the passage was taken from
 * @param displayName      name the source is listed under
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record RetrievedSource(int number, UUID sourceDocumentId, String displayName) {

    /**
     * Creates the entry.
     *
     * @param number           number the answer cites this source under
     * @param sourceDocumentId identifier of the source the passage was taken from
     * @param displayName      name the source is listed under
     * @throws NullPointerException     if {@code sourceDocumentId} or {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code number} is smaller than one
     */
    public RetrievedSource {
        Objects.requireNonNull(sourceDocumentId, "sourceDocumentId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (number < 1) {
            throw new IllegalArgumentException("number must be at least one");
        }
    }
}
