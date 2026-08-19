package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import de.pfoertner.assessment.sumbooklm.ingestion.chunking.TextChunker;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.ExtractedContent;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.FileTextExtractor;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.TextExtractionException;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.WebPageTextExtractor;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a stored source into segments the retrieval index can answer from.
 *
 * <h2>When It Runs</h2>
 * A run starts after the transaction that requested it has committed, and it runs on its own
 * executor. Both matter: before the commit the row would not be visible, and on the request thread
 * the user would wait for a neural network to finish before learning that their upload arrived.
 *
 * <h2>Reading Once, Unless Asked</h2>
 * A source that was read successfully before is not read again by itself. The text of that reading is
 * stored with the source, and a run that finds it there goes straight to splitting and embedding,
 * which is what lets the whole index be rebuilt without a parser and without reaching a single
 * foreign host.
 *
 * A run may be asked to read anyway, which is what a user asks for when a page has changed or when a
 * source failed. The stored text is then ignored rather than deleted, so a reading that fails leaves
 * the source answering with what it said before.
 *
 * <h2>Order of Work</h2>
 * The source is marked as being indexed, its text is extracted, the text is cut into segments, the
 * segments are embedded under the identifiers of their notebook and their source, and the result is
 * written back. Marking first is what lets the interface show progress instead of an upload that
 * appears to do nothing.
 *
 * <h2>Failures End on the Source</h2>
 * A source that cannot be read is recorded as failed rather than retried or dropped. The user is the
 * only one who can act on it, by removing it, by asking for it again or by adding it in another form,
 * so the state has to reach them.
 *
 * <h2>Why It Failed</h2>
 * The cause a source is recorded under comes from the extractor that raised it, because only the
 * extractor knows whether an address was refused, unreachable or merely empty. Anything that fails
 * outside an extractor is recorded as unexpected rather than guessed at, which keeps the causes
 * meaning what they say.
 *
 * <h2>Unexpected Is a Defect, Not an Outcome</h2>
 * The unexpected cause tells the user that it did not work and nothing they can act on, and it is
 * kept that way on purpose, because the alternative is showing them what a stack trace says. What
 * can be improved about it is not the wording but how often it is reached, so a run that reaches it
 * reports at error level, with the trace, the notebook, and how often this instance has had to do so
 * since it started. The count is what turns a line into a rate: one occurrence says that something
 * went wrong once, and the twentieth says that something is wrong.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class SourceIngestionPipeline {

    /**
     * Name of the executor indexing runs on. The bean is declared by the composition root, which is
     * what decides how much of the machine background work may occupy.
     */
    public static final String INGESTION_EXECUTOR = "sourceIngestionExecutor";

    /**
     * Log the progress and the failures of a run are reported to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SourceIngestionPipeline.class);

    /**
     * Number of runs that failed for a reason no extractor named, counted since this instance started.
     */
    private final AtomicLong unexpectedFailures = new AtomicLong();

    /**
     * Service that records how far a source has come.
     */
    private final SourceDocumentService sourceDocumentService;

    /**
     * Reader of uploaded files.
     */
    private final FileTextExtractor fileTextExtractor;

    /**
     * Reader of web pages.
     */
    private final WebPageTextExtractor webPageTextExtractor;

    /**
     * Splitter that cuts extracted text into segments.
     */
    private final TextChunker textChunker;

    /**
     * Retrieval index the segments are written to.
     */
    private final NotebookIndex notebookIndex;

    /**
     * Creates the pipeline.
     *
     * @param sourceDocumentService service that records how far a source has come
     * @param fileTextExtractor     reader of uploaded files
     * @param webPageTextExtractor  reader of web pages
     * @param textChunker           splitter that cuts extracted text into segments
     * @param notebookIndex         retrieval index the segments are written to
     */
    public SourceIngestionPipeline(final SourceDocumentService sourceDocumentService,
                                   final FileTextExtractor fileTextExtractor,
                                   final WebPageTextExtractor webPageTextExtractor,
                                   final TextChunker textChunker,
                                   final NotebookIndex notebookIndex) {
        this.sourceDocumentService = sourceDocumentService;
        this.fileTextExtractor = fileTextExtractor;
        this.webPageTextExtractor = webPageTextExtractor;
        this.textChunker = textChunker;
        this.notebookIndex = notebookIndex;
    }

    /**
     * Indexes a source whose indexing was requested.
     *
     * @param event announcement of the source waiting to be indexed
     */
    @Async(INGESTION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIndexRequested(final SourceIndexRequestedEvent event) {
        index(event.userId(), event.sourceId(), event.reread());
    }

    /**
     * Runs one source through extraction, splitting and embedding.
     *
     * @param userId   identifier of the account the source belongs to
     * @param sourceId identifier of the source to index
     * @param reread   whether the source is to be read again rather than indexed from the text an
     *                 earlier run extracted
     * @return {@code true} if the source is now part of the retrieval index, {@code false} if it was
     *         removed in the meantime or could not be read
     */
    public boolean index(final UUID userId, final UUID sourceId, final boolean reread) {
        final IngestionInput input;
        try {
            input = this.sourceDocumentService.beginIndexing(userId, sourceId);
        } catch (final SourceNotFoundException e) {
            LOG.debug("Source {} was removed before it could be indexed", sourceId);
            return false;
        }

        final String stored = input.extractedText();
        final boolean fromStore = !reread && stored != null && !stored.isBlank();
        try {
            final ExtractedContent content =
                    fromStore ? new ExtractedContent("", stored) : extract(input);
            final List<TextSegment> segments = this.textChunker.chunk(content.text());
            final int tokenCount = this.notebookIndex.index(input.notebookId(), sourceId, segments);
            this.sourceDocumentService.completeIndexing(userId, sourceId,
                    displayName(input, content), tokenCount, content.text(), !fromStore);
            LOG.debug("Indexed source {} as {} segments and {} tokens", sourceId, segments.size(), tokenCount);
            return true;
        } catch (final SourceNotFoundException e) {
            LOG.debug("Source {} was removed while it was being indexed", sourceId);
            return false;
        } catch (final TextExtractionException e) {
            LOG.warn("Indexing source {} failed as {}: {}", sourceId, e.failure(), e.getMessage());
            recordFailure(userId, sourceId, e.failure());
            return false;
        } catch (final RuntimeException e) {
            LOG.error("Indexing source {} of notebook {} failed for a reason no extractor named, "
                            + "unexpected failure number {} since this instance started",
                    sourceId, input.notebookId(), this.unexpectedFailures.incrementAndGet(), e);
            recordFailure(userId, sourceId, DocumentFailure.UNEXPECTED);
            return false;
        }
    }

    /**
     * Reads the text of a source.
     *
     * @param input values the run works with
     * @return the extracted text and the title the content carries
     * @throws TextExtractionException if the source cannot be read
     */
    private ExtractedContent extract(final IngestionInput input) {
        if (input.kind() == SourceKind.WEB) {
            return this.webPageTextExtractor.extract(input.origin());
        }
        final byte[] content = input.content();
        if (content == null) {
            throw new TextExtractionException(
                    DocumentFailure.EMPTY, "The uploaded file " + input.origin() + " holds no bytes");
        }
        return this.fileTextExtractor.extract(content, input.origin());
    }

    /**
     * Decides which name a source is listed under once its content has been read.
     *
     * <p>An upload keeps the name it was uploaded under, which is what the user recognises it by. A
     * page is listed under the title it carries, because its address is a location rather than a
     * name, and keeps its address when it carries no title.
     *
     * @param input   values the run works with
     * @param content text and title that were read
     * @return the name the source is listed under from now on
     */
    private static String displayName(final IngestionInput input, final ExtractedContent content) {
        if (input.kind() == SourceKind.WEB && !content.title().isBlank()) {
            return content.title().strip();
        }
        return input.displayName();
    }

    /**
     * Records that a source could not be indexed.
     *
     * @param userId   identifier of the account the source belongs to
     * @param sourceId identifier of the source the run failed on
     * @param cause    reason the source could not be indexed
     */
    private void recordFailure(final UUID userId, final UUID sourceId, final DocumentFailure cause) {
        try {
            this.sourceDocumentService.failIndexing(userId, sourceId, cause);
        } catch (final SourceNotFoundException e) {
            LOG.debug("Source {} was removed before its failure could be recorded", sourceId);
        }
    }
}
