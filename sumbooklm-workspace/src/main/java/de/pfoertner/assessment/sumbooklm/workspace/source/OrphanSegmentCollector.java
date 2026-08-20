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

import java.util.concurrent.TimeUnit;

import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes the segments of sources that no longer exist.
 *
 * <h2>How They Come About</h2>
 * The segments of a removed source are removed after the transaction that removed it has committed,
 * which is deliberate: a removal that happened before the commit would take the segments of a source
 * whose deletion is still able to roll back. The price of that order is the opposite failure, a commit
 * whose removal does not follow, and nothing is left afterwards that knows those segments should go.
 *
 * <h2>Why a Schedule Is Right Here</h2>
 * A schedule was deliberately not taken for reading web sources, because a run of that kind reaches
 * hosts nobody asked it to reach and can change what a Sumbook answers. This pass does neither. It
 * touches nothing outside the process, and it can only remove what no source claims, so the worst a
 * run does is nothing at all.
 *
 * <h2>Rarely, Because It Is Not Urgent</h2>
 * An orphaned segment is invisible to an answer: a passage is dropped when the notebook does not list
 * the source it came from. What it costs is memory and a comparison per search, which is a reason to
 * collect it eventually rather than promptly, so the pass runs hourly and starts once the first
 * indexing after a start has had time to finish.
 *
 * <h2>Not at Startup</h2>
 * The rebuild after a start needs no collection: the store is empty at that point, so it cannot hold
 * a segment of anything. Orphans accumulate while the application runs, and that is when they are
 * looked for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class OrphanSegmentCollector {

    /**
     * Time between the start of the application and the first pass.
     */
    private static final long INITIAL_DELAY_MINUTES = 10;

    /**
     * Time between the end of one pass and the start of the next.
     */
    private static final long INTERVAL_MINUTES = 60;

    /**
     * Log a pass reports to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(OrphanSegmentCollector.class);

    /**
     * Service the sources that exist are read from.
     */
    private final SourceDocumentService sourceDocumentService;

    /**
     * Retrieval index the segments are removed from.
     */
    private final NotebookIndex notebookIndex;

    /**
     * Creates the collector.
     *
     * @param sourceDocumentService service the sources that exist are read from
     * @param notebookIndex         retrieval index the segments are removed from
     */
    public OrphanSegmentCollector(final SourceDocumentService sourceDocumentService,
                                  final NotebookIndex notebookIndex) {
        this.sourceDocumentService = sourceDocumentService;
        this.notebookIndex = notebookIndex;
    }

    /**
     * Runs one pass over the retrieval index.
     *
     * <p>The sources are handed over as a method reference rather than as a list, because the index
     * reads them while it holds off the runs that write, which is what keeps the pass from removing a
     * source that was added while it was working.
     */
    @Scheduled(initialDelay = INITIAL_DELAY_MINUTES,
            fixedDelay = INTERVAL_MINUTES,
            timeUnit = TimeUnit.MINUTES)
    public void collect() {
        LOG.debug("Collecting the segments of sources that no longer exist");
        this.notebookIndex.collectOrphanedSegments(this.sourceDocumentService::sourceIds);
    }
}
