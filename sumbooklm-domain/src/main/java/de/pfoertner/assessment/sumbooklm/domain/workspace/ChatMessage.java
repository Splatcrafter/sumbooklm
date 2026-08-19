package de.pfoertner.assessment.sumbooklm.domain.workspace;

import java.time.Instant;
import java.util.Objects;

/**
 * One message of a conversation held inside a notebook.
 *
 * <h2>Text as It Was Produced</h2>
 * The text is stored exactly as it was written or generated, including the Markdown an answer carries.
 * Rendering it is a decision of the client, and stripping the markup here would destroy the citation
 * markers that connect a sentence to the source it came from.
 *
 * @param role      author of the message
 * @param text      content of the message as it was written or generated
 * @param createdAt point in time the message was appended to its conversation
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatMessage(ChatRole role, String text, Instant createdAt) {

    /**
     * Creates the message.
     *
     * @param role      author of the message
     * @param text      content of the message
     * @param createdAt point in time the message was appended to its conversation
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
