package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.UUID;

/**
 * Raised when a conversation is addressed that the requesting account does not hold.
 *
 * <h2>Wording</h2>
 * As everywhere below a notebook, a conversation of another account is missing rather than forbidden.
 * Saying that it exists but may not be read would confirm the identifier to a caller who only
 * guessed it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class ChatSessionNotFoundException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param sessionId identifier the caller addressed
     */
    public ChatSessionNotFoundException(final UUID sessionId) {
        super("No chat session with identifier " + sessionId);
    }
}
