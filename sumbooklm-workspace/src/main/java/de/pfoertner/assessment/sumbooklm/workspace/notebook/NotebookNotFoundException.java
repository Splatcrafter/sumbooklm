package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.io.Serial;
import java.util.UUID;

/**
 * Raised when an account holds no notebook with the requested identifier.
 *
 * <h2>Two Causes, One Failure</h2>
 * The failure is raised both when no notebook with the identifier exists and when one exists but
 * belongs to another account. Distinguishing the two would confirm the existence of a notebook to
 * someone who is not allowed to see it, so the caller receives the same answer either way.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class NotebookNotFoundException extends RuntimeException {

    /**
     * Serialization version of the class.
     */
    @Serial
    private static final long serialVersionUID = 2350504704244333694L;

    /**
     * Creates the failure.
     *
     * @param notebookId identifier that could not be resolved for the requesting account
     */
    public NotebookNotFoundException(final UUID notebookId) {
        super("No notebook with identifier " + notebookId + " belongs to the requesting account");
    }
}
