package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.io.Serial;

/**
 * Raised when an upload carries no bytes.
 *
 * <h2>Why It Is Rejected Here</h2>
 * A source without content can never become searchable, so accepting it would mean storing a row
 * whose only possible outcome is a failed indexing run. Refusing it while the caller is still
 * waiting turns that into an answer they can act on.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class EmptyUploadException extends RuntimeException {

    /**
     * Serialization version of the class.
     */
    @Serial
    private static final long serialVersionUID = 1477528301964453100L;

    /**
     * Creates the failure.
     *
     * @param fileName name the empty file was uploaded under
     */
    public EmptyUploadException(final String fileName) {
        super("The uploaded file " + fileName + " carries no bytes");
    }
}
