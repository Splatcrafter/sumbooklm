package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.UUID;

/**
 * Raised when an account already has as many answers in flight as it may have.
 *
 * <h2>Not a Failure of the Question</h2>
 * The question is fine and nothing about it is stored. What the caller is told is that this one has
 * to wait for one of their own answers, which is a state that passes on its own rather than something
 * they have to correct.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class TooManyQuestionsException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param userId identifier of the account that asked
     */
    public TooManyQuestionsException(final UUID userId) {
        super("The account " + userId + " already has as many answers in flight as it may have");
    }
}
