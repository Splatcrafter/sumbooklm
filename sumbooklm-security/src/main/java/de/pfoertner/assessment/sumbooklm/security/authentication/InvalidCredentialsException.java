package de.pfoertner.assessment.sumbooklm.security.authentication;

import java.io.Serial;

/**
 * Signals that a login was rejected.
 *
 * <h2>Deliberate Lack of Detail</h2>
 * The exception does not distinguish an unknown username from a wrong password. Reporting the
 * difference would let a caller enumerate which accounts exist.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Serialization version for compatibility with older versions of the class.
     */
    @Serial
    private static final long serialVersionUID = 8104319939084304950L;

    /**
     * Creates the exception.
     */
    public InvalidCredentialsException() {
        super("The provided credentials are not valid");
    }
}
