package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import de.pfoertner.assessment.sumbooklm.ai.chat.ContextPassage;
import de.pfoertner.assessment.sumbooklm.ai.chat.GroundedChatEngine;
import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import de.pfoertner.assessment.sumbooklm.ai.embedding.SegmentMetadata;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookNotFoundException;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceDocumentService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Answers a question about the sources of one notebook.
 *
 * <h2>Where the Isolation Happens</h2>
 * The retriever is built for one notebook and filters on the identifier every stored segment carries.
 * A question asked in one notebook therefore cannot reach a passage of another, and that is enforced
 * by the retriever rather than by a check on what came back.
 *
 * <h2>Numbering Sources, Not Passages</h2>
 * Several passages of one document share the number the answer cites them under. A citation then
 * names a document the reader can open, which is what a citation is for; distinguishing the paragraph
 * it came from would be a number the reader cannot follow anywhere.
 *
 * <h2>Passages Without a Source</h2>
 * A retrieved passage whose document is no longer listed in the notebook is dropped rather than shown
 * under a placeholder. The two can disagree only while a removal is in flight, and answering out of a
 * document the user has just deleted is worse than answering out of one document less.
 *
 * <h2>How Many at Once</h2>
 * A permit is taken before the question is stored and returned when the answer ends, so an account
 * that already has as many answers in flight as it may have is refused before anything is written.
 * Taking it first is what keeps a refused question out of the transcript.
 *
 * <h2>Asking Anyway</h2>
 * A question that retrieves nothing still reaches the model. The refusal then arrives in the language
 * of the question and in the flow of the conversation, and it arrives through the same channel as
 * every other answer, so the client has one case to render rather than two.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class NotebookChatService {

    /**
     * Name of the executor an answer is generated on. The bean is declared by the composition root,
     * which is what decides how many answers may be generated at the same time.
     */
    public static final String CHAT_EXECUTOR = "chatAnswerExecutor";

    /**
     * Log the failures of a run are reported to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NotebookChatService.class);

    /**
     * Service that owns the transcript of a conversation.
     */
    private final ChatSessionService chatSessionService;

    /**
     * Service that knows the names the sources of a notebook are listed under.
     */
    private final SourceDocumentService sourceDocumentService;

    /**
     * Retrieval index the passages of a notebook are read from.
     */
    private final NotebookIndex notebookIndex;

    /**
     * Engine that generates an answer from the retrieved passages.
     */
    private final GroundedChatEngine groundedChatEngine;

    /**
     * Writer of the finished answer.
     */
    private final ChatTranscriptRecorder chatTranscriptRecorder;

    /**
     * Bound on how many answers one account may have in flight.
     */
    private final ConcurrentAnswerLimit concurrentAnswerLimit;

    /**
     * Creates the service.
     *
     * @param chatSessionService     service that owns the transcript of a conversation
     * @param sourceDocumentService  service that knows the names of the sources of a notebook
     * @param notebookIndex          retrieval index the passages of a notebook are read from
     * @param groundedChatEngine     engine that generates an answer from the retrieved passages
     * @param chatTranscriptRecorder writer of the finished answer
     * @param concurrentAnswerLimit  bound on how many answers one account may have in flight
     */
    public NotebookChatService(final ChatSessionService chatSessionService,
                               final SourceDocumentService sourceDocumentService,
                               final NotebookIndex notebookIndex,
                               final GroundedChatEngine groundedChatEngine,
                               final ChatTranscriptRecorder chatTranscriptRecorder,
                               final ConcurrentAnswerLimit concurrentAnswerLimit) {
        this.chatSessionService = chatSessionService;
        this.sourceDocumentService = sourceDocumentService;
        this.notebookIndex = notebookIndex;
        this.groundedChatEngine = groundedChatEngine;
        this.chatTranscriptRecorder = chatTranscriptRecorder;
        this.concurrentAnswerLimit = concurrentAnswerLimit;
    }

    /**
     * Reads the conversation of one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to read the conversation of
     * @return the conversation, or empty if nothing has been asked in this notebook yet
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    public Optional<ChatSession> conversation(final UUID userId, final UUID notebookId) {
        return this.chatSessionService.conversation(userId, notebookId);
    }

    /**
     * Stores a question and reads what generating its answer needs.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the question was asked in
     * @param question   question that was asked
     * @return the conversation as it was before the question, together with its identifier
     * @throws NotebookNotFoundException  if the account holds no notebook with that identifier
     * @throws TooManyQuestionsException  if the account already has as many answers in flight as it
     *                                    may have
     */
    public ChatTurnContext beginTurn(final UUID userId, final UUID notebookId, final String question) {
        if (!this.concurrentAnswerLimit.tryAcquire(userId)) {
            throw new TooManyQuestionsException(userId);
        }
        try {
            return this.chatSessionService.beginTurn(userId, notebookId, question);
        } catch (final RuntimeException e) {
            this.concurrentAnswerLimit.release(userId);
            throw e;
        }
    }

    /**
     * Gives up a turn whose answer was never started.
     *
     * <p>The permit is taken when the question is stored and returned when the answer ends. The one
     * moment in between that has no ending is the hand-off to the executor, which is where this is
     * called from.
     *
     * @param userId identifier of the account the turn belongs to
     */
    public void abandonTurn(final UUID userId) {
        this.concurrentAnswerLimit.release(userId);
    }

    /**
     * Generates the answer of an opened turn and reports it as it arrives.
     *
     * @param userId    identifier of the account the notebook belongs to
     * @param context   values the opened turn produced
     * @param selection model the answer is requested from
     * @param handler   receiver of the sources, of the parts and of the ending of the answer
     */
    @Async(CHAT_EXECUTOR)
    public void answer(final UUID userId,
                       final ChatTurnContext context,
                       final ModelSelection selection,
                       final ChatStreamHandler handler) {
        final ChatStreamHandler ending = new AnswerRecorder(userId, context.sessionId(), handler);
        final List<ContextPassage> passages;
        try {
            passages = retrieve(userId, context, ending);
        } catch (final RuntimeException e) {
            LOG.warn("Retrieving passages for notebook {} failed", context.notebookId(), e);
            ending.onFailed(e);
            return;
        }

        this.groundedChatEngine.answer(selection, passages, context.history(), context.question(), ending);
    }

    /**
     * Reads the passages one question may be answered from and reports their sources.
     *
     * @param userId  identifier of the account the notebook belongs to
     * @param context values the opened turn produced
     * @param handler receiver the sources are reported to
     * @return the passages, numbered by the source they belong to
     */
    private List<ContextPassage> retrieve(final UUID userId,
                                          final ChatTurnContext context,
                                          final ChatStreamHandler handler) {
        final Map<UUID, String> names = this.sourceDocumentService.displayNames(userId, context.notebookId());
        final List<Content> contents = this.notebookIndex.retrieverFor(context.notebookId())
                .retrieve(Query.from(context.question()));

        final Map<UUID, Integer> numbers = new LinkedHashMap<>();
        final List<ContextPassage> passages = new ArrayList<>(contents.size());
        final List<RetrievedSource> sources = new ArrayList<>();

        for (final Content content : contents) {
            final TextSegment segment = content.textSegment();
            final UUID sourceId = segment.metadata().getUUID(SegmentMetadata.SOURCE_DOCUMENT_ID);
            final String name = sourceId == null ? null : names.get(sourceId);
            if (name == null) {
                continue;
            }
            Integer number = numbers.get(sourceId);
            if (number == null) {
                number = numbers.size() + 1;
                numbers.put(sourceId, number);
                sources.add(new RetrievedSource(number, sourceId, name));
            }
            passages.add(new ContextPassage(number, name, segment.text()));
        }

        handler.onSources(List.copyOf(sources));
        return passages;
    }

    /**
     * Hands a finished answer to the transcript before it is reported as finished.
     *
     * <h2>Order</h2>
     * The write is started before the client is told that the answer is complete, and it is started
     * rather than awaited. A client that reloads immediately afterwards can therefore still be ahead
     * of the transcript, which is the price of not making the reader wait for a transaction.
     *
     * <h2>Exactly One Ending</h2>
     * The permit of the account is returned by whichever ending arrives, and by only one of them. A
     * provider that reported both would otherwise return a permit it does not hold, and the account
     * would end up allowed one more answer than the limit says.
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    private final class AnswerRecorder implements ChatStreamHandler {

        /**
         * Identifier of the account the conversation belongs to.
         */
        private final UUID userId;

        /**
         * Identifier of the conversation the answer belongs to.
         */
        private final UUID sessionId;

        /**
         * Receiver the calls are passed on to.
         */
        private final ChatStreamHandler delegate;

        /**
         * Whether an ending has already been handled.
         */
        private final AtomicBoolean ended = new AtomicBoolean();

        /**
         * Creates the receiver.
         *
         * @param userId    identifier of the account the conversation belongs to
         * @param sessionId identifier of the conversation the answer belongs to
         * @param delegate  receiver the calls are passed on to
         */
        private AnswerRecorder(final UUID userId, final UUID sessionId, final ChatStreamHandler delegate) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.delegate = delegate;
        }

        @Override
        public void onSources(final List<RetrievedSource> sources) {
            this.delegate.onSources(sources);
        }

        @Override
        public void onToken(final String token) {
            this.delegate.onToken(token);
        }

        @Override
        public void onCompleted(final String answer) {
            if (!this.ended.compareAndSet(false, true)) {
                return;
            }
            NotebookChatService.this.concurrentAnswerLimit.release(this.userId);
            NotebookChatService.this.chatTranscriptRecorder.record(this.userId, this.sessionId, answer);
            this.delegate.onCompleted(answer);
        }

        @Override
        public void onFailed(final Throwable error) {
            if (!this.ended.compareAndSet(false, true)) {
                return;
            }
            NotebookChatService.this.concurrentAnswerLimit.release(this.userId);
            this.delegate.onFailed(error);
        }
    }
}
