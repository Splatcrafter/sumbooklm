package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * Data access for chat sessions.
 *
 * <h2>Appending Is a Read and a Write</h2>
 * A message is added by decoding the transcript, appending to it and encoding it again. Two of those
 * at once would each write a transcript that is missing the other's message, so the read that starts
 * one takes a write lock on the row rather than a version that is compared at the end. The optimistic
 * counter would only tell the second writer that it lost, and losing a generated answer is not
 * something a caller can be asked to repeat.
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
     * Reads the conversations of one notebook of an account, most recently used first.
     *
     * @param notebookId identifier of the notebook the sessions belong to
     * @param userId     identifier of the owning account
     * @return the conversations of the notebook, ordered by their most recent message descending
     */
    List<ChatSessionEntity> findAllByNotebookIdAndUserIdOrderByLastMessageAtDesc(UUID notebookId, UUID userId);

    /**
     * Reads one session of an account.
     *
     * @param id     identifier of the session to read
     * @param userId identifier of the owning account
     * @return the session, or empty if the account holds no session with that identifier
     */
    Optional<ChatSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Reads one session of an account and locks it against other writers.
     *
     * @param id     identifier of the session to read
     * @param userId identifier of the owning account
     * @return the session, or empty if the account holds no session with that identifier
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChatSessionEntity> findForUpdateByIdAndUserId(UUID id, UUID userId);

    /**
     * Deletes every session of a notebook.
     *
     * @param notebookId identifier of the notebook whose sessions are removed
     * @param userId     identifier of the owning account
     * @return number of removed sessions
     */
    long deleteByNotebookIdAndUserId(UUID notebookId, UUID userId);
}
