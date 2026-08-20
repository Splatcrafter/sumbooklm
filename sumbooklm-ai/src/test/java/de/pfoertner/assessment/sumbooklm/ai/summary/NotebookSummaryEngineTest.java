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
import de.pfoertner.assessment.sumbooklm.ai.chat.ChatProvider;
import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises what the engine does with what a provider answers a summary request with.
 *
 * <h2>Why Every Ending Is a Refusal</h2>
 * A summary is stored under a fingerprint of the sources it describes. An answer that is empty, or
 * one that never arrived, must therefore not be stored as a summary at all, because it would then
 * look current until the sources change. Each case below is one way of arriving at nothing, and all
 * of them have to end in the same refusal rather than in an empty paragraph.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookSummaryEngineTest {

    /**
     * Selection every case answers under, which no case reaches a provider with.
     */
    private static final ModelSelection SELECTION =
            new ModelSelection(ChatProvider.OPENAI, "gpt-4o-mini", "sk-secret", "https://proxy.test/v1");

    /**
     * Sources every case summarises.
     */
    private static final List<SourceExcerpt> SOURCES =
            List.of(new SourceExcerpt("Thermodynamics.pdf", "Entropy never decreases."));

    /**
     * Source of the model the engine writes with.
     */
    private ChatModelFactory chatModelFactory;

    /**
     * Model the engine asks.
     */
    private ChatModel model;

    /**
     * Engine under test.
     */
    private NotebookSummaryEngine engine;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    NotebookSummaryEngineTest() {
    }

    /**
     * Builds the engine and its model.
     */
    @BeforeEach
    void setUp() {
        this.chatModelFactory = mock(ChatModelFactory.class);
        this.model = mock(ChatModel.class);
        this.engine = new NotebookSummaryEngine(this.chatModelFactory);
        when(this.chatModelFactory.createComplete(SELECTION)).thenReturn(this.model);
    }

    /**
     * Verifies that a written summary is handed on without the whitespace a model tends to frame it
     * with, because the text is stored as it is returned.
     */
    @Test
    void aWrittenSummaryIsHandedOnTrimmed() {
        when(this.model.chat(any(ChatRequest.class)))
                .thenReturn(responseOf("\n  The sources describe entropy.  \n"));

        assertThat(this.engine.summarise(SELECTION, SOURCES, "en"))
                .isEqualTo("The sources describe entropy.");
    }

    /**
     * Verifies that a provider which cannot be reached is reported as a summary that was not
     * written, with the failure of the provider kept as its cause.
     */
    @Test
    void aProviderThatCannotBeReachedIsReported() {
        final RuntimeException refusal = new IllegalStateException("connection refused");
        when(this.model.chat(any(ChatRequest.class))).thenThrow(refusal);

        assertThatThrownBy(() -> this.engine.summarise(SELECTION, SOURCES, "en"))
                .isInstanceOf(SummaryNotWrittenException.class)
                .hasMessageContaining("did not write")
                .hasCause(refusal);
    }

    /**
     * Verifies that a model which cannot be built at all is reported the same way, so that a
     * selection refused by its client does not reach the caller as something else.
     */
    @Test
    void aModelThatCannotBeBuiltIsReported() {
        when(this.chatModelFactory.createComplete(SELECTION))
                .thenThrow(new IllegalArgumentException("unknown model"));

        assertThatThrownBy(() -> this.engine.summarise(SELECTION, SOURCES, "en"))
                .isInstanceOf(SummaryNotWrittenException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that an answer holding nothing but whitespace is refused rather than stored, because
     * a notebook would otherwise carry a summary that says nothing and looks current.
     */
    @Test
    void anAnswerOfWhitespaceIsRefused() {
        when(this.model.chat(any(ChatRequest.class))).thenReturn(responseOf("   \n  "));

        assertThatThrownBy(() -> this.engine.summarise(SELECTION, SOURCES, "en"))
                .isInstanceOf(SummaryNotWrittenException.class)
                .hasMessageContaining("without a summary");
    }

    /**
     * Verifies that an answer without any message at all is refused, which is what a provider
     * returning nothing amounts to.
     */
    @Test
    void anAnswerWithoutAMessageIsRefused() {
        when(this.model.chat(any(ChatRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> this.engine.summarise(SELECTION, SOURCES, "en"))
                .isInstanceOf(SummaryNotWrittenException.class)
                .hasMessageContaining("without a summary");
    }

    /**
     * Verifies that the request carries the material under the instructions and asks for the summary
     * in a second message, which is the shape the rules are written for.
     */
    @Test
    void theRequestCarriesTheMaterialAndTheRequestForIt() {
        when(this.model.chat(any(ChatRequest.class))).thenReturn(responseOf("A summary."));

        this.engine.summarise(SELECTION, SOURCES, "de");

        final ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(this.model).chat(request.capture());

        final List<ChatMessage> messages = request.getValue().messages();
        assertThat(messages).hasSize(2);
        assertThat(((SystemMessage) messages.get(0)).text())
                .contains("Thermodynamics.pdf", "Entropy never decreases.", "German");
    }

    /**
     * Verifies that material beyond what one request may hold is shortened rather than sent, so that
     * a notebook of many sources can still be summarised.
     */
    @Test
    void materialBeyondTheBudgetIsShortened() {
        when(this.model.chat(any(ChatRequest.class))).thenReturn(responseOf("A summary."));

        this.engine.summarise(SELECTION, List.of(
                new SourceExcerpt("Long.txt", "x".repeat(40_000)),
                new SourceExcerpt("Short.txt", "Heat flows from hot to cold.")), "en");

        final ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(this.model).chat(request.capture());

        final String instructions = ((SystemMessage) request.getValue().messages().get(0)).text();
        assertThat(instructions).contains("[...]").contains("Heat flows from hot to cold.");
        assertThat(instructions.length()).isLessThan(40_000);
    }

    /**
     * Builds the response a provider answers with.
     *
     * @param text text the provider wrote
     * @return the response carrying that text
     */
    private static ChatResponse responseOf(final String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
