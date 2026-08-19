package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.List;
import java.util.Optional;
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
     * Finds every source there is, oldest first, reduced to the identifiers that address it.
     *
     * <p>The query deliberately spans all accounts. It serves the rebuild of the retrieval index,
     * which is a property of the process rather than of one account, and which therefore has to see
     * every source the database holds.
     *
     * @return one entry per stored source, in the order the sources were added
     */
    @Query("""
            select document.id as id, document.userId as userId
            from SourceDocumentEntity document
            order by document.createdAt asc
            """)
    List<SourceReference> findAllReferences();

    /**
     * Finds the sources of one notebook, oldest first.
     *
     * @param notebookId identifier of the notebook the sources belong to
     * @param userId     identifier of the owning account
     * @return the sources of the notebook, in the order they were added
     */
    List<SourceDocumentEntity> findAllByNotebookIdAndUserIdOrderByCreatedAtAsc(UUID notebookId, UUID userId);

    /**
     * Finds one source of an account.
     *
     * @param id     identifier of the source
     * @param userId identifier of the owning account
     * @return the source, or an empty result if the account owns no source with that identifier
     */
    Optional<SourceDocumentEntity> findByIdAndUserId(UUID id, UUID userId);

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
