package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for chat sessions.
 *
 * <h2>Owner Scoped Queries</h2>
 * As with every aggregate below a notebook, the owning account is part of the query rather than of a
 * check performed afterwards, so that knowing an identifier is not enough to reach a row.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    /**
     * Deletes every session of a notebook.
     *
     * @param notebookId identifier of the notebook whose sessions are removed
     * @param userId     identifier of the owning account
     * @return number of removed sessions
     */
    long deleteByNotebookIdAndUserId(UUID notebookId, UUID userId);
}
