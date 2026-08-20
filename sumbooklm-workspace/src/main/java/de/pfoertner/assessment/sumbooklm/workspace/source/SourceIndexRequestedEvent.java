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

package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.util.Objects;
import java.util.UUID;

/**
 * Announces that a source is waiting to be indexed.
 *
 * <h2>Why an Event</h2>
 * Indexing must not start before the row that describes the source is visible to other transactions.
 * Publishing the intent and letting the listener run after the commit is what guarantees that; a
 * call made directly from the storing method would race its own transaction and would sometimes find
 * nothing to index.
 *
 * <h2>Two Reasons, One Event</h2>
 * The event is published when a source is added and when reading it again is asked for. What differs
 * between them is one thing, and it is the one thing the listener needs: whether the text an earlier
 * run extracted may be used, or whether the source is to be read from where it came from.
 *
 * <h2>Identifiers Only</h2>
 * The event carries identifiers rather than the source itself, because everything it describes may
 * have changed by the time the listener runs. The listener reads the current row instead of trusting
 * a copy taken earlier.
 *
 * @param userId   identifier of the account the source belongs to
 * @param sourceId identifier of the source to index
 * @param reread   whether the source is to be read again rather than indexed from the text an
 *                 earlier run extracted
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record SourceIndexRequestedEvent(UUID userId, UUID sourceId, boolean reread) {

    /**
     * Creates the event.
     *
     * @param userId   identifier of the account the source belongs to
     * @param sourceId identifier of the source to index
     * @param reread   whether the source is to be read again
     * @throws NullPointerException if {@code userId} or {@code sourceId} is {@code null}
     */
    public SourceIndexRequestedEvent {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }
}
