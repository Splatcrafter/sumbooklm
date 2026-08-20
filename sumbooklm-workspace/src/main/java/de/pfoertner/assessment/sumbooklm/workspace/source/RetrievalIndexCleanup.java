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

import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Takes the segments of a deleted source or notebook out of the retrieval index.
 *
 * <h2>After the Commit</h2>
 * The index is not part of the transaction that deletes the rows and cannot be rolled back with it.
 * Removing the segments while that transaction is still open would therefore make a rollback leave a
 * source that exists and can no longer be retrieved from, which is the failure that reports success.
 * Waiting for the commit turns that around: what can be left behind now is a segment whose source is
 * gone, and one of those is already ignored when an answer is put together, because a passage is
 * dropped when the notebook does not list the source it belongs to.
 *
 * <h2>Not on Its Own Thread</h2>
 * The removal runs on the thread that committed, unlike indexing. It is a filtered pass over the
 * store rather than a neural network, and keeping it there means a source that is deleted and added
 * again cannot have the two operations overtake each other.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class RetrievalIndexCleanup {

    /**
     * Log the removals are reported to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(RetrievalIndexCleanup.class);

    /**
     * Retrieval index the segments are taken out of.
     */
    private final NotebookIndex notebookIndex;

    /**
     * Creates the listener.
     *
     * @param notebookIndex retrieval index the segments are taken out of
     */
    public RetrievalIndexCleanup(final NotebookIndex notebookIndex) {
        this.notebookIndex = notebookIndex;
    }

    /**
     * Removes the segments of a deleted source.
     *
     * @param event announcement of the deleted source
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSourceRemoved(final SourceRemovedEvent event) {
        this.notebookIndex.removeSource(event.sourceId());
        LOG.debug("Removed the segments of source {}", event.sourceId());
    }

    /**
     * Removes the segments of every source of a deleted notebook.
     *
     * @param event announcement of the deleted notebook
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotebookRemoved(final NotebookRemovedEvent event) {
        this.notebookIndex.removeNotebook(event.notebookId());
        LOG.debug("Removed the segments of notebook {}", event.notebookId());
    }
}
