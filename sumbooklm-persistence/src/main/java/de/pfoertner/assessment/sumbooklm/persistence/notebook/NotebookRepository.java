/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for notebooks.
 *
 * <h2>Touching Is Not Editing</h2>
 * The activity timestamp is refreshed by everything that happens inside a notebook, and by several
 * things at once when a user works in two places. Refreshing it through the entity would put those
 * writes against the optimistic locking counter and let one of them fail, although they do not
 * disagree about anything: both mean now. It is therefore written by a statement that neither reads
 * nor raises that counter.
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

    /**
     * Refreshes the activity timestamp of one notebook of an account.
     *
     * <p>The statement takes a write lock on the row for the rest of the transaction, which is what
     * serialises everything that happens inside one notebook. A caller that touches the notebook
     * before it reads what it is about to change therefore sees the result of whatever else was
     * doing the same, instead of both of them writing over one another.
     *
     * @param id     identifier of the notebook
     * @param userId identifier of the owning account
     * @param at     point in time to record as the most recent activity
     * @return number of updated rows, which is zero when the account owns no such notebook
     */
    @Modifying
    @Query("""
            update NotebookEntity notebook
            set notebook.lastActivityAt = :at
            where notebook.id = :id and notebook.userId = :userId
            """)
    int touch(@Param("id") UUID id, @Param("userId") UUID userId, @Param("at") Instant at);
}
