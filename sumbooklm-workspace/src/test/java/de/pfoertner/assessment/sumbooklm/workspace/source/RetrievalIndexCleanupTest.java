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

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookRemovedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises what clears the retrieval index of what no longer exists.
 *
 * <h2>Two Ways Something Disappears</h2>
 * A source can be removed, and so can the notebook holding it, and the index is a structure in
 * memory that neither removal reaches on its own. What matters is when the clearing happens: after
 * the transaction has committed rather than during it, because a removal that is rolled back would
 * otherwise leave a notebook whose sources exist and whose passages are gone. The collector that
 * runs on a schedule is the second net under both.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class RetrievalIndexCleanupTest {

    /**
     * Index the segments are removed from.
     */
    private final NotebookIndex notebookIndex = mock(NotebookIndex.class);

    /**
     * Listener under test.
     */
    private final RetrievalIndexCleanup cleanup = new RetrievalIndexCleanup(this.notebookIndex);

    /**
     * Creates the test class.
     */
    RetrievalIndexCleanupTest() {
    }

    /**
     * Verifies that a removed source loses its segments and nothing else.
     */
    @Test
    void aRemovedSourceLosesItsSegments() {
        final UUID sourceId = UUID.randomUUID();

        this.cleanup.onSourceRemoved(new SourceRemovedEvent(sourceId));

        verify(this.notebookIndex).removeSource(sourceId);
        verifyNoMoreInteractions(this.notebookIndex);
    }

    /**
     * Verifies that a removed notebook loses the segments of all of its sources at once, rather than
     * one removal per source.
     */
    @Test
    void aRemovedNotebookLosesEverythingAtOnce() {
        final UUID notebookId = UUID.randomUUID();

        this.cleanup.onNotebookRemoved(new NotebookRemovedEvent(notebookId));

        verify(this.notebookIndex).removeNotebook(notebookId);
        verifyNoMoreInteractions(this.notebookIndex);
    }

    /**
     * Verifies that both removals happen after the transaction committed, because a rolled back
     * removal must not clear an index of rows that still exist.
     *
     * @throws NoSuchMethodException if either listener was renamed
     */
    @Test
    void bothRemovalsHappenAfterTheCommit() throws NoSuchMethodException {
        final TransactionalEventListener onSource = RetrievalIndexCleanup.class
                .getMethod("onSourceRemoved", SourceRemovedEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        final TransactionalEventListener onNotebook = RetrievalIndexCleanup.class
                .getMethod("onNotebookRemoved", NotebookRemovedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(onSource).isNotNull();
        assertThat(onSource.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(onNotebook).isNotNull();
        assertThat(onNotebook.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    /**
     * Verifies that the collector hands the index the sources that still exist, so that everything
     * else can be removed in one pass.
     */
    @Test
    void theCollectorHandsOverWhatStillExists() {
        final SourceDocumentService sourceDocumentService = mock(SourceDocumentService.class);
        final List<UUID> existing = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(sourceDocumentService.sourceIds()).thenReturn(existing);

        new OrphanSegmentCollector(sourceDocumentService, this.notebookIndex).collect();

        @SuppressWarnings("unchecked") final ArgumentCaptor<Supplier<Collection<UUID>>> sources =
                ArgumentCaptor.forClass(Supplier.class);
        verify(this.notebookIndex).collectOrphanedSegments(sources.capture());
        assertThat(sources.getValue().get()).isEqualTo(existing);
    }

    /**
     * Verifies that the collector runs on a schedule rather than on demand, because the removals
     * above are what ordinarily clears the index and the collector is what catches what they missed.
     *
     * @throws NoSuchMethodException if the collecting method was renamed
     */
    @Test
    void theCollectorRunsOnASchedule() throws NoSuchMethodException {
        final Scheduled schedule = OrphanSegmentCollector.class
                .getMethod("collect")
                .getAnnotation(Scheduled.class);

        assertThat(schedule).isNotNull();
        assertThat(schedule.timeUnit()).isEqualTo(TimeUnit.MINUTES);
        assertThat(schedule.fixedDelay()).isPositive();
        assertThat(schedule.initialDelay()).isPositive();
    }
}
