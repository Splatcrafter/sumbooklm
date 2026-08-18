package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for notebooks.
 *
 * <h2>Owner Scoped Queries</h2>
 * Every method below carries the owner as a parameter, including the ones that already receive an
 * identifier. A lookup by identifier alone would return a notebook of another account whenever a
 * caller learned or guessed its identifier, so the owner is part of the query rather than of a check
 * performed afterwards.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface NotebookRepository extends JpaRepository<NotebookEntity, UUID> {

    /**
     * Finds the notebooks of an account, most recently active first.
     *
     * @param userId identifier of the owning account
     * @return the notebooks of the account, ordered by their activity timestamp descending
     */
    List<NotebookEntity> findAllByUserIdOrderByLastActivityAtDesc(UUID userId);

    /**
     * Finds one notebook of an account.
     *
     * @param id     identifier of the notebook
     * @param userId identifier of the owning account
     * @return the notebook, or an empty result if the account owns no notebook with that identifier
     */
    Optional<NotebookEntity> findByIdAndUserId(UUID id, UUID userId);
}
