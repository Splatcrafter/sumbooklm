package de.pfoertner.assessment.sumbooklm.security.authentication;

import java.io.Serial;

/**
 * Signals that an account cannot be created because its username is already in use.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class UsernameAlreadyTakenException extends RuntimeException {

    /**
     * Serialization version for compatibility with older versions of the class.
     */
    @Serial
    private static final long serialVersionUID = -7623915014633367502L;

    /**
     * Creates the exception.
     *
     * @param username login name that is already in use
     */
    public UsernameAlreadyTakenException(final String username) {
        super("The username '" + username + "' is already taken");
    }
}
