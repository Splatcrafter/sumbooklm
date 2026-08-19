package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The request to stop an answer that is being generated.
 *
 * <h2>A Flag, Not a Handle</h2>
 * Stopping is recorded rather than performed. The stream is being read on another thread, and that
 * thread is the only one that may act on it: it notices the request between two parts of the answer,
 * finishes what it has, and stops passing the rest on.
 *
 * <h2>Why Not the Handle of the Client</h2>
 * The chat client offers a handle whose {@code cancel} closes the body of the response, and the HTTP
 * stack underneath drains a chunked body before it closes it. Calling it from the thread that asked
 * for the stop blocks that request until the provider has finished writing an answer nobody wants,
 * and calling it from the reading thread buys nothing over reading the rest and discarding it. The
 * handle is therefore not used at all; see the open questions for what that leaves.
 *
 * <h2>Threading</h2>
 * The request arrives on one thread and is read on another, so the flag is published between them.
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
     * Creates a cancellation for one answer.
     */
    public AnswerCancellation() {
    }

    /**
     * Asks for the answer to stop at the next part that arrives.
     */
    public void cancel() {
        this.requested.set(true);
    }

    /**
     * Reports whether a stop was asked for.
     *
     * @return {@code true} once {@link #cancel()} was called
     */
    public boolean isRequested() {
        return this.requested.get();
    }
}
