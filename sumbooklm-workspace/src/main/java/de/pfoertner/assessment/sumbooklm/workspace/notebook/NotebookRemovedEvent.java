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

package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.util.Objects;
import java.util.UUID;

/**
 * Announces that a notebook was deleted and the segments of its sources are no longer wanted.
 *
 * <h2>Why an Event</h2>
 * As with a single source, the retrieval index has no transaction of its own. Announcing the deletion
 * and removing the segments after the commit is what keeps the index from forgetting something that a
 * rollback would have kept.
 *
 * <h2>One Event for Every Source</h2>
 * A notebook is deleted without its sources being read, so there is nothing to announce them one by
 * one with. The listener removes by notebook instead, which the index supports for exactly this
 * reason.
 *
 * @param notebookId identifier of the deleted notebook
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record NotebookRemovedEvent(UUID notebookId) {

    /**
     * Creates the event.
     *
     * @param notebookId identifier of the deleted notebook
     * @throws NullPointerException if {@code notebookId} is {@code null}
     */
    public NotebookRemovedEvent {
        Objects.requireNonNull(notebookId, "notebookId must not be null");
    }
}
