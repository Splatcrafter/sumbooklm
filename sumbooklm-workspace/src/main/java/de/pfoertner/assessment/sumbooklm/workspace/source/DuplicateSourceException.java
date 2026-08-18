package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.io.Serial;
import java.util.UUID;

/**
 * Raised when a notebook already holds a source with the same content.
 *
 * <h2>Scope of the Comparison</h2>
 * The comparison is made within one notebook. The same document may be added to two notebooks,
 * because a notebook is a workspace rather than a library, and the same document can legitimately
 * be part of two pieces of work.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class DuplicateSourceException extends RuntimeException {

    /**
     * Serialization version of the class.
     */
    @Serial
    private static final long serialVersionUID = -3311095642019283440L;

    /**
     * Creates the failure.
     *
     * @param notebookId identifier of the notebook that already holds the content
     */
    public DuplicateSourceException(final UUID notebookId) {
        super("Notebook " + notebookId + " already holds a source with this content");
    }
}
