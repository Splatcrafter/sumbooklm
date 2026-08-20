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

import java.util.List;

import de.pfoertner.assessment.sumbooklm.persistence.document.SourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the retrieval index for every stored source once the application is up.
 *
 * <h2>Why It Exists</h2>
 * The vector store keeps its segments in the heap and loses them when the process ends, while the
 * sources they were made from are in the database and outlive it. Without this job a restarted
 * application reports every source as indexed and answers every question from nothing, which is the
 * worst of the possible failures: it looks like it worked.
 *
 * <h2>A Rebuild Is Not a Reading</h2>
 * The rebuild indexes from the text each source was read as, and asks for no source to be read again.
 * A restart is not a reason to believe a page has changed, and reading every source of every account
 * at every start would make a restart an event on every host a user ever named.
 *
 * <h2>Every Source, Not Only the Unfinished Ones</h2>
 * A source that finished successfully is rebuilt just as one that never did. After a restart the two
 * are in the same position, because what distinguished them lived in the store that is gone. The
 * difference is only in what the rebuild costs: a source that was read before is rebuilt from the
 * text stored with it, and only one that was never read is read now, which makes the run a retry for
 * exactly the sources that failed.
 *
 * <h2>After the Application Is Ready</h2>
 * The rebuild starts once the application is serving and runs on the indexing executor, so a large
 * library delays no request. What it does delay is the first answer of a source it has not reached
 * yet, which is the honest behaviour: that source genuinely cannot be retrieved from until it has.
 *
 * <h2>Sequential</h2>
 * The sources are worked through one after another rather than in parallel. Embedding saturates the
 * cores it is given, so a second thread would not finish sooner, and one source at a time keeps a
 * failure to the source that caused it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class IndexRestoreJob {

    /**
     * Log the progress of a rebuild is reported to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(IndexRestoreJob.class);

    /**
     * Service the stored sources are read from.
     */
    private final SourceDocumentService sourceDocumentService;

    /**
     * Pipeline one source is put through.
     */
    private final SourceIngestionPipeline sourceIngestionPipeline;

    /**
     * Creates the job.
     *
     * @param sourceDocumentService   service the stored sources are read from
     * @param sourceIngestionPipeline pipeline one source is put through
     */
    public IndexRestoreJob(final SourceDocumentService sourceDocumentService,
                           final SourceIngestionPipeline sourceIngestionPipeline) {
        this.sourceDocumentService = sourceDocumentService;
        this.sourceIngestionPipeline = sourceIngestionPipeline;
    }

    /**
     * Starts the rebuild once the application has finished starting.
     */
    @Async(SourceIngestionPipeline.INGESTION_EXECUTOR)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        restore();
    }

    /**
     * Puts every stored source through the pipeline again.
     *
     * @return number of sources that are part of the retrieval index afterwards
     */
    public int restore() {
        final List<SourceReference> references = this.sourceDocumentService.references();
        if (references.isEmpty()) {
            return 0;
        }

        LOG.info("Rebuilding the retrieval index for {} sources", references.size());
        int restored = 0;
        for (final SourceReference reference : references) {
            if (this.sourceIngestionPipeline.index(reference.getUserId(), reference.getId(), false)) {
                restored += 1;
            }
        }
        LOG.info("Rebuilt the retrieval index for {} of {} sources", restored, references.size());
        return restored;
    }
}
