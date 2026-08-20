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

package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import de.pfoertner.assessment.sumbooklm.ai.chat.AnswerCancellation;
import org.springframework.stereotype.Component;

/**
 * The answers being generated right now, so that one of them can be stopped.
 *
 * <h2>Why a Registry</h2>
 * A stop arrives as its own request, on another thread and possibly on another connection than the
 * one the answer is being written to. The only thing the two share is the conversation, so that is
 * what an answer is registered under.
 *
 * <h2>One per Conversation</h2>
 * A second answer in the same conversation replaces the first in this map, and stopping then stops
 * the newer one. The interface does not offer asking twice in one conversation at once, so the case
 * is a client doing something the interface does not, rather than a state to preserve.
 *
 * <h2>Entries Do Not Outlive Their Answer</h2>
 * An answer removes itself when it ends, whichever way it ends, so the map holds what is running
 * rather than what has ever run.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class RunningAnswers {

    /**
     * Answers being generated, by the conversation they belong to.
     */
    private final Map<UUID, RunningAnswer> running = new ConcurrentHashMap<>();

    /**
     * Creates the registry. The instance is created by the container.
     */
    public RunningAnswers() {
    }

    /**
     * Records that an answer is being generated.
     *
     * @param userId       identifier of the account the conversation belongs to
     * @param sessionId    identifier of the conversation the answer belongs to
     * @param cancellation the way to stop that answer
     */
    public void register(final UUID userId, final UUID sessionId, final AnswerCancellation cancellation) {
        this.running.put(sessionId, new RunningAnswer(userId, cancellation));
    }

    /**
     * Records that an answer has ended.
     *
     * @param sessionId    identifier of the conversation the answer belonged to
     * @param cancellation the registration to remove, so that an answer that already replaced this
     *                     one is left alone
     */
    public void unregister(final UUID sessionId, final AnswerCancellation cancellation) {
        this.running.remove(sessionId, new RunningAnswer(null, cancellation));
    }

    /**
     * Stops the answer of one conversation, if there is one and it belongs to the account.
     *
     * @param userId    identifier of the account asking for the stop
     * @param sessionId identifier of the conversation whose answer is to stop
     * @return {@code true} if an answer was stopped
     */
    public boolean stop(final UUID userId, final UUID sessionId) {
        final RunningAnswer answer = this.running.get(sessionId);
        if (answer == null || !userId.equals(answer.userId())) {
            return false;
        }
        answer.cancellation().cancel();
        return true;
    }

    /**
     * One answer being generated.
     *
     * <h2>Equality</h2>
     * Two entries are the same when they concern the same cancellation, so that removing an entry
     * removes the one that ended rather than whatever is registered under the key at that moment. The
     * account is not part of it for the same reason.
     *
     * @param userId       identifier of the account the conversation belongs to, absent in the value
     *                     used to remove an entry
     * @param cancellation the way to stop the answer
     * @author Erik Pförtner
     * @since 0.1.0
     */
    private record RunningAnswer(UUID userId, AnswerCancellation cancellation) {

        @Override
        public boolean equals(final Object other) {
            return other instanceof RunningAnswer running && running.cancellation() == this.cancellation;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.cancellation);
        }
    }
}
