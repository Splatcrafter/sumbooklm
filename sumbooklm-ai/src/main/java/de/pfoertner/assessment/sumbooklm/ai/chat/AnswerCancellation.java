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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * The request to stop an answer that is being generated.
 *
 * <h2>A Flag and a Handle</h2>
 * Stopping does two things, and both are needed. The flag is what the thread reading the stream
 * notices, so that nothing more reaches the reader and the run ends with what arrived. The handle is
 * what abandons the request to the provider, so that the rest of the answer is not generated at all.
 * Without the flag the reader would keep passing parts on until the abandoned connection was noticed;
 * without the handle the provider would finish an answer nobody is waiting for.
 *
 * <h2>Why the Handle Can Be Used Now</h2>
 * Cancelling closes the body of the response, and what that costs depends on the client underneath.
 * The client this application builds abandons the exchange, so the close returns at once and the
 * request that asked for the stop is not made to wait for the provider. An earlier attempt called the
 * same handle on a client whose close first read the remainder of the message, which held the stop
 * request open for as long as the answer took; see {@link ChatModelFactory}.
 *
 * <h2>Arriving in Either Order</h2>
 * The handle exists only once the provider has begun to answer, and a stop may arrive before that.
 * Both orders end with an abandoned request: a stop that arrives first is applied by the thread that
 * attaches the handle, and a handle that is there already is cancelled by the thread that stops.
 *
 * <h2>Threading</h2>
 * The request arrives on one thread and the answer is read on another, so both the flag and the handle
 * are published between them.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AnswerCancellation {

    /**
     * Whether a stop was asked for.
     */
    private final AtomicBoolean requested = new AtomicBoolean();

    /**
     * The way to abandon the request to the provider, absent until it has begun to answer.
     */
    private final AtomicReference<StreamingHandle> handle = new AtomicReference<>();

    /**
     * Creates a cancellation for one answer.
     */
    public AnswerCancellation() {
    }

    /**
     * Records the way to abandon the request, and abandons it immediately if a stop was already asked
     * for.
     *
     * @param streamingHandle handle of the response that is being read
     */
    public void attach(final StreamingHandle streamingHandle) {
        this.handle.set(streamingHandle);
        if (this.requested.get()) {
            abandon();
        }
    }

    /**
     * Asks for the answer to stop, and abandons the request to the provider if it has begun.
     */
    public void cancel() {
        this.requested.set(true);
        abandon();
    }

    /**
     * Reports whether a stop was asked for.
     *
     * @return {@code true} once {@link #cancel()} was called
     */
    public boolean isRequested() {
        return this.requested.get();
    }

    /**
     * Abandons the request to the provider, if there is one to abandon and it is still running.
     */
    private void abandon() {
        final StreamingHandle streamingHandle = this.handle.get();
        if (streamingHandle != null && !streamingHandle.isCancelled()) {
            streamingHandle.cancel();
        }
    }
}
