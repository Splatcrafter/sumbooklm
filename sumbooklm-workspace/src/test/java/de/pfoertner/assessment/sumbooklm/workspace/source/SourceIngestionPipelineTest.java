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

package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import de.pfoertner.assessment.sumbooklm.ingestion.chunking.TextChunker;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.ExtractedContent;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.FileTextExtractor;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.TextExtractionException;
import de.pfoertner.assessment.sumbooklm.ingestion.extraction.WebPageTextExtractor;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the run that turns a stored source into segments of the retrieval index.
 *
 * <h2>Why the Endings Matter</h2>
 * The run happens after the response was already sent, so nothing it does can be reported to the
 * caller. Every way it can end therefore has to leave the source in a state a later reader can make
 * sense of: a reason they can act on where a parser refused it, an unremarkable stage where the
 * source was removed underneath the run, and a recorded failure even where nothing named a cause.
 * The other half is that a source is read from the network only when it has to be, because reading
 * it again is what makes it change.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SourceIngestionPipelineTest {

    /**
     * Segments the chunker produces for every case that gets that far.
     */
    private static final List<TextSegment> SEGMENTS =
            List.of(TextSegment.from("Entropy never decreases."));

    /**
     * Store the run reads its source from and writes its result to.
     */
    private SourceDocumentService sourceDocumentService;

    /**
     * Reader of uploaded files.
     */
    private FileTextExtractor fileTextExtractor;

    /**
     * Reader of web pages.
     */
    private WebPageTextExtractor webPageTextExtractor;

    /**
     * Splitter the read text is cut with.
     */
    private TextChunker textChunker;

    /**
     * Index the segments are written into.
     */
    private NotebookIndex notebookIndex;

    /**
     * Run under test.
     */
    private SourceIngestionPipeline pipeline;

    /**
     * Account the source of the cases belongs to.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Source the cases read.
     */
    private final UUID sourceId = UUID.randomUUID();

    /**
     * Notebook the source of the cases belongs to.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SourceIngestionPipelineTest() {
    }

    /**
     * Builds the run and everything it reads and writes through.
     */
    @BeforeEach
    void setUp() {
        this.sourceDocumentService = mock(SourceDocumentService.class);
        this.fileTextExtractor = mock(FileTextExtractor.class);
        this.webPageTextExtractor = mock(WebPageTextExtractor.class);
        this.textChunker = mock(TextChunker.class);
        this.notebookIndex = mock(NotebookIndex.class);
        this.pipeline = new SourceIngestionPipeline(this.sourceDocumentService,
                this.fileTextExtractor, this.webPageTextExtractor, this.textChunker,
                this.notebookIndex);

        when(this.textChunker.chunk(anyString())).thenReturn(SEGMENTS);
        when(this.notebookIndex.index(any(), any(), any())).thenReturn(128);
    }

    /**
     * Verifies that a page which was never read is retrieved, cut and indexed, and that the title
     * the page carries becomes the name it is listed under.
     */
    @Test
    void aPageThatWasNeverReadIsRetrievedAndNamedByItsTitle() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, null));
        when(this.webPageTextExtractor.extract("https://example.org"))
                .thenReturn(new ExtractedContent("  Entropy explained  ", "Entropy never decreases."));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isTrue();

        verify(this.notebookIndex).index(this.notebookId, this.sourceId, SEGMENTS);
        verify(this.sourceDocumentService).completeIndexing(this.userId, this.sourceId,
                "Entropy explained", 128, "Entropy never decreases.", true);
    }

    /**
     * Verifies that a page whose text is already stored is indexed from that text rather than
     * retrieved again, which is what lets the index be rebuilt after a restart without the network.
     */
    @Test
    void aStoredTextIsIndexedWithoutTheNetwork() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, "Stored text of the page."));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isTrue();

        verify(this.webPageTextExtractor, never()).extract(anyString());
        verify(this.textChunker).chunk("Stored text of the page.");
        verify(this.sourceDocumentService).completeIndexing(this.userId, this.sourceId,
                "Page", 128, "Stored text of the page.", false);
    }

    /**
     * Verifies that a source read again is retrieved even though its text is stored, because reading
     * it again is exactly what the reader asked for.
     */
    @Test
    void aRereadGoesToTheNetworkAnyway() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, "Stored text of the page."));
        when(this.webPageTextExtractor.extract("https://example.org"))
                .thenReturn(new ExtractedContent("", "Fresh text of the page."));

        assertThat(this.pipeline.index(this.userId, this.sourceId, true)).isTrue();

        verify(this.webPageTextExtractor).extract("https://example.org");
        verify(this.sourceDocumentService).completeIndexing(this.userId, this.sourceId,
                "Page", 128, "Fresh text of the page.", true);
    }

    /**
     * Verifies that a page which carries no title keeps the name it already had, so that a list does
     * not lose its entry to an empty string.
     */
    @Test
    void aPageWithoutATitleKeepsItsName() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, null));
        when(this.webPageTextExtractor.extract("https://example.org"))
                .thenReturn(new ExtractedContent("   ", "Entropy never decreases."));

        this.pipeline.index(this.userId, this.sourceId, false);

        verify(this.sourceDocumentService).completeIndexing(this.userId, this.sourceId,
                "Page", 128, "Entropy never decreases.", true);
    }

    /**
     * Verifies that an uploaded file is read from its bytes and keeps the name it was uploaded
     * under, because a file has no title of its own.
     */
    @Test
    void anUploadedFileIsReadFromItsBytes() {
        final byte[] content = {1, 2, 3};
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(new IngestionInput(this.notebookId, SourceKind.FILE, "notes.txt",
                        "notes.txt", content, null));
        when(this.fileTextExtractor.extract(content, "notes.txt"))
                .thenReturn(new ExtractedContent("", "Entropy never decreases."));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isTrue();

        verify(this.sourceDocumentService).completeIndexing(this.userId, this.sourceId,
                "notes.txt", 128, "Entropy never decreases.", true);
    }

    /**
     * Verifies that a file whose bytes are gone is recorded as empty rather than reaching the parser
     * as nothing, which is the state a row written without its content would be in.
     */
    @Test
    void aFileWithoutBytesIsRecordedAsEmpty() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(new IngestionInput(this.notebookId, SourceKind.FILE, "notes.txt",
                        "notes.txt", null, null));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isFalse();

        verify(this.sourceDocumentService).failIndexing(this.userId, this.sourceId, DocumentFailure.EMPTY);
        verify(this.notebookIndex, never()).index(any(), any(), any());
    }

    /**
     * Verifies that the reason a parser or a client named is what gets recorded, so that the user is
     * told what they can do about it.
     */
    @Test
    void theReasonAnExtractorNamedIsRecorded() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, null));
        when(this.webPageTextExtractor.extract("https://example.org")).thenThrow(
                new TextExtractionException(DocumentFailure.BLOCKED, "The address is refused"));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isFalse();

        verify(this.sourceDocumentService).failIndexing(this.userId, this.sourceId, DocumentFailure.BLOCKED);
    }

    /**
     * Verifies that a failure nothing named is still recorded, as the reason that stands for
     * everything else, so that a source does not stay in the reading stage for ever.
     */
    @Test
    void aFailureNothingNamedIsStillRecorded() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, null));
        when(this.webPageTextExtractor.extract("https://example.org"))
                .thenThrow(new IllegalStateException("something inside a library broke"));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isFalse();

        verify(this.sourceDocumentService).failIndexing(
                this.userId, this.sourceId, DocumentFailure.UNEXPECTED);
    }

    /**
     * Verifies that a source removed before the run started is passed over quietly, because a reader
     * who adds a source and removes it again has done nothing wrong.
     */
    @Test
    void aSourceRemovedBeforeTheRunIsPassedOver() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenThrow(new SourceNotFoundException(this.sourceId));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isFalse();

        verify(this.sourceDocumentService, never())
                .failIndexing(any(), any(), any(DocumentFailure.class));
        verify(this.notebookIndex, never()).index(any(), any(), any());
    }

    /**
     * Verifies that a source removed while it was being read is passed over as well, rather than
     * being written back after it was deleted.
     */
    @Test
    void aSourceRemovedDuringTheRunIsPassedOver() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, "Stored text."));
        doThrow(new SourceNotFoundException(this.sourceId))
                .when(this.sourceDocumentService)
                .completeIndexing(any(), any(), anyString(), anyInt(), anyString(), anyBoolean());

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isFalse();

        verify(this.sourceDocumentService, never())
                .failIndexing(any(), any(), any(DocumentFailure.class));
    }

    /**
     * Verifies that a source removed between failing and having its failure recorded does not turn
     * one failure into two, which is what a reader removing a page that cannot be reached produces.
     */
    @Test
    void aSourceRemovedWhileItsFailureIsRecordedEndsQuietly() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, null));
        when(this.webPageTextExtractor.extract("https://example.org")).thenThrow(
                new TextExtractionException(DocumentFailure.UNREACHABLE, "not reachable"));
        doThrow(new SourceNotFoundException(this.sourceId))
                .when(this.sourceDocumentService)
                .failIndexing(any(), any(), any(DocumentFailure.class));

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isFalse();
    }

    /**
     * Verifies that a source whose text produced no segments at all is still finished rather than
     * left in the reading stage, and that it is counted as no tokens.
     */
    @Test
    void aTextThatProducedNoSegmentsIsStillFinished() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, "Stored text."));
        when(this.textChunker.chunk(anyString())).thenReturn(List.of());
        when(this.notebookIndex.index(any(), any(), eq(List.of()))).thenReturn(0);

        assertThat(this.pipeline.index(this.userId, this.sourceId, false)).isTrue();

        final ArgumentCaptor<Integer> tokens = ArgumentCaptor.forClass(Integer.class);
        verify(this.sourceDocumentService).completeIndexing(eq(this.userId), eq(this.sourceId),
                anyString(), tokens.capture(), anyString(), anyBoolean());
        assertThat(tokens.getValue()).isZero();
    }

    /**
     * Verifies that a run started by the event of an added source does the same as one started by
     * hand, so that the two ways into the pipeline cannot drift apart.
     */
    @Test
    void anAnnouncedSourceIsReadTheSameWay() {
        when(this.sourceDocumentService.beginIndexing(this.userId, this.sourceId))
                .thenReturn(input(SourceKind.WEB, null, "Stored text."));

        this.pipeline.onIndexRequested(new SourceIndexRequestedEvent(this.userId, this.sourceId, false));

        verify(this.notebookIndex).index(this.notebookId, this.sourceId, SEGMENTS);
    }

    /**
     * Builds what the store hands to the run.
     *
     * @param kind          way the source entered the notebook
     * @param content       bytes of an uploaded file, or {@code null} for a page
     * @param extractedText text of a previous run, or {@code null} if there was none
     * @return the input of the run
     */
    private IngestionInput input(final SourceKind kind, final byte[] content, final String extractedText) {
        return new IngestionInput(this.notebookId, kind, "https://example.org", "Page",
                content, extractedText);
    }
}
