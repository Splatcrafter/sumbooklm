package de.pfoertner.assessment.sumbooklm.persistence.payload;

/**
 * Signals that a payload could not be encoded, migrated or decoded.
 *
 * <h2>Meaning</h2>
 * The exception indicates that stored bytes and the expected payload schema do not fit together, or
 * that the migration pipeline rejected the data. It is not recoverable at the call site: the caller
 * cannot repair the payload, only report it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class PayloadCodecException extends RuntimeException {

    /**
     * Identifies the serialized form of this class across compilations.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of what failed
     */
    public PayloadCodecException(final String message) {
        super(message);
    }

    /**
     * Creates the exception with the failure that caused it.
     *
     * @param message description of what failed
     * @param cause   underlying failure
     */
    public PayloadCodecException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
