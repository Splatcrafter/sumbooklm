package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for the record of asked questions.
 *
 * <h2>Everything Is Scoped to a Window</h2>
 * No method here reads the whole history of an account. A bound on how often somebody asks is about a
 * span of time, so every query carries the moment that span begins, and rows older than it are deleted
 * rather than kept for a statistic nobody asked for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface AskedQuestionRepository extends JpaRepository<AskedQuestionEntity, UUID> {

    /**
     * Counts the questions an account asked within a window.
     *
     * @param userId identifier of the account
     * @param since  moment the window begins
     * @return number of questions the account asked since that moment
     */
    long countByUserIdAndAskedAtGreaterThanEqual(UUID userId, Instant since);

    /**
     * Reads the oldest question of an account within a window, which is the one whose leaving the
     * window makes room for the next.
     *
     * @param userId identifier of the account
     * @param since  moment the window begins
     * @return the oldest recorded moment inside the window, or an empty result if there is none
     */
    @Query("""
            select min(question.askedAt)
            from AskedQuestionEntity question
            where question.userId = :userId and question.askedAt >= :since
            """)
    Optional<Instant> findOldestWithin(@Param("userId") UUID userId, @Param("since") Instant since);

    /**
     * Deletes the questions of one account that are older than a moment.
     *
     * @param userId identifier of the account
     * @param before moment before which the records are no longer of interest
     * @return number of deleted records
     */
    long deleteByUserIdAndAskedAtLessThan(UUID userId, Instant before);

    /**
     * Deletes the questions of every account that are older than a moment.
     *
     * <p>The statement deliberately spans all accounts. It serves the sweep that keeps the table from
     * holding records of accounts that stopped asking, which no request of theirs would ever reach.
     *
     * @param before moment before which the records are no longer of interest
     * @return number of deleted records
     */
    @Modifying
    @Query("delete from AskedQuestionEntity question where question.askedAt < :before")
    int deleteAskedBefore(@Param("before") Instant before);
}
