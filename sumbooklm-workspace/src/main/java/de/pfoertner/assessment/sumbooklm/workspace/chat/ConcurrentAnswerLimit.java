package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Bounds how many answers one account may have in flight at the same time.
 *
 * <h2>Why per Account</h2>
 * An answer holds a thread of the answering pool for as long as the provider takes, which can be
 * minutes. Without a limit one account asking forty questions and forty accounts asking one look the
 * same to the pool, and the first of them fills it. The tokens are paid for by the user either way;
 * the threads are not.
 *
 * <h2>Counted, Not Queued</h2>
 * A question beyond the limit is refused rather than made to wait. Waiting would hold the request
 * open for the length of an answer that has not started, which is indistinguishable from a
 * server that has stopped responding, and the client can ask again as soon as one of its own answers
 * has arrived.
 *
 * <h2>In This Process Only</h2>
 * The count lives in the heap of one instance. Two instances behind a load balancer therefore permit
 * twice the limit, which is the correct shape for a bound on threads: what is being protected is the
 * pool of the instance that took the request, and each pool is protected by its own count.
 *
 * <h2>Not the Bound on Spending</h2>
 * This one says nothing about how often an account asks. An account that asks one question, waits for
 * it and asks the next is inside this limit for as long as it likes, which is why there is a second
 * bound that is a rate and is counted where every instance sees it; see {@link QuestionRateLimit}.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class ConcurrentAnswerLimit {

    /**
     * Number of answers one account may have in flight. It is set above one so that a reader can ask
     * about a second Sumbook without waiting for the first, and far below the size of the pool so
     * that one account cannot fill it.
     */
    private static final int MAX_PER_ACCOUNT = 3;

    /**
     * Answers currently in flight, by account. An account without an entry has none, which is what
     * keeps the map to the accounts that are answering rather than to every account there is.
     */
    private final Map<UUID, Integer> inFlight = new ConcurrentHashMap<>();

    /**
     * Creates the limit. The instance is created by the container.
     */
    public ConcurrentAnswerLimit() {
    }

    /**
     * Takes one of the permits of an account, unless it holds all of them already.
     *
     * @param userId identifier of the account asking
     * @return {@code true} if a permit was taken and has to be returned afterwards
     */
    public boolean tryAcquire(final UUID userId) {
        final AtomicBoolean acquired = new AtomicBoolean();
        this.inFlight.compute(userId, (id, count) -> {
            if (count == null) {
                acquired.set(true);
                return 1;
            }
            if (count >= MAX_PER_ACCOUNT) {
                return count;
            }
            acquired.set(true);
            return count + 1;
        });
        return acquired.get();
    }

    /**
     * Returns a permit that was taken.
     *
     * @param userId identifier of the account whose answer has ended
     */
    public void release(final UUID userId) {
        this.inFlight.computeIfPresent(userId, (id, count) -> count <= 1 ? null : count - 1);
    }
}
