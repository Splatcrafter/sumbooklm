package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.util.UUID;

/**
 * Raised when a summary is requested for a notebook that has no readable source.
 *
 * <h2>Not the Same as an Empty Notebook</h2>
 * The interface knows how many sources a notebook holds and does not offer a summary of none. What
 * reaches this exception is the case it cannot see: sources that exist but could not be read, or ones
 * that are still on their way into the index. Both are answered as a request that cannot be carried
 * out right now rather than as a summary that is empty, because an empty summary would be stored and
 * shown as the description of the notebook.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class NothingToSummariseException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param notebookId identifier of the notebook that holds nothing readable
     */
    public NothingToSummariseException(final UUID notebookId) {
        super("Notebook " + notebookId + " holds no source that has been read");
    }
}
