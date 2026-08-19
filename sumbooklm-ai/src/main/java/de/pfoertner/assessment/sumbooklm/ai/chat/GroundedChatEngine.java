package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.springframework.stereotype.Component;

/**
 * Asks a selected model a question about a set of passages and streams the answer back.
 *
 * <h2>What It Does Not Do</h2>
 * The engine neither chooses the passages nor stores the answer. It receives the material, builds the
 * conversation the model sees and hands the parts on as they arrive, which is what lets the same code
 * serve any provider and any caller.
 *
 * <h2>The Answer That Is Kept</h2>
 * The finished text is taken from the response the provider closes the stream with, and the parts
 * that were streamed are only used when that response carries no text. The two agree in the normal
 * case, and where they do not, the closing response is the one the provider considers the answer.
 *
 * <h2>A Stopped Answer Has Finished</h2>
 * A stop is noticed between two parts of the answer. The run then ends as complete, carrying what
 * arrived up to that point, because the reader has already read it and it was already paid for. The
 * parts that keep arriving afterwards are read and discarded: the provider is not told, and cannot
 * be, so the only thing left to decide is whether they reach the reader.
 *
 * <h2>Threading</h2>
 * The parts of one response may be delivered by more than one thread, so the fallback buffer is a
 * synchronised one. Nothing else in a run is shared.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class GroundedChatEngine {

    /**
     * Factory of the client one answer is generated through.
     */
    private final ChatModelFactory chatModelFactory;

    /**
     * Creates the engine.
     *
     * @param chatModelFactory factory of the client one answer is generated through
     */
    public GroundedChatEngine(final ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    /**
     * Answers one question and reports the answer to the handler as it is generated.
     *
     * @param selection model the answer is requested from
     * @param passages  passages the answer may be based on, in the order they are numbered
     * @param history   earlier messages of the conversation, oldest first
     * @param question     question that was asked
     * @param handler      receiver of the parts, of the finished answer and of a failure
     * @param cancellation the way to stop this answer, filled in once there is something to stop
     */
    public void answer(final ModelSelection selection,
                       final List<ContextPassage> passages,
                       final List<ChatTurn> history,
                       final String question,
                       final AnswerStreamHandler handler,
                       final AnswerCancellation cancellation) {
        final StreamingChatModel model;
        try {
            model = this.chatModelFactory.create(selection);
        } catch (final RuntimeException e) {
            handler.onFailed(e);
            return;
        }

        final ChatRequest request = ChatRequest.builder()
                .messages(messages(passages, history, question))
                .build();

        try {
            model.chat(request, new StreamingChatResponseHandler() {

                private final StringBuffer streamed = new StringBuffer();

                private final AtomicBoolean ended = new AtomicBoolean();

                @Override
                public void onPartialResponse(final String partialResponse) {
                    if (cancellation.isRequested()) {
                        finish(this.streamed.toString());
                        return;
                    }
                    this.streamed.append(partialResponse);
                    handler.onToken(partialResponse);
                }

                @Override
                public void onPartialResponse(final PartialResponse partialResponse,
                                              final PartialResponseContext context) {
                    onPartialResponse(partialResponse.text());
                }

                @Override
                public void onCompleteResponse(final ChatResponse completeResponse) {
                    final AiMessage message = completeResponse.aiMessage();
                    final String text = message == null ? null : message.text();
                    finish(text == null || text.isEmpty() ? this.streamed.toString() : text);
                }

                @Override
                public void onError(final Throwable error) {
                    if (cancellation.isRequested()) {
                        finish(this.streamed.toString());
                        return;
                    }
                    if (this.ended.compareAndSet(false, true)) {
                        handler.onFailed(error);
                    }
                }

                /**
                 * Reports the answer as finished, unless this run has already ended.
                 *
                 * @param answer text the answer ends with
                 */
                private void finish(final String answer) {
                    if (this.ended.compareAndSet(false, true)) {
                        handler.onCompleted(answer);
                    }
                }
            });
        } catch (final RuntimeException e) {
            handler.onFailed(e);
        }
    }

    /**
     * Builds the conversation the model is given.
     *
     * @param passages passages the answer may be based on
     * @param history  earlier messages of the conversation, oldest first
     * @param question question that was asked
     * @return the instructions, the earlier messages and the question, in that order
     */
    private static List<ChatMessage> messages(final List<ContextPassage> passages,
                                              final List<ChatTurn> history,
                                              final String question) {
        final List<ChatMessage> messages = new ArrayList<>(history.size() + 2);
        messages.add(SystemMessage.from(GroundedPrompt.of(passages)));
        for (final ChatTurn turn : history) {
            messages.add(switch (turn.role()) {
                case USER -> UserMessage.from(turn.text());
                case ASSISTANT -> AiMessage.from(turn.text());
            });
        }
        messages.add(UserMessage.from(question));
        return messages;
    }
}
