package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.util.Objects;
import java.util.UUID;

/**
 * Announces that a notebook was deleted and the segments of its sources are no longer wanted.
 *
 * <h2>Why an Event</h2>
 * As with a single source, the retrieval index has no transaction of its own. Announcing the deletion
 * and removing the segments after the commit is what keeps the index from forgetting something that a
 * rollback would have kept.
 *
 * <h2>One Event for Every Source</h2>
 * A notebook is deleted without its sources being read, so there is nothing to announce them one by
 * one with. The listener removes by notebook instead, which the index supports for exactly this
 * reason.
 *
 * @param notebookId identifier of the deleted notebook
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record NotebookRemovedEvent(UUID notebookId) {

    /**
     * Creates the event.
     *
     * @param notebookId identifier of the deleted notebook
     * @throws NullPointerException if {@code notebookId} is {@code null}
     */
    public NotebookRemovedEvent {
        Objects.requireNonNull(notebookId, "notebookId must not be null");
    }
}
