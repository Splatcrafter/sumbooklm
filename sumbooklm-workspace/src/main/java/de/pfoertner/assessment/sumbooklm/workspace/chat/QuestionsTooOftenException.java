package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.time.Duration;
import java.util.UUID;

/**
 * Raised when an account has asked as many questions within the last hour as it may ask.
 *
 * <h2>Not the Same as Too Many at Once</h2>
 * {@link TooManyQuestionsException} says that an account is busy with its own answers and passes as
 * soon as one of them arrives. This one says that it has asked often enough for a while, and how long
 * that while is has to travel with it, because a caller that cannot tell the two apart would retry a
 * request that will be refused for the next hour.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class QuestionsTooOftenException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Time after which the account may ask again.
     */
    private final Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param userId     identifier of the account that asked
     * @param retryAfter time after which the oldest question leaves the window
     */
    public QuestionsTooOftenException(final UUID userId, final Duration retryAfter) {
        super("The account " + userId + " has asked as many questions as it may ask for the next "
                + retryAfter.toSeconds() + " seconds");
        this.retryAfter = retryAfter;
    }

    /**
     * Returns the time after which the account may ask again.
     *
     * @return time until the oldest question leaves the window, never negative
     */
    public Duration retryAfter() {
        return this.retryAfter;
    }
}
