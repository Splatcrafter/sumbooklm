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

package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises what the engine does with what a provider sends back.
 *
 * <h2>Why a Provider Is Not Contacted</h2>
 * Everything worth stating here is a way a stream can end: with a complete response, with an error,
 * or with a reader who stopped listening halfway. A real provider produces one of those per run and
 * never the others, and the two that matter most are the ones no provider can be asked for. The
 * model is therefore driven by hand, which is also what makes the cases readable as endings.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class GroundedChatEngineTest {

    /**
     * Selection every case answers under, which no case reaches a provider with.
     */
    private static final ModelSelection SELECTION =
            new ModelSelection(ChatProvider.OPENAI, "gpt-4o-mini", "sk-secret", "https://proxy.test/v1");

    /**
     * Passage every case grounds its answer in.
     */
    private static final ContextPassage PASSAGE =
            new ContextPassage(1, "Thermodynamics.pdf", "Entropy never decreases.");

    /**
     * Source of the model the engine answers with.
     */
    private ChatModelFactory chatModelFactory;

    /**
     * Model the engine streams from.
     */
    private StreamingChatModel model;

    /**
     * Engine under test.
     */
    private GroundedChatEngine engine;

    /**
     * Reader the engine writes to.
     */
    private RecordingHandler handler;

    /**
     * Handle the reader stops an answer with.
     */
    private AnswerCancellation cancellation;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    GroundedChatEngineTest() {
    }

    /**
     * Builds the engine, its model and the reader every case runs against.
     */
    @BeforeEach
    void setUp() {
        this.chatModelFactory = mock(ChatModelFactory.class);
        this.model = mock(StreamingChatModel.class);
        this.engine = new GroundedChatEngine(this.chatModelFactory);
        this.handler = new RecordingHandler();
        this.cancellation = new AnswerCancellation();
        when(this.chatModelFactory.create(SELECTION)).thenReturn(this.model);
    }

    /**
     * Verifies that a model which cannot be built at all ends the answer as a failure, rather than
     * leaving a reader waiting for a stream that will never be opened.
     */
    @Test
    void aModelThatCannotBeBuiltEndsTheAnswer() {
        final RuntimeException refusal = new IllegalStateException("no such model");
        when(this.chatModelFactory.create(SELECTION)).thenThrow(refusal);

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(this.handler.failure()).isSameAs(refusal);
        assertThat(this.handler.completed()).isNull();
        verifyNoInteractions(this.model);
    }

    /**
     * Verifies that a request which is refused before the first token ends the answer as a failure,
     * which is what an address that answers with a status does.
     */
    @Test
    void aRequestThatIsRefusedEndsTheAnswer() {
        final RuntimeException refusal = new IllegalStateException("unauthorized");
        doThrow(refusal).when(this.model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(this.handler.failure()).isSameAs(refusal);
    }

    /**
     * Verifies that the parts of a stream reach the reader as they arrive and that the finished
     * answer is the text the provider reported as complete.
     */
    @Test
    void thePartsOfAStreamReachTheReader() {
        respondWith(stream -> {
            stream.onPartialResponse("Entropy ");
            stream.onPartialResponse("never decreases.");
            stream.onCompleteResponse(responseOf("Entropy never decreases. [1](#source-1)"));
        });

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(this.handler.tokens()).containsExactly("Entropy ", "never decreases.");
        assertThat(this.handler.completed()).isEqualTo("Entropy never decreases. [1](#source-1)");
    }

    /**
     * Verifies that a provider which ends without repeating the answer leaves the reader with what
     * was streamed, so that an empty complete response cannot erase a written answer.
     */
    @Test
    void aProviderThatRepeatsNothingLeavesWhatWasStreamed() {
        respondWith(stream -> {
            stream.onPartialResponse("Entropy never decreases.");
            stream.onCompleteResponse(responseOf(""));
        });

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(this.handler.completed()).isEqualTo("Entropy never decreases.");
    }

    /**
     * Verifies that a stream which fails after the reader stopped it is ended as a finished answer
     * rather than as a failure, because the reader already has what arrived and asked for no more.
     */
    @Test
    void aStreamThatFailsAfterBeingStoppedKeepsWhatArrived() {
        respondWith(stream -> {
            stream.onPartialResponse("Entropy ");
            this.cancellation.cancel();
            stream.onError(new IllegalStateException("stream closed"));
        });

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(this.handler.completed()).isEqualTo("Entropy ");
        assertThat(this.handler.failure()).isNull();
    }

    /**
     * Verifies that a part arriving after the reader stopped the answer is neither forwarded nor
     * added to it, so that stopping means stopping and not merely detaching.
     */
    @Test
    void aPartArrivingAfterAStopIsNotForwarded() {
        respondWith(stream -> {
            stream.onPartialResponse("Entropy ");
            this.cancellation.cancel();
            stream.onPartialResponse("never decreases.");
        });

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(this.handler.tokens()).containsExactly("Entropy ");
        assertThat(this.handler.completed()).isEqualTo("Entropy ");
    }

    /**
     * Verifies that a stream which fails and then reports itself complete ends the answer once, so
     * that a reader is never told two different things about the same answer.
     */
    @Test
    void aStreamThatFailsAndThenCompletesEndsOnce() {
        final RuntimeException failure = new IllegalStateException("connection reset");
        respondWith(stream -> {
            stream.onError(failure);
            stream.onCompleteResponse(responseOf("Entropy never decreases."));
        });

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(this.handler.failure()).isSameAs(failure);
        assertThat(this.handler.completed()).isNull();
        assertThat(this.handler.endings()).isEqualTo(1);
    }

    /**
     * Verifies that the stream of a provider is handed to the cancellation, so that a reader who
     * stops an answer abandons the request instead of merely being ignored.
     */
    @Test
    void theStreamOfTheProviderCanBeAbandoned() {
        final AtomicReference<Boolean> abandoned = new AtomicReference<>(Boolean.FALSE);
        final StreamingHandle handle = new StreamingHandle() {

            @Override
            public void cancel() {
                abandoned.set(Boolean.TRUE);
            }

            @Override
            public boolean isCancelled() {
                return abandoned.get();
            }
        };

        respondWith(stream -> {
            stream.onPartialResponse(new PartialResponse("Entropy "), new PartialResponseContext(handle));
            this.cancellation.cancel();
            stream.onCompleteResponse(responseOf("Entropy "));
        });

        this.engine.answer(SELECTION, List.of(PASSAGE), List.of(), "What is entropy?",
                this.handler, this.cancellation);

        assertThat(abandoned.get()).isTrue();
    }

    /**
     * Verifies that a request carries the instructions first, the remembered conversation in the
     * order it was held in and the question last, which is the shape every provider reads.
     */
    @Test
    void theRequestCarriesInstructionsHistoryAndQuestion() {
        respondWith(stream -> stream.onCompleteResponse(responseOf("An answer.")));

        this.engine.answer(SELECTION, List.of(PASSAGE),
                List.of(new ChatTurn(ChatRole.USER, "What is heat?"),
                        new ChatTurn(ChatRole.ASSISTANT, "Energy in transit.")),
                "And entropy?", this.handler, this.cancellation);

        final ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(this.model).chat(request.capture(), any(StreamingChatResponseHandler.class));

        final List<ChatMessage> messages = request.getValue().messages();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).text()).contains("Thermodynamics.pdf");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText()).isEqualTo("What is heat?");
        assertThat(messages.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(2)).text()).isEqualTo("Energy in transit.");
        assertThat(((UserMessage) messages.get(3)).singleText()).isEqualTo("And entropy?");
    }

    /**
     * Verifies that a conversation which no longer fits into a request is cut rather than sent, so
     * that a long history cannot make every further question fail at the provider.
     */
    @Test
    void aConversationBeyondTheBudgetIsCut() {
        respondWith(stream -> stream.onCompleteResponse(responseOf("An answer.")));

        this.engine.answer(SELECTION, List.of(PASSAGE),
                List.of(new ChatTurn(ChatRole.USER, "x".repeat(30_000))),
                "And entropy?", this.handler, this.cancellation);

        final ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(this.model).chat(request.capture(), any(StreamingChatResponseHandler.class));

        assertThat(request.getValue().messages()).hasSize(2);
    }

    /**
     * Lets a case drive the stream the engine passes to the model.
     *
     * @param script what the case sends into the stream, in the order it is to arrive
     */
    private void respondWith(final StreamScript script) {
        doAnswer(invocation -> {
            script.play(invocation.getArgument(1, StreamingChatResponseHandler.class));
            return null;
        }).when(this.model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    /**
     * Builds the response a provider ends a stream with.
     *
     * @param text text the provider reports as the complete answer
     * @return the response carrying that text
     */
    private static ChatResponse responseOf(final String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    /**
     * What one case sends into the stream of a provider.
     */
    @FunctionalInterface
    private interface StreamScript {

        /**
         * Sends the events of the case into the stream.
         *
         * @param stream stream the engine handed to the model
         */
        void play(StreamingChatResponseHandler stream);
    }

    /**
     * A reader that records what it was told.
     */
    private static final class RecordingHandler implements AnswerStreamHandler {

        /**
         * Parts of the answer, in the order they arrived.
         */
        private final List<String> tokens = new ArrayList<>();

        /**
         * Finished answer, or {@code null} while none arrived.
         */
        private String completed;

        /**
         * Failure the answer ended with, or {@code null} while none arrived.
         */
        private Throwable failure;

        /**
         * Number of endings the reader was told about.
         */
        private int endings;

        /**
         * Creates the reader.
         */
        private RecordingHandler() {
        }

        @Override
        public void onToken(final String token) {
            this.tokens.add(token);
        }

        @Override
        public void onCompleted(final String answer) {
            this.completed = answer;
            this.endings += 1;
        }

        @Override
        public void onFailed(final Throwable error) {
            this.failure = error;
            this.endings += 1;
        }

        /**
         * Reports the parts of the answer that arrived.
         *
         * @return the parts, in the order they arrived
         */
        private List<String> tokens() {
            return this.tokens;
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

        /**
         * Reports how often the reader was told the answer had ended.
         *
         * @return the number of endings
         */
        private int endings() {
            return this.endings;
        }
    }
}
