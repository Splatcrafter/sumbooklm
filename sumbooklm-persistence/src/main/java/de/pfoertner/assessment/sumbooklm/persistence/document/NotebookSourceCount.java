package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.UUID;

/**
 * Number of sources one notebook holds.
 *
 * <h2>Purpose</h2>
 * The projection is the result shape of the grouped count query of
 * {@link SourceDocumentRepository#countPerNotebook(UUID)}. It exists so that the query can return
 * two values per row without materialising the source rows themselves.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface NotebookSourceCount {

    /**
     * Returns the notebook the count belongs to.
     *
     * @return identifier of the notebook
     */
    UUID getNotebookId();

    /**
     * Returns the number of sources the notebook holds.
     *
     * @return number of sources, always greater than zero
     */
    long getSourceCount();
}
