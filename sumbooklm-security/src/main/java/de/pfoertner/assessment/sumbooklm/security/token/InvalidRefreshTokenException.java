package de.pfoertner.assessment.sumbooklm.security.token;

/**
 * Signals that a presented refresh token is not accepted.
 *
 * <h2>Deliberate Lack of Detail</h2>
 * The exception carries no indication of which check failed. A caller that learns whether a token
 * was unknown, expired, revoked or malformed learns something about the state of the server it has
 * no legitimate use for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class InvalidRefreshTokenException extends RuntimeException {

    /**
     * Identifies the serialized form of this class across compilations.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     */
    public InvalidRefreshTokenException() {
        super("The presented refresh token is not valid");
    }
}
