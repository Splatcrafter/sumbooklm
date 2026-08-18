package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.util.Objects;
import java.util.UUID;

/**
 * Announces that a source was stored and is waiting to be indexed.
 *
 * <h2>Why an Event</h2>
 * Indexing must not start before the row that describes the source is visible to other transactions.
 * Publishing the intent and letting the listener run after the commit is what guarantees that; a
 * call made directly from the storing method would race its own transaction and would sometimes find
 * nothing to index.
 *
 * <h2>Identifiers Only</h2>
 * The event carries identifiers rather than the source itself, because everything it describes may
 * have changed by the time the listener runs. The listener reads the current row instead of trusting
 * a copy taken earlier.
 *
 * @param userId   identifier of the account the source belongs to
 * @param sourceId identifier of the stored source
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record SourceAddedEvent(UUID userId, UUID sourceId) {

    /**
     * Creates the event.
     *
     * @param userId   identifier of the account the source belongs to
     * @param sourceId identifier of the stored source
     * @throws NullPointerException if any argument is {@code null}
     */
    public SourceAddedEvent {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }
}
