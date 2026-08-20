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

import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.AnswerCancellation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the register an answer is stopped through.
 *
 * <h2>Why Ownership Is Checked Here</h2>
 * Stopping an answer is reached by naming a conversation, and the register is the only place that
 * knows which account an answer being written belongs to. If it did not check, the identifier of a
 * conversation would be enough to stop somebody else's answer, which is a thing anybody could guess
 * at. The other half is the ending: an answer that finished must not be stoppable afterwards, and a
 * later answer of the same conversation must not be stopped by the ending of the earlier one.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class RunningAnswersTest {

    /**
     * Register under test.
     */
    private final RunningAnswers running = new RunningAnswers();

    /**
     * Account the answers of the cases belong to.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Conversation the answers of the cases are written in.
     */
    private final UUID sessionId = UUID.randomUUID();

    /**
     * Creates the test class.
     */
    RunningAnswersTest() {
    }

    /**
     * Verifies that an answer being written can be stopped by the account it belongs to.
     */
    @Test
    void anAnswerCanBeStoppedByItsAccount() {
        final AnswerCancellation cancellation = new AnswerCancellation();
        this.running.register(this.userId, this.sessionId, cancellation);

        assertThat(this.running.stop(this.userId, this.sessionId)).isTrue();
        assertThat(cancellation.isRequested()).isTrue();
    }

    /**
     * Verifies that another account cannot stop an answer, even when it names the conversation
     * exactly, and that the answer keeps being written.
     */
    @Test
    void anotherAccountCannotStopAnAnswer() {
        final AnswerCancellation cancellation = new AnswerCancellation();
        this.running.register(this.userId, this.sessionId, cancellation);

        assertThat(this.running.stop(UUID.randomUUID(), this.sessionId)).isFalse();
        assertThat(cancellation.isRequested()).isFalse();
    }

    /**
     * Verifies that stopping a conversation nobody is answering in is answered with no rather than
     * with a failure, because a client may press stop after the answer arrived.
     */
    @Test
    void stoppingAnAnswerNobodyIsWritingIsAnsweredWithNo() {
        assertThat(this.running.stop(this.userId, UUID.randomUUID())).isFalse();
    }

    /**
     * Verifies that an answer which ended can no longer be stopped, because the register is what
     * says whether one is still being written.
     */
    @Test
    void anAnswerThatEndedCanNoLongerBeStopped() {
        final AnswerCancellation cancellation = new AnswerCancellation();
        this.running.register(this.userId, this.sessionId, cancellation);

        this.running.unregister(this.sessionId, cancellation);

        assertThat(this.running.stop(this.userId, this.sessionId)).isFalse();
    }

    /**
     * Verifies that the ending of an earlier answer does not remove a later one of the same
     * conversation, which is what a reader asking again straight away produces.
     */
    @Test
    void theEndingOfAnEarlierAnswerLeavesTheLaterOne() {
        final AnswerCancellation earlier = new AnswerCancellation();
        final AnswerCancellation later = new AnswerCancellation();
        this.running.register(this.userId, this.sessionId, earlier);
        this.running.register(this.userId, this.sessionId, later);

        this.running.unregister(this.sessionId, earlier);

        assertThat(this.running.stop(this.userId, this.sessionId)).isTrue();
        assertThat(later.isRequested()).isTrue();
        assertThat(earlier.isRequested()).isFalse();
    }

    /**
     * Verifies that answers of different conversations are stopped apart, so that stopping one does
     * not stop another.
     */
    @Test
    void answersOfDifferentConversationsAreStoppedApart() {
        final AnswerCancellation first = new AnswerCancellation();
        final AnswerCancellation second = new AnswerCancellation();
        final UUID otherSession = UUID.randomUUID();
        this.running.register(this.userId, this.sessionId, first);
        this.running.register(this.userId, otherSession, second);

        this.running.stop(this.userId, this.sessionId);

        assertThat(first.isRequested()).isTrue();
        assertThat(second.isRequested()).isFalse();
    }

    /**
     * Verifies that stopping an answer twice is answered with yes both times, because the second
     * request finds the same answer and asking again is not an error.
     */
    @Test
    void stoppingTwiceIsAnsweredTwice() {
        this.running.register(this.userId, this.sessionId, new AnswerCancellation());

        assertThat(this.running.stop(this.userId, this.sessionId)).isTrue();
        assertThat(this.running.stop(this.userId, this.sessionId)).isTrue();
    }
}
