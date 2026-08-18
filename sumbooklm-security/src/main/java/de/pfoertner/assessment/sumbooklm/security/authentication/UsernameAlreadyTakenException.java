package de.pfoertner.assessment.sumbooklm.security.authentication;

/**
 * Signals that an account cannot be created because its username is already in use.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class UsernameAlreadyTakenException extends RuntimeException {

    /**
     * Identifies the serialized form of this class across compilations.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param username login name that is already in use
     */
    public UsernameAlreadyTakenException(final String username) {
        super("The username '" + username + "' is already taken");
    }
}
