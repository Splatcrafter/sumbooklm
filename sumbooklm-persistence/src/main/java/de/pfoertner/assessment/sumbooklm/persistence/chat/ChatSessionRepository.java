package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.util.Optional;
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
     * Reads the conversation of one notebook of an account.
     *
     * <p>A notebook holds at most one conversation today, and the query returns the oldest should a
     * second one ever be created, so that the conversation a user sees does not change when the model
     * behind it grows a second session.
     *
     * @param notebookId identifier of the notebook the session belongs to
     * @param userId     identifier of the owning account
     * @return the conversation of the notebook, or empty if none was started yet
     */
    Optional<ChatSessionEntity> findFirstByNotebookIdAndUserIdOrderByCreatedAtAsc(UUID notebookId, UUID userId);

    /**
     * Reads one session of an account.
     *
     * @param id     identifier of the session to read
     * @param userId identifier of the owning account
     * @return the session, or empty if the account holds no session with that identifier
     */
    Optional<ChatSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Deletes every session of a notebook.
     *
     * @param notebookId identifier of the notebook whose sessions are removed
     * @param userId     identifier of the owning account
     * @return number of removed sessions
     */
    long deleteByNotebookIdAndUserId(UUID notebookId, UUID userId);
}
