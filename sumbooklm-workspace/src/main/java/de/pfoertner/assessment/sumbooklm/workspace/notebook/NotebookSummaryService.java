package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import de.pfoertner.assessment.sumbooklm.ai.summary.NotebookSummaryEngine;
import de.pfoertner.assessment.sumbooklm.ai.summary.SourceExcerpt;
import de.pfoertner.assessment.sumbooklm.ai.summary.SummaryNotWrittenException;
import de.pfoertner.assessment.sumbooklm.domain.workspace.NotebookSummary;
import de.pfoertner.assessment.sumbooklm.workspace.chat.ConcurrentAnswerLimit;
import de.pfoertner.assessment.sumbooklm.workspace.chat.QuestionRateLimit;
import de.pfoertner.assessment.sumbooklm.workspace.chat.QuestionsTooOftenException;
import de.pfoertner.assessment.sumbooklm.workspace.chat.TooManyQuestionsException;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceDocumentService;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceText;
import org.springframework.stereotype.Service;

/**
 * Writes and reads the summary of the sources of one notebook.
 *
 * <h2>Everything, Not What Matches</h2>
 * A question is answered from the passages a retriever selected for it. A summary has no question to
 * select by, so the material is every source of the notebook that was read, cut to a share of the
 * request each; see {@code SummaryBudget}. Retrieval would answer a different question here, namely
 * which parts of the notebook resemble the word summary.
 *
 * <h2>Reading Costs Nothing, Writing Costs a Request</h2>
 * A stored summary is read out of the notebook and needs no model. Writing one is a request to the
 * provider of the user, which is why it happens only when it is asked for and never as a side effect
 * of opening a notebook on the server.
 *
 * <h2>A Summary Is an Asked Question</h2>
 * Both bounds on asking are taken for a summary as well. What they protect is what an account may
 * spend of the installation and of its own provider, and by that measure a summary of forty documents
 * is not cheaper than a question about them. The alternative would be a second, unbounded way to reach
 * a model with the same key.
 *
 * <h2>The Material Is Fingerprinted Before It Is Sent</h2>
 * The set of sources is recorded before the model is asked, and stored with the text that comes back.
 * A source added while the summary was being written therefore makes it stale immediately, which is
 * the honest outcome: the text does not describe that source, and nobody has to know when it arrived
 * to see that.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class NotebookSummaryService {

    /**
     * Service the summary is read from and stored through.
     */
    private final NotebookService notebookService;

    /**
     * Service the material of a summary is read from.
     */
    private final SourceDocumentService sourceDocumentService;

    /**
     * Engine that turns the material into a summary.
     */
    private final NotebookSummaryEngine notebookSummaryEngine;

    /**
     * Bound on how many requests one account may have in flight.
     */
    private final ConcurrentAnswerLimit concurrentAnswerLimit;

    /**
     * Bound on how often one account may ask.
     */
    private final QuestionRateLimit questionRateLimit;

    /**
     * Creates the service.
     *
     * @param notebookService       service the summary is read from and stored through
     * @param sourceDocumentService service the material of a summary is read from
     * @param notebookSummaryEngine engine that turns the material into a summary
     * @param concurrentAnswerLimit bound on how many requests one account may have in flight
     * @param questionRateLimit     bound on how often one account may ask
     */
    public NotebookSummaryService(final NotebookService notebookService,
                                  final SourceDocumentService sourceDocumentService,
                                  final NotebookSummaryEngine notebookSummaryEngine,
                                  final ConcurrentAnswerLimit concurrentAnswerLimit,
                                  final QuestionRateLimit questionRateLimit) {
        this.notebookService = notebookService;
        this.sourceDocumentService = sourceDocumentService;
        this.notebookSummaryEngine = notebookSummaryEngine;
        this.concurrentAnswerLimit = concurrentAnswerLimit;
        this.questionRateLimit = questionRateLimit;
    }

    /**
     * Reads the summary one notebook of an account carries.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to read the summary of
     * @return the stored summary, with an empty text while none has been written
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    public NotebookSummary read(final UUID userId, final UUID notebookId) {
        return this.notebookService.readSummary(userId, notebookId);
    }

    /**
     * Writes the summary of one notebook of an account and stores it in place of the previous one.
     *
     * @param userId      identifier of the account the notebook belongs to
     * @param notebookId  identifier of the notebook to summarise
     * @param selection   model the summary is requested from
     * @param languageTag IETF language tag the summary is to be written in, empty for the language of
     *                    the sources
     * @return the summary as it is now stored
     * @throws NotebookNotFoundException   if the account holds no notebook with that identifier
     * @throws NothingToSummariseException if no source of the notebook has been read
     * @throws TooManyQuestionsException   if the account already has as many requests in flight as it
     *                                     may have
     * @throws QuestionsTooOftenException  if the account has asked as often within the hour as it may
     * @throws SummaryNotWrittenException  if the provider failed or answered with nothing
     */
    public NotebookSummary write(final UUID userId,
                                 final UUID notebookId,
                                 final ModelSelection selection,
                                 final String languageTag) {
        final List<SourceText> texts = this.sourceDocumentService.texts(userId, notebookId);
        if (texts.isEmpty()) {
            throw new NothingToSummariseException(notebookId);
        }
        final String fingerprint = this.sourceDocumentService.fingerprintOfSources(userId, notebookId);

        if (!this.concurrentAnswerLimit.tryAcquire(userId)) {
            throw new TooManyQuestionsException(userId);
        }
        try {
            this.questionRateLimit.record(userId);
            final String text = this.notebookSummaryEngine.summarise(selection, excerpts(texts), languageTag);
            return this.notebookService.storeSummary(userId, notebookId, text, fingerprint);
        } finally {
            this.concurrentAnswerLimit.release(userId);
        }
    }

    /**
     * Turns the sources of a notebook into the form the engine is given them in.
     *
     * @param texts readable sources of the notebook
     * @return one excerpt per source, still carrying the whole text
     */
    private static List<SourceExcerpt> excerpts(final List<SourceText> texts) {
        return texts.stream()
                .map(text -> new SourceExcerpt(text.displayName(), text.text()))
                .toList();
    }
}
