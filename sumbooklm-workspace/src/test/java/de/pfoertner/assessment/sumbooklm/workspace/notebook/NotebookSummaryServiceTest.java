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

package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ChatProvider;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises how a summary is asked for and stored.
 *
 * <h2>Why the Permit Is the Subject</h2>
 * Writing a summary costs a request to a model, so it is bounded the same way a question is. That
 * makes the permit the part worth stating: it has to be taken before the model is asked and given
 * back however the request ends, including when the model refuses and when the rate refuses before
 * the model is reached at all. A permit that is not given back is one an account never gets back.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookSummaryServiceTest {

    /**
     * Selection every case answers under, which no case reaches a provider with.
     */
    private static final ModelSelection SELECTION =
            new ModelSelection(ChatProvider.OPENAI, "gpt-4o-mini", "sk-secret", "https://proxy.test/v1");

    /**
     * Store of the notebooks.
     */
    private NotebookService notebookService;

    /**
     * Store of the sources the summary is written from.
     */
    private SourceDocumentService sourceDocumentService;

    /**
     * Engine the summary is written by.
     */
    private NotebookSummaryEngine notebookSummaryEngine;

    /**
     * Bound on how many requests one account may have in flight.
     */
    private ConcurrentAnswerLimit concurrentAnswerLimit;

    /**
     * Bound on how often one account may ask.
     */
    private QuestionRateLimit questionRateLimit;

    /**
     * Service under test.
     */
    private NotebookSummaryService service;

    /**
     * Account of the cases.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Notebook of the cases.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    NotebookSummaryServiceTest() {
    }

    /**
     * Builds the service and everything it writes a summary through.
     */
    @BeforeEach
    void setUp() {
        this.notebookService = mock(NotebookService.class);
        this.sourceDocumentService = mock(SourceDocumentService.class);
        this.notebookSummaryEngine = mock(NotebookSummaryEngine.class);
        this.concurrentAnswerLimit = mock(ConcurrentAnswerLimit.class);
        this.questionRateLimit = mock(QuestionRateLimit.class);
        this.service = new NotebookSummaryService(this.notebookService, this.sourceDocumentService,
                this.notebookSummaryEngine, this.concurrentAnswerLimit, this.questionRateLimit);

        when(this.concurrentAnswerLimit.tryAcquire(this.userId)).thenReturn(true);
    }

    /**
     * Verifies that reading a summary asks the notebook and nothing else, because reading one costs
     * no request to a model and is therefore bounded by nothing.
     */
    @Test
    void readingASummaryCostsNothing() {
        final NotebookSummary stored = new NotebookSummary(this.notebookId, "About entropy.", false);
        when(this.notebookService.readSummary(this.userId, this.notebookId)).thenReturn(stored);

        assertThat(this.service.read(this.userId, this.notebookId)).isSameAs(stored);
        verify(this.concurrentAnswerLimit, never()).tryAcquire(any());
        verify(this.questionRateLimit, never()).record(any());
    }

    /**
     * Verifies that a summary is written from the text of every source that was read and stored
     * under the fingerprint of those sources.
     */
    @Test
    void aSummaryIsWrittenFromTheSourcesThatWereRead() {
        when(this.sourceDocumentService.texts(this.userId, this.notebookId)).thenReturn(List.of(
                new SourceText(UUID.randomUUID(), "First.pdf", "Entropy never decreases."),
                new SourceText(UUID.randomUUID(), "Second.pdf", "Heat flows from hot to cold.")));
        when(this.sourceDocumentService.fingerprintOfSources(this.userId, this.notebookId))
                .thenReturn("fingerprint");
        when(this.notebookSummaryEngine.summarise(any(), any(), anyString()))
                .thenReturn("The sources describe thermodynamics.");
        when(this.notebookService.storeSummary(this.userId, this.notebookId,
                "The sources describe thermodynamics.", "fingerprint"))
                .thenReturn(new NotebookSummary(this.notebookId, "The sources describe thermodynamics.", false));

        final NotebookSummary written = this.service.write(this.userId, this.notebookId, SELECTION, "de");

        assertThat(written.text()).isEqualTo("The sources describe thermodynamics.");
        assertThat(capturedExcerpts()).extracting(SourceExcerpt::displayName)
                .containsExactly("First.pdf", "Second.pdf");
        verify(this.concurrentAnswerLimit).release(this.userId);
    }

    /**
     * Verifies that a notebook holding no source that was read is refused before anything is
     * counted, because there is nothing to write about and a request would only cost.
     */
    @Test
    void aNotebookWithoutReadSourcesIsRefused() {
        when(this.sourceDocumentService.texts(this.userId, this.notebookId)).thenReturn(List.of());

        assertThatThrownBy(() -> this.service.write(this.userId, this.notebookId, SELECTION, "en"))
                .isInstanceOf(NothingToSummariseException.class)
                .hasMessageContaining(this.notebookId.toString());
        verify(this.concurrentAnswerLimit, never()).tryAcquire(any());
        verify(this.notebookSummaryEngine, never()).summarise(any(), any(), anyString());
    }

    /**
     * Verifies that an account which already has as many requests as it may have is refused without
     * reaching the model.
     */
    @Test
    void anAccountWithoutAPermitIsRefused() {
        when(this.sourceDocumentService.texts(this.userId, this.notebookId))
                .thenReturn(List.of(new SourceText(UUID.randomUUID(), "First.pdf", "Text.")));
        when(this.concurrentAnswerLimit.tryAcquire(this.userId)).thenReturn(false);

        assertThatThrownBy(() -> this.service.write(this.userId, this.notebookId, SELECTION, "en"))
                .isInstanceOf(TooManyQuestionsException.class);
        verify(this.questionRateLimit, never()).record(any());
        verify(this.concurrentAnswerLimit, never()).release(any());
    }

    /**
     * Verifies that a request refused by the rate gives its permit back, so that being told to wait
     * does not also cost an account a permit.
     */
    @Test
    void aRequestRefusedByTheRateGivesItsPermitBack() {
        when(this.sourceDocumentService.texts(this.userId, this.notebookId))
                .thenReturn(List.of(new SourceText(UUID.randomUUID(), "First.pdf", "Text.")));
        doThrow(new QuestionsTooOftenException(this.userId, Duration.ofSeconds(60)))
                .when(this.questionRateLimit).record(this.userId);

        assertThatThrownBy(() -> this.service.write(this.userId, this.notebookId, SELECTION, "en"))
                .isInstanceOf(QuestionsTooOftenException.class);
        verify(this.concurrentAnswerLimit).release(this.userId);
        verify(this.notebookSummaryEngine, never()).summarise(any(), any(), anyString());
    }

    /**
     * Verifies that a model which writes nothing gives the permit back and stores nothing, so that a
     * provider that is down cannot leave a notebook with an empty summary.
     */
    @Test
    void aModelThatWritesNothingStoresNothing() {
        when(this.sourceDocumentService.texts(this.userId, this.notebookId))
                .thenReturn(List.of(new SourceText(UUID.randomUUID(), "First.pdf", "Text.")));
        when(this.notebookSummaryEngine.summarise(any(), any(), anyString()))
                .thenThrow(new SummaryNotWrittenException("The model answered without a summary"));

        assertThatThrownBy(() -> this.service.write(this.userId, this.notebookId, SELECTION, "en"))
                .isInstanceOf(SummaryNotWrittenException.class);
        verify(this.concurrentAnswerLimit).release(this.userId);
        verify(this.notebookService, never()).storeSummary(any(), any(), anyString(), anyString());
    }

    /**
     * Verifies that the fingerprint is read before the model is asked, so that a summary is stored
     * under the sources it was written from rather than under the sources that exist afterwards.
     */
    @Test
    void theFingerprintIsTakenBeforeTheModelIsAsked() {
        when(this.sourceDocumentService.texts(this.userId, this.notebookId))
                .thenReturn(List.of(new SourceText(UUID.randomUUID(), "First.pdf", "Text.")));
        when(this.sourceDocumentService.fingerprintOfSources(this.userId, this.notebookId))
                .thenReturn("fingerprint-of-then");
        when(this.notebookSummaryEngine.summarise(any(), any(), anyString())).thenReturn("A summary.");
        when(this.notebookService.storeSummary(any(), any(), anyString(), anyString()))
                .thenReturn(new NotebookSummary(this.notebookId, "A summary.", true));

        this.service.write(this.userId, this.notebookId, SELECTION, "en");

        verify(this.notebookService).storeSummary(
                this.userId, this.notebookId, "A summary.", "fingerprint-of-then");
    }

    /**
     * Verifies that the language the summary is to be written in is passed on as it was given,
     * including the case where the reader named none.
     */
    @Test
    void theLanguageIsPassedOnAsGiven() {
        when(this.sourceDocumentService.texts(this.userId, this.notebookId))
                .thenReturn(List.of(new SourceText(UUID.randomUUID(), "First.pdf", "Text.")));
        when(this.notebookSummaryEngine.summarise(any(), any(), anyString())).thenReturn("A summary.");
        when(this.notebookService.storeSummary(any(), any(), anyString(), anyString()))
                .thenReturn(new NotebookSummary(this.notebookId, "A summary.", false));

        this.service.write(this.userId, this.notebookId, SELECTION, "");

        final ArgumentCaptor<String> language = ArgumentCaptor.forClass(String.class);
        verify(this.notebookSummaryEngine).summarise(any(), any(), language.capture());
        assertThat(language.getValue()).isEmpty();
    }

    /**
     * Reads the material the engine was handed.
     *
     * @return the excerpts of the most recent request
     */
    @SuppressWarnings("unchecked")
    private List<SourceExcerpt> capturedExcerpts() {
        final ArgumentCaptor<List<SourceExcerpt>> excerpts = ArgumentCaptor.forClass(List.class);
        verify(this.notebookSummaryEngine).summarise(any(), excerpts.capture(), anyString());
        return excerpts.getValue();
    }
}
