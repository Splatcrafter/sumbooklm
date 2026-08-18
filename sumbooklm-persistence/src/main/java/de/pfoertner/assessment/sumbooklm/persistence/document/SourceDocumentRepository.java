package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for source documents.
 *
 * <h2>Counting per Notebook</h2>
 * The overview shows how many sources each notebook holds. Answering that with one count query per
 * notebook would issue as many statements as the account has notebooks, so the count is grouped in a
 * single query and joined to the notebooks in memory.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface SourceDocumentRepository extends JpaRepository<SourceDocumentEntity, UUID> {

    /**
     * Counts the sources of every notebook of an account.
     *
     * @param userId identifier of the owning account
     * @return one entry per notebook that holds at least one source
     */
    @Query("""
            select document.notebookId as notebookId, count(document) as sourceCount
            from SourceDocumentEntity document
            where document.userId = :userId
            group by document.notebookId
            """)
    List<NotebookSourceCount> countPerNotebook(@Param("userId") UUID userId);

    /**
     * Counts the sources of one notebook.
     *
     * @param notebookId identifier of the notebook to count the sources of
     * @param userId     identifier of the owning account
     * @return number of sources the notebook holds
     */
    long countByNotebookIdAndUserId(UUID notebookId, UUID userId);

    /**
     * Deletes every source of a notebook.
     *
     * @param notebookId identifier of the notebook whose sources are removed
     * @param userId     identifier of the owning account
     * @return number of removed sources
     */
    long deleteByNotebookIdAndUserId(UUID notebookId, UUID userId);
}
