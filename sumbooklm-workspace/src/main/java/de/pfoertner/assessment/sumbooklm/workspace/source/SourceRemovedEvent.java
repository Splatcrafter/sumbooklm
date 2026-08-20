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
 * Announces that a source was deleted and its segments are no longer wanted.
 *
 * <h2>Why an Event</h2>
 * The retrieval index has no transaction of its own, so a removal performed while the deleting
 * transaction is still open is a change that a rollback cannot take back. Announcing the deletion and
 * removing the segments after the commit means the index is only ever asked to forget something that
 * is actually gone.
 *
 * @param sourceId identifier of the deleted source
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record SourceRemovedEvent(UUID sourceId) {

    /**
     * Creates the event.
     *
     * @param sourceId identifier of the deleted source
     * @throws NullPointerException if {@code sourceId} is {@code null}
     */
    public SourceRemovedEvent {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }
}
