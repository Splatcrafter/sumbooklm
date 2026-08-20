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

package de.pfoertner.assessment.sumbooklm.ai.summary;

import java.util.List;

import de.pfoertner.assessment.sumbooklm.ai.chat.ChatModelFactory;
import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Asks a selected model to write one summary of a set of sources.
 *
 * <h2>What It Does Not Do</h2>
 * The engine neither chooses the sources nor stores the text. It receives the material, builds the
 * request the model sees and returns what came back, which is what lets the same code serve any
 * provider and any caller.
 *
 * <h2>Waiting Instead of Streaming</h2>
 * The call returns when the summary is finished. A summary is a few sentences that are read as one
 * paragraph, so streaming it would add a second protocol to the client for a text that is complete
 * before it is worth reading.
 *
 * <h2>Nothing Is a Failure</h2>
 * A response without text ends as a failure rather than as an empty summary. An empty summary would
 * be stored and shown as the description of a notebook that has sources, and the reader would have no
 * way to tell it from one the model wrote.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class NotebookSummaryEngine {

    /**
     * Factory of the client one summary is written through.
     */
    private final ChatModelFactory chatModelFactory;

    /**
     * Creates the engine.
     *
     * @param chatModelFactory factory of the client one summary is written through
     */
    public NotebookSummaryEngine(final ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    /**
     * Writes one summary of the sources it is given.
     *
     * @param selection   model the summary is requested from
     * @param sources     sources the summary is written from, in the order they are listed in
     * @param languageTag IETF language tag the summary is to be written in, empty for the language of
     *                    the sources
     * @return the finished summary
     * @throws IllegalArgumentException   if there are no sources to summarise
     * @throws SummaryNotWrittenException if the provider failed or answered with nothing
     */
    public String summarise(final ModelSelection selection,
                            final List<SourceExcerpt> sources,
                            final String languageTag) {
        final ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(NotebookSummaryPrompt.of(SummaryBudget.fit(sources), languageTag)),
                        UserMessage.from(NotebookSummaryPrompt.REQUEST)))
                .build();

        final ChatResponse response;
        try {
            final ChatModel model = this.chatModelFactory.createComplete(selection);
            response = model.chat(request);
        } catch (final RuntimeException e) {
            throw new SummaryNotWrittenException("The model did not write a summary", e);
        }

        final AiMessage message = response == null ? null : response.aiMessage();
        final String text = message == null ? null : message.text();
        if (text == null || text.isBlank()) {
            throw new SummaryNotWrittenException("The model answered without a summary");
        }
        return text.strip();
    }
}
