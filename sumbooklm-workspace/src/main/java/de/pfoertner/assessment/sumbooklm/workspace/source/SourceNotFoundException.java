package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.io.Serial;
import java.util.UUID;

/**
 * Raised when an account holds no source with the requested identifier.
 *
 * <h2>Two Causes, One Failure</h2>
 * As with a notebook, a source that does not exist and a source that belongs to somebody else
 * produce the same failure. Telling the two apart would confirm that a source with that identifier
 * exists to a caller who is not allowed to see it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class SourceNotFoundException extends RuntimeException {

    /**
     * Serialization version of the class.
     */
    @Serial
    private static final long serialVersionUID = 7719302266400584112L;

    /**
     * Creates the failure.
     *
     * @param sourceId identifier that could not be resolved for the requesting account
     */
    public SourceNotFoundException(final UUID sourceId) {
        super("No source with identifier " + sourceId + " belongs to the requesting account");
    }
}
