package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.util.Objects;
import java.util.UUID;

/**
 * Announces that a source was deleted and its segments are no longer wanted.
 *
 * <h2>Why an Event</h2>
 * The retrieval index has no transaction of its own, so a removal performed while the deleting
 * transaction is still open is a change that a rollback cannot take back. Announcing the deletion and
 * removing the segments after the commit means the index is only ever asked to forget something that
 * is actually gone.
 *
 * @param sourceId identifier of the deleted source
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record SourceRemovedEvent(UUID sourceId) {

    /**
     * Creates the event.
     *
     * @param sourceId identifier of the deleted source
     * @throws NullPointerException if {@code sourceId} is {@code null}
     */
    public SourceRemovedEvent {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }
}
