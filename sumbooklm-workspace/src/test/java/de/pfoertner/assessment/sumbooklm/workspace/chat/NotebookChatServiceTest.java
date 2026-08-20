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

package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.AnswerCancellation;
import de.pfoertner.assessment.sumbooklm.ai.chat.ChatProvider;
import de.pfoertner.assessment.sumbooklm.ai.chat.ContextPassage;
import de.pfoertner.assessment.sumbooklm.ai.chat.GroundedChatEngine;
import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import de.pfoertner.assessment.sumbooklm.ai.embedding.SegmentMetadata;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookNotFoundException;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceDocumentService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises how a question is turned into an answer.
 *
 * <h2>What the Cases Are About</h2>
 * Three things decide whether this holds together. The first is the permit: an account may have only
 * so many answers being written at once, and every way a turn can fail before it starts has to give
 * the permit back, or an account locks itself out by asking questions that failed. The second is the
 * numbering of the sources, because the answer cites numbers and a passage of a source the notebook
 * no longer lists must not shift them. The third is that an answer nobody could ground ends as a
 * finished answer rather than as a failure, because the reader asked a question the sources do not
 * cover, which is not an error.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookChatServiceTest {

    /**
     * Selection every case answers under, which no case reaches a provider with.
     */
    private static final ModelSelection SELECTION =
            new ModelSelection(ChatProvider.OPENAI, "gpt-4o-mini", "sk-secret", "https://proxy.test/v1");

    /**
     * Store of the conversations.
     */
    private ChatSessionService chatSessionService;

    /**
     * Store of the sources, which the names of the citations come from.
     */
    private SourceDocumentService sourceDocumentService;

    /**
     * Index the passages are retrieved from.
     */
    private NotebookIndex notebookIndex;

    /**
     * Engine the answer is written by.
     */
    private GroundedChatEngine groundedChatEngine;

    /**
     * Writer of the finished answer into the transcript.
     */
    private ChatTranscriptRecorder chatTranscriptRecorder;

    /**
     * Bound on how many answers one account may have in flight.
     */
    private ConcurrentAnswerLimit concurrentAnswerLimit;

    /**
     * Bound on how often one account may ask.
     */
    private QuestionRateLimit questionRateLimit;

    /**
     * Register an answer is stopped through.
     */
    private RunningAnswers runningAnswers;

    /**
     * Service under test.
     */
    private NotebookChatService service;

    /**
     * Reader of the cases.
     */
    private RecordingHandler handler;

    /**
     * Account of the cases.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Notebook of the cases.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Conversation of the cases.
     */
    private final UUID sessionId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    NotebookChatServiceTest() {
    }

    /**
     * Builds the service and everything it answers through.
     */
    @BeforeEach
    void setUp() {
        this.chatSessionService = mock(ChatSessionService.class);
        this.sourceDocumentService = mock(SourceDocumentService.class);
        this.notebookIndex = mock(NotebookIndex.class);
        this.groundedChatEngine = mock(GroundedChatEngine.class);
        this.chatTranscriptRecorder = mock(ChatTranscriptRecorder.class);
        this.concurrentAnswerLimit = mock(ConcurrentAnswerLimit.class);
        this.questionRateLimit = mock(QuestionRateLimit.class);
        this.runningAnswers = mock(RunningAnswers.class);
        this.handler = new RecordingHandler();
        this.service = new NotebookChatService(this.chatSessionService, this.sourceDocumentService,
                this.notebookIndex, this.groundedChatEngine, this.chatTranscriptRecorder,
                this.concurrentAnswerLimit, this.questionRateLimit, this.runningAnswers);

        when(this.concurrentAnswerLimit.tryAcquire(this.userId)).thenReturn(true);
    }

    /**
     * Verifies that a turn takes a permit, is counted against the rate and is then written into the
     * conversation, in that order.
     */
    @Test
    void aTurnTakesAPermitAndIsCounted() {
        final ChatTurnContext context =
                new ChatTurnContext(this.sessionId, this.notebookId, "What is entropy?", List.of());
        when(this.chatSessionService.beginTurn(this.userId, this.notebookId, this.sessionId, "What is entropy?"))
                .thenReturn(context);

        assertThat(this.service.beginTurn(this.userId, this.notebookId, this.sessionId, "What is entropy?"))
                .isSameAs(context);
        verify(this.questionRateLimit).record(this.userId);
        verify(this.concurrentAnswerLimit, never()).release(this.userId);
    }

    /**
     * Verifies that an account which already has as many answers as it may have is refused without
     * being counted against the rate, because a refused question is not a question asked.
     */
    @Test
    void anAccountWithoutAPermitIsRefused() {
        when(this.concurrentAnswerLimit.tryAcquire(this.userId)).thenReturn(false);

        assertThatThrownBy(() -> this.service.beginTurn(
                this.userId, this.notebookId, this.sessionId, "What is entropy?"))
                .isInstanceOf(TooManyQuestionsException.class);
        verify(this.questionRateLimit, never()).record(any());
        verify(this.concurrentAnswerLimit, never()).release(any());
    }

    /**
     * Verifies that a turn refused by the rate gives its permit back, so that an account refused for
     * asking too often does not also lose the permits it holds.
     */
    @Test
    void aTurnRefusedByTheRateGivesItsPermitBack() {
        doThrow(new QuestionsTooOftenException(this.userId, Duration.ofSeconds(60)))
                .when(this.questionRateLimit).record(this.userId);

        assertThatThrownBy(() -> this.service.beginTurn(
                this.userId, this.notebookId, this.sessionId, "What is entropy?"))
                .isInstanceOf(QuestionsTooOftenException.class);
        verify(this.concurrentAnswerLimit).release(this.userId);
    }

    /**
     * Verifies that a turn in a notebook that does not exist gives its permit back as well, which is
     * the case that would otherwise be spent by a client asking in a removed notebook.
     */
    @Test
    void aTurnInAMissingNotebookGivesItsPermitBack() {
        when(this.chatSessionService.beginTurn(any(), any(), any(), anyString()))
                .thenThrow(new NotebookNotFoundException(this.notebookId));

        assertThatThrownBy(() -> this.service.beginTurn(
                this.userId, this.notebookId, this.sessionId, "What is entropy?"))
                .isInstanceOf(NotebookNotFoundException.class);
        verify(this.concurrentAnswerLimit).release(this.userId);
    }

    /**
     * Verifies that a turn abandoned before it was answered gives its permit back, which is what a
     * reader disconnecting between the question and the stream produces.
     */
    @Test
    void anAbandonedTurnGivesItsPermitBack() {
        this.service.abandonTurn(this.userId);

        verify(this.concurrentAnswerLimit).release(this.userId);
    }

    /**
     * Verifies that the passages of an answer are numbered by the source they came from, that a
     * source quoted twice keeps one number, and that the sources are announced before the answer.
     */
    @Test
    void thePassagesAreNumberedByTheirSource() {
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        retrieve(Map.of(first, "First.pdf", second, "Second.pdf"),
                passage(first, "Entropy never decreases."),
                passage(second, "Heat flows from hot to cold."),
                passage(first, "In an isolated system."));

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        assertThat(this.handler.sources()).extracting(RetrievedSource::number).containsExactly(1, 2);
        assertThat(this.handler.sources()).extracting(RetrievedSource::sourceDocumentId)
                .containsExactly(first, second);
        assertThat(capturedPassages()).extracting(ContextPassage::number).containsExactly(1, 2, 1);
        assertThat(capturedPassages()).extracting(ContextPassage::displayName)
                .containsExactly("First.pdf", "Second.pdf", "First.pdf");
    }

    /**
     * Verifies that a passage of a source the notebook no longer lists is left out without shifting
     * the numbers of the others, which is what a source removed while an answer runs produces.
     */
    @Test
    void aPassageOfAnUnlistedSourceIsLeftOut() {
        final UUID listed = UUID.randomUUID();
        retrieve(Map.of(listed, "Listed.pdf"),
                passage(UUID.randomUUID(), "From a source that is gone."),
                passage(listed, "Entropy never decreases."));

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        assertThat(this.handler.sources()).hasSize(1);
        assertThat(capturedPassages()).extracting(ContextPassage::text)
                .containsExactly("Entropy never decreases.");
    }

    /**
     * Verifies that a passage carrying no source at all is left out, rather than being cited under a
     * number that resolves to nothing.
     */
    @Test
    void aPassageWithoutASourceIsLeftOut() {
        final ContentRetriever retriever = mock(ContentRetriever.class);
        when(this.sourceDocumentService.displayNames(this.userId, this.notebookId)).thenReturn(Map.of());
        when(this.notebookIndex.retrieverFor(this.notebookId)).thenReturn(retriever);
        when(retriever.retrieve(any(Query.class)))
                .thenReturn(List.of(Content.from(TextSegment.from("Orphan passage."))));

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        assertThat(this.handler.completed()).isEmpty();
        verify(this.groundedChatEngine, never())
                .answer(any(), any(), any(), anyString(), any(), any());
    }

    /**
     * Verifies that a question nothing could be retrieved for is finished as an empty answer rather
     * than reported as a failure, because the sources simply do not cover it.
     */
    @Test
    void aQuestionWithoutPassagesIsFinishedEmpty() {
        retrieve(Map.of());

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        assertThat(this.handler.completed()).isEmpty();
        assertThat(this.handler.failure()).isNull();
        verify(this.concurrentAnswerLimit).release(this.userId);
        verify(this.chatTranscriptRecorder, never()).record(any(), any(), anyString());
    }

    /**
     * Verifies that a retrieval which fails ends the answer as a failure and gives the permit back,
     * so that a broken index does not cost an account its permits.
     */
    @Test
    void aRetrievalThatFailsEndsTheAnswer() {
        when(this.sourceDocumentService.displayNames(this.userId, this.notebookId))
                .thenThrow(new IllegalStateException("the index is gone"));

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        assertThat(this.handler.failure()).isInstanceOf(IllegalStateException.class);
        verify(this.concurrentAnswerLimit).release(this.userId);
        verify(this.runningAnswers).unregister(eq(this.sessionId), any());
    }

    /**
     * Verifies that a finished answer is written into the transcript and that the permit is given
     * back, which is the ordinary ending of a turn.
     */
    @Test
    void aFinishedAnswerIsWrittenIntoTheTranscript() {
        final UUID source = UUID.randomUUID();
        retrieve(Map.of(source, "Source.pdf"), passage(source, "Entropy never decreases."));
        answerWith("Entropy never decreases [1](#source-1).");

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        verify(this.chatTranscriptRecorder).record(
                this.userId, this.sessionId, "Entropy never decreases [1](#source-1).");
        verify(this.concurrentAnswerLimit).release(this.userId);
        assertThat(this.handler.completed()).isEqualTo("Entropy never decreases [1](#source-1).");
    }

    /**
     * Verifies that an answer which produced nothing is not written into the transcript, because a
     * conversation would otherwise carry an empty turn nobody can read.
     */
    @Test
    void anEmptyAnswerIsNotWrittenIntoTheTranscript() {
        final UUID source = UUID.randomUUID();
        retrieve(Map.of(source, "Source.pdf"), passage(source, "Entropy never decreases."));
        answerWith("   ");

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        verify(this.chatTranscriptRecorder, never()).record(any(), any(), anyString());
        verify(this.concurrentAnswerLimit).release(this.userId);
    }

    /**
     * Verifies that an answer which ends twice gives its permit back once, so that a provider
     * reporting both an error and a completion cannot hand an account a permit it never took.
     */
    @Test
    void anAnswerThatEndsTwiceGivesOnePermitBack() {
        final UUID source = UUID.randomUUID();
        retrieve(Map.of(source, "Source.pdf"), passage(source, "Entropy never decreases."));
        doAnswer(invocation -> {
            final ChatStreamHandler stream = invocation.getArgument(4, ChatStreamHandler.class);
            stream.onCompleted("An answer.");
            stream.onFailed(new IllegalStateException("too late"));
            return null;
        }).when(this.groundedChatEngine).answer(any(), any(), any(), anyString(), any(), any());

        this.service.answer(this.userId, context(), SELECTION, this.handler);

        verify(this.concurrentAnswerLimit).release(this.userId);
        assertThat(this.handler.failure()).isNull();
    }

    /**
     * Verifies that removing a conversation stops the answer being written in it first, so that the
     * answer is not written back into a conversation that is gone.
     */
    @Test
    void removingAConversationStopsItsAnswer() {
        this.service.deleteConversation(this.userId, this.notebookId, this.sessionId);

        verify(this.runningAnswers).stop(this.userId, this.sessionId);
        verify(this.chatSessionService).delete(this.userId, this.notebookId, this.sessionId);
    }

    /**
     * Verifies that stopping an answer is answered by the register, so that a reader is told whether
     * anything was actually stopped.
     */
    @Test
    void stoppingAnAnswerIsAnsweredByTheRegister() {
        when(this.runningAnswers.stop(this.userId, this.sessionId)).thenReturn(true);

        assertThat(this.service.stopAnswer(this.userId, this.sessionId)).isTrue();
    }

    /**
     * Lets the engine of a case produce one answer.
     *
     * @param answer text the engine reports as the finished answer
     */
    private void answerWith(final String answer) {
        doAnswer(invocation -> {
            invocation.getArgument(4, ChatStreamHandler.class).onCompleted(answer);
            return null;
        }).when(this.groundedChatEngine).answer(any(), any(), any(), anyString(), any(), any());
    }

    /**
     * Lets the index of a case return passages under the names the notebook lists.
     *
     * @param names    names of the sources the notebook lists
     * @param contents passages the index returns, in the order it returns them
     */
    private void retrieve(final Map<UUID, String> names, final Content... contents) {
        final ContentRetriever retriever = mock(ContentRetriever.class);
        when(this.sourceDocumentService.displayNames(this.userId, this.notebookId)).thenReturn(names);
        when(this.notebookIndex.retrieverFor(this.notebookId)).thenReturn(retriever);
        when(retriever.retrieve(any(Query.class))).thenReturn(List.of(contents));
    }

    /**
     * Reads the passages the engine was handed.
     *
     * @return the passages of the most recent answer
     */
    @SuppressWarnings("unchecked")
    private List<ContextPassage> capturedPassages() {
        final ArgumentCaptor<List<ContextPassage>> passages = ArgumentCaptor.forClass(List.class);
        verify(this.groundedChatEngine).answer(any(), passages.capture(), any(), anyString(), any(), any());
        return passages.getValue();
    }

    /**
     * Builds one retrieved passage of one source.
     *
     * @param sourceId source the passage was taken from
     * @param text     text of the passage
     * @return the passage as the index returns it
     */
    private static Content passage(final UUID sourceId, final String text) {
        final TextSegment segment = TextSegment.from(text);
        segment.metadata().put(SegmentMetadata.SOURCE_DOCUMENT_ID, sourceId);
        return Content.from(segment);
    }

    /**
     * Builds the turn the cases answer.
     *
     * @return the turn of the case
     */
    private ChatTurnContext context() {
        return new ChatTurnContext(this.sessionId, this.notebookId, "What is entropy?", List.of());
    }

    /**
     * A reader that records what it was told.
     */
    private static final class RecordingHandler implements ChatStreamHandler {

        /**
         * Sources the answer may cite.
         */
        private final List<RetrievedSource> sources = new ArrayList<>();

        /**
         * Finished answer, or {@code null} while none arrived.
         */
        private String completed;

        /**
         * Failure the answer ended with, or {@code null} while none arrived.
         */
        private Throwable failure;

        /**
         * Creates the reader.
         */
        private RecordingHandler() {
        }

        @Override
        public void onSources(final List<RetrievedSource> retrieved) {
            this.sources.addAll(retrieved);
        }

        @Override
        public void onToken(final String token) {
            // The cases state their answers through the ending rather than through the parts.
        }

        @Override
        public void onCompleted(final String answer) {
            this.completed = answer;
        }

        @Override
        public void onFailed(final Throwable error) {
            this.failure = error;
        }

        /**
         * Reports the sources the answer may cite.
         *
         * @return the sources, in the order they were announced
         */
        private List<RetrievedSource> sources() {
            return this.sources;
        }

        /**
         * Reports the finished answer.
         *
         * @return the answer, or {@code null} while none arrived
         */
        private String completed() {
            return this.completed;
        }

        /**
         * Reports the failure the answer ended with.
         *
         * @return the failure, or {@code null} while none arrived
         */
        private Throwable failure() {
            return this.failure;
        }
    }
}
