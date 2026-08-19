package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
     * Creates the service.
     *
     * @param chatSessionService     service that owns the transcript of a conversation
     * @param sourceDocumentService  service that knows the names of the sources of a notebook
     * @param notebookIndex          retrieval index the passages of a notebook are read from
     * @param groundedChatEngine     engine that generates an answer from the retrieved passages
     * @param chatTranscriptRecorder writer of the finished answer
     */
    public NotebookChatService(final ChatSessionService chatSessionService,
                               final SourceDocumentService sourceDocumentService,
                               final NotebookIndex notebookIndex,
                               final GroundedChatEngine groundedChatEngine,
                               final ChatTranscriptRecorder chatTranscriptRecorder) {
        this.chatSessionService = chatSessionService;
        this.sourceDocumentService = sourceDocumentService;
        this.notebookIndex = notebookIndex;
        this.groundedChatEngine = groundedChatEngine;
        this.chatTranscriptRecorder = chatTranscriptRecorder;
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
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    public ChatTurnContext beginTurn(final UUID userId, final UUID notebookId, final String question) {
        return this.chatSessionService.beginTurn(userId, notebookId, question);
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
        final List<ContextPassage> passages;
        try {
            passages = retrieve(userId, context, handler);
        } catch (final RuntimeException e) {
            LOG.warn("Retrieving passages for notebook {} failed", context.notebookId(), e);
            handler.onFailed(e);
            return;
        }

        this.groundedChatEngine.answer(selection, passages, context.history(), context.question(),
                new AnswerRecorder(userId, context.sessionId(), handler));
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
            NotebookChatService.this.chatTranscriptRecorder.record(this.userId, this.sessionId, answer);
            this.delegate.onCompleted(answer);
        }

        @Override
        public void onFailed(final Throwable error) {
            this.delegate.onFailed(error);
        }
    }
}
