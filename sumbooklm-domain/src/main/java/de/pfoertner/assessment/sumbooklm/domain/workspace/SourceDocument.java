package de.pfoertner.assessment.sumbooklm.domain.workspace;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One source a notebook grounds its answers in.
 *
 * <h2>Ownership</h2>
 * A source belongs to exactly one notebook and, through it, to exactly one account. Both identifiers
 * are carried on the source rather than only on the notebook, so that every read and every write can
 * name the account without first resolving the notebook.
 *
 * <h2>Origin and Display Name</h2>
 * The origin says where the content came from: the name of the uploaded file, or the address of the
 * retrieved page. The display name is what a list shows, which starts out as the origin and may be
 * replaced by something better once the content has been read, such as the title of a web page.
 *
 * <h2>Token Count</h2>
 * The count is what the embedding model reported for the segments of this source and is therefore
 * zero until indexing has finished. It describes the indexed text, not the uploaded bytes.
 *
 * <h2>Failure</h2>
 * The failure says why a source in {@link DocumentStatus#ERROR} could not be indexed and is
 * {@link DocumentFailure#NONE} in every other stage. It is a cause rather than a message, because it
 * is shown to the user and the text a parser or an HTTP client fails with is not theirs to read.
 *
 * @param id          stable identifier of the source, never {@code null}
 * @param notebookId  identifier of the notebook the source belongs to, never {@code null}
 * @param ownerId     identifier of the account the source belongs to, never {@code null}
 * @param displayName name the source is listed under, never {@code null}
 * @param kind        way the source entered the notebook, never {@code null}
 * @param origin      name of the uploaded file or address of the page, never {@code null}
 * @param status      stage the source has reached on its way into the retrieval index, never
 *                    {@code null}
 * @param tokenCount  number of tokens the indexed text was counted as, zero while unknown
 * @param failure     reason the source could not be indexed, never {@code null}
 * @param createdAt   point in time the source was added to its notebook, never {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record SourceDocument(UUID id,
                             UUID notebookId,
                             UUID ownerId,
                             String displayName,
                             SourceKind kind,
                             String origin,
                             DocumentStatus status,
                             int tokenCount,
                             DocumentFailure failure,
                             Instant createdAt) {

    /**
     * Creates the source.
     *
     * @param id          stable identifier of the source
     * @param notebookId  identifier of the notebook the source belongs to
     * @param ownerId     identifier of the account the source belongs to
     * @param displayName name the source is listed under
     * @param kind        way the source entered the notebook
     * @param origin      name of the uploaded file or address of the page
     * @param status      stage the source has reached on its way into the retrieval index
     * @param tokenCount  number of tokens the indexed text was counted as, zero while unknown
     * @param failure     reason the source could not be indexed
     * @param createdAt   point in time the source was added to its notebook
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code tokenCount} is negative
     */
    public SourceDocument {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(notebookId, "notebookId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(failure, "failure must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must not be negative");
        }
    }
}
