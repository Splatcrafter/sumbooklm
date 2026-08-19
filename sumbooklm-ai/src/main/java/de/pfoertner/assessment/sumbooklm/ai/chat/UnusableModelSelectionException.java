package de.pfoertner.assessment.sumbooklm.ai.chat;

/**
 * Raised when the model a caller selected cannot be addressed with what they presented.
 *
 * <h2>Message Contents</h2>
 * The message names the part of the selection that is unusable and never the value of a key. It is
 * reported back to the caller, who is the only one able to correct their own settings, so it has to
 * be readable without being an echo of a secret.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class UnusableModelSelectionException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of what is missing or not understood about the selection
     */
    public UnusableModelSelectionException(final String message) {
        super(message);
    }
}
