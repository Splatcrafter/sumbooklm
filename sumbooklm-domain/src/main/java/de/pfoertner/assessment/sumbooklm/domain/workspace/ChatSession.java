package de.pfoertner.assessment.sumbooklm.domain.workspace;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The conversation held inside one notebook.
 *
 * <h2>Whole Transcript</h2>
 * A session carries all of its messages rather than a page of them. What bounds the size is the
 * notebook it belongs to, and a client that opens a notebook needs the transcript in order to display
 * it, so splitting it into pages would add a second request without removing any work.
 *
 * @param id            stable identifier of the session
 * @param notebookId    identifier of the notebook the session belongs to
 * @param title         name the session is listed under, derived from its first question
 * @param messages      messages of the session, oldest first
 * @param createdAt     point in time the session was started
 * @param lastMessageAt point in time the most recent message was exchanged
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatSession(UUID id,
                          UUID notebookId,
                          String title,
                          List<ChatMessage> messages,
                          Instant createdAt,
                          Instant lastMessageAt) {

    /**
     * Creates the session.
     *
     * @param id            stable identifier of the session
     * @param notebookId    identifier of the notebook the session belongs to
     * @param title         name the session is listed under
     * @param messages      messages of the session, oldest first
     * @param createdAt     point in time the session was started
     * @param lastMessageAt point in time the most recent message was exchanged
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(notebookId, "notebookId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(lastMessageAt, "lastMessageAt must not be null");
        messages = List.copyOf(messages);
    }
}
