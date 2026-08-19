package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.Objects;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;

/**
 * One earlier message of the conversation, as the model is reminded of it.
 *
 * <h2>Narrower Than a Stored Message</h2>
 * A stored message also carries the point in time it was written at. A model is not told when
 * something was said, only who said it and what, so the timestamp stops at the boundary of this
 * package rather than being passed on and ignored.
 *
 * @param role author of the message
 * @param text content of the message
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatTurn(ChatRole role, String text) {

    /**
     * Creates the turn.
     *
     * @param role author of the message
     * @param text content of the message
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatTurn {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
