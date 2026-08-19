package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.pfoertner.assessment.sumbooklm.persistence.chat.AskedQuestionEntity;
import de.pfoertner.assessment.sumbooklm.persistence.chat.AskedQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounds how often one account may ask, counted over the last hour and across every instance.
 *
 * <h2>Why This Is Not the Other Bound</h2>
 * The bound on answers in flight protects the thread pool of the instance that took the request, which
 * is why it lives in that instance and is a concurrency. This one protects what the installation as a
 * whole is willing to serve, so it is a rate, and it is counted where every instance can see it.
 * An account that asks one question, waits for it and asks the next is inside the first bound forever
 * and is what this one is for.
 *
 * <h2>The Database Is the Shared Count</h2>
 * The count lives in the table that every instance already writes to. A store of its own would be a
 * second thing to operate, and what it would buy is a counter that is faster than the transcript write
 * happening in the same request.
 *
 * <h2>Recorded Before the Question Is Stored</h2>
 * A question is recorded here first and appended to the transcript afterwards, so a turn that fails
 * after this point has still been counted. That is the safe direction: a bound that only counted the
 * questions that were stored successfully could be avoided by asking in a way that fails late.
 *
 * <h2>Racing Is Bounded by the Other Bound</h2>
 * Two questions of one account can read the same count and both be admitted, so the limit can be
 * exceeded by as many questions as that account may have in flight. Serialising it would mean locking
 * the account for every question, which is a cost paid by everybody to make a bound exact that is
 * approximate by nature.
 *
 * <h2>Rows Do Not Accumulate</h2>
 * The records an account left behind the window are deleted whenever it asks again, and a daily sweep
 * removes the ones of accounts that stopped asking. The table therefore holds about as many rows as
 * were asked in the last hour rather than everything that was ever asked.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class QuestionRateLimit {

    /**
     * Span of time the questions of an account are counted over.
     */
    private static final Duration WINDOW = Duration.ofHours(1);

    /**
     * Time between the start of the application and the first sweep of records nobody will read.
     */
    private static final long SWEEP_INITIAL_DELAY_MINUTES = 30;

    /**
     * Time between two sweeps.
     */
    private static final long SWEEP_INTERVAL_MINUTES = 24 * 60;

    /**
     * Log the sweep reports to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(QuestionRateLimit.class);

    /**
     * Data access for the record of asked questions.
     */
    private final AskedQuestionRepository askedQuestionRepository;

    /**
     * Settings the number of questions per window is read from.
     */
    private final ChatProperties chatProperties;

    /**
     * Source of the current time.
     */
    private final Clock clock;

    /**
     * Creates the bound.
     *
     * @param askedQuestionRepository data access for the record of asked questions
     * @param chatProperties          settings the number of questions per window is read from
     * @param clock                   source of the current time
     */
    public QuestionRateLimit(final AskedQuestionRepository askedQuestionRepository,
                             final ChatProperties chatProperties,
                             final Clock clock) {
        this.askedQuestionRepository = askedQuestionRepository;
        this.chatProperties = chatProperties;
        this.clock = clock;
    }

    /**
     * Records one question of an account, unless it has asked as often as it may.
     *
     * @param userId identifier of the account that is asking
     * @throws QuestionsTooOftenException if the account has reached the number of questions it may ask
     *                                    within the window
     */
    @Transactional
    public void record(final UUID userId) {
        final Instant now = this.clock.instant();
        final Instant since = now.minus(WINDOW);
        this.askedQuestionRepository.deleteByUserIdAndAskedAtLessThan(userId, since);

        final long asked = this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(userId, since);
        if (asked >= this.chatProperties.questionsPerHour()) {
            throw new QuestionsTooOftenException(userId, retryAfter(userId, since, now));
        }
        this.askedQuestionRepository.save(new AskedQuestionEntity(UUID.randomUUID(), userId, now));
    }

    /**
     * Removes the records that no account can still be counted by.
     */
    @Scheduled(initialDelay = SWEEP_INITIAL_DELAY_MINUTES,
            fixedDelay = SWEEP_INTERVAL_MINUTES,
            timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void sweep() {
        final int removed =
                this.askedQuestionRepository.deleteAskedBefore(this.clock.instant().minus(WINDOW));
        if (removed > 0) {
            LOG.info("Removed {} records of questions older than the window they are counted over", removed);
        }
    }

    /**
     * Works out how long an account has to wait for room in the window.
     *
     * @param userId identifier of the account that asked
     * @param since  moment the window begins
     * @param now    current moment
     * @return time until the oldest question of the account leaves the window, never negative
     */
    private Duration retryAfter(final UUID userId, final Instant since, final Instant now) {
        final Instant oldest = this.askedQuestionRepository.findOldestWithin(userId, since).orElse(since);
        final Duration remaining = Duration.between(now, oldest.plus(WINDOW));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
