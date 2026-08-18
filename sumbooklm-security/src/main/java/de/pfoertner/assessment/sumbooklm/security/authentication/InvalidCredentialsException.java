package de.pfoertner.assessment.sumbooklm.security.authentication;

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
     * Identifies the serialized form of this class across compilations.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     */
    public InvalidCredentialsException() {
        super("The provided credentials are not valid");
    }
}
