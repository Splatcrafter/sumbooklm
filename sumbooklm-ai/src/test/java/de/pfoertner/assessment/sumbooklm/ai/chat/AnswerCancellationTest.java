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

package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.model.chat.response.StreamingHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the handle a reader stops an answer with.
 *
 * <h2>The Race It Exists For</h2>
 * A reader may press stop before the provider has answered at all, which is before there is anything
 * to abandon. The two orders below are the whole point of the class: the request arriving first and
 * the stream arriving first have to end in the same place, and neither may cancel a stream twice.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class AnswerCancellationTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    AnswerCancellationTest() {
    }

    /**
     * Verifies that a fresh handle reports no request, which is what lets a caller ask whether the
     * answer it is about to write is still wanted.
     */
    @Test
    void nothingIsRequestedBeforeAnybodyAsks() {
        assertThat(new AnswerCancellation().isRequested()).isFalse();
    }

    /**
     * Verifies that a stream attached first is abandoned when the reader stops the answer.
     */
    @Test
    void aStreamThatIsAlreadyRunningIsAbandoned() {
        final AnswerCancellation cancellation = new AnswerCancellation();
        final RecordingHandle handle = new RecordingHandle();
        cancellation.attach(handle);

        cancellation.cancel();

        assertThat(cancellation.isRequested()).isTrue();
        assertThat(handle.cancellations()).isEqualTo(1);
    }

    /**
     * Verifies that a stream which arrives after the reader stopped the answer is abandoned as it
     * arrives, which is the race the handle exists for.
     */
    @Test
    void aStreamThatArrivesTooLateIsAbandonedAtOnce() {
        final AnswerCancellation cancellation = new AnswerCancellation();
        cancellation.cancel();

        final RecordingHandle handle = new RecordingHandle();
        cancellation.attach(handle);

        assertThat(handle.cancellations()).isEqualTo(1);
    }

    /**
     * Verifies that stopping an answer twice abandons its stream once, because the second request
     * finds a stream that is already cancelled.
     */
    @Test
    void stoppingTwiceAbandonsTheStreamOnce() {
        final AnswerCancellation cancellation = new AnswerCancellation();
        final RecordingHandle handle = new RecordingHandle();
        cancellation.attach(handle);

        cancellation.cancel();
        cancellation.cancel();

        assertThat(handle.cancellations()).isEqualTo(1);
    }

    /**
     * Verifies that attaching a second stream after the answer was stopped abandons that one too,
     * which is what a provider client reconnecting would produce.
     */
    @Test
    void aSecondStreamIsAbandonedAsWell() {
        final AnswerCancellation cancellation = new AnswerCancellation();
        final RecordingHandle first = new RecordingHandle();
        cancellation.attach(first);
        cancellation.cancel();

        final RecordingHandle second = new RecordingHandle();
        cancellation.attach(second);

        assertThat(first.cancellations()).isEqualTo(1);
        assertThat(second.cancellations()).isEqualTo(1);
    }

    /**
     * Verifies that a stream which is attached while nobody asked to stop is left running.
     */
    @Test
    void aStreamNobodyStoppedKeepsRunning() {
        final RecordingHandle handle = new RecordingHandle();

        new AnswerCancellation().attach(handle);

        assertThat(handle.cancellations()).isZero();
    }

    /**
     * A stream that counts how often it was abandoned.
     *
     * <h2>Why It Is Written Out</h2>
     * The class under test cancels only a stream that does not report itself as cancelled, so the
     * two methods of the interface have to answer each other. A stub that always reported the same
     * state would hide exactly the case the counting is for.
     */
    private static final class RecordingHandle implements StreamingHandle {

        /**
         * Number of times the stream was asked to stop while it was still running.
         */
        private final AtomicInteger cancellations = new AtomicInteger();

        /**
         * Creates the stream.
         */
        private RecordingHandle() {
        }

        @Override
        public void cancel() {
            this.cancellations.incrementAndGet();
        }

        @Override
        public boolean isCancelled() {
            return this.cancellations.get() > 0;
        }

        /**
         * Reports how often the stream was asked to stop.
         *
         * @return the number of requests the stream received while it was running
         */
        private int cancellations() {
            return this.cancellations.get();
        }
    }
}
