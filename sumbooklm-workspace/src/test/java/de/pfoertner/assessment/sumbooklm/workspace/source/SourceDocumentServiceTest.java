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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentStatus;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceDocument;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import de.pfoertner.assessment.sumbooklm.persistence.document.DocumentPayload;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentEntity;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentMapper;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentRepository;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookEntity;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookRepository;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises how the sources of one notebook are added, read and removed.
 *
 * <h2>What the Cases Watch</h2>
 * Adding a source is where the most can go wrong that a user can cause: an upload of nothing, a
 * second upload of the same bytes, a file whose name is a path, and two uploads of the same content
 * at once. The first three are refused before anything is written; the fourth passes the check and
 * is caught by the table, and both have to reach the caller as the same conflict. Everything after
 * that is ownership: a source is reached through a notebook and both have to belong to the account.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SourceDocumentServiceTest {

    /**
     * Moment every case is answered at.
     */
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Store of the notebooks.
     */
    private NotebookRepository notebookRepository;

    /**
     * Store of the sources.
     */
    private SourceDocumentRepository sourceDocumentRepository;

    /**
     * Reader of the stored part of a source.
     */
    private SourceDocumentMapper sourceDocumentMapper;

    /**
     * Channel an addition or a removal is announced over.
     */
    private ApplicationEventPublisher eventPublisher;

    /**
     * Service under test.
     */
    private SourceDocumentService service;

    /**
     * Account the sources of the cases belong to.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Notebook the sources of the cases belong to.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SourceDocumentServiceTest() {
    }

    /**
     * Builds the service and everything it reads and writes through.
     */
    @BeforeEach
    void setUp() {
        this.notebookRepository = mock(NotebookRepository.class);
        this.sourceDocumentRepository = mock(SourceDocumentRepository.class);
        this.sourceDocumentMapper = mock(SourceDocumentMapper.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.service = new SourceDocumentService(this.notebookRepository,
                this.sourceDocumentRepository, this.sourceDocumentMapper, this.eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(this.notebookRepository.touch(eq(this.notebookId), eq(this.userId), any())).thenReturn(1);
        when(this.notebookRepository.findByIdAndUserId(this.notebookId, this.userId))
                .thenReturn(Optional.of(new NotebookEntity(this.notebookId, this.userId, NOW, NOW,
                        new byte[]{1}, PayloadSchemaVersion.CURRENT)));
        when(this.sourceDocumentMapper.writePayload(any())).thenReturn(new byte[]{1, 2});
        when(this.sourceDocumentRepository.saveAndFlush(any())).thenAnswer(
                invocation -> invocation.getArgument(0, SourceDocumentEntity.class));
        when(this.sourceDocumentMapper.toDomain(any(SourceDocumentEntity.class), any(DocumentPayload.class)))
                .thenAnswer(invocation -> sourceOf(
                        invocation.getArgument(0, SourceDocumentEntity.class),
                        invocation.getArgument(1, DocumentPayload.class)));
    }

    /**
     * Verifies that an uploaded file enters the notebook as a source waiting to be read, and that a
     * request to read it is announced.
     */
    @Test
    void anUploadedFileEntersAsASourceWaitingToBeRead() {
        final SourceDocument added = this.service.addFile(this.userId, this.notebookId, "notes.txt",
                "Entropy never decreases.".getBytes(StandardCharsets.UTF_8));

        assertThat(added.displayName()).isEqualTo("notes.txt");
        assertThat(added.kind()).isEqualTo(SourceKind.FILE);
        assertThat(added.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(added.failure()).isEqualTo(DocumentFailure.NONE);
        assertThat(added.indexedAt()).isNull();

        final ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(this.eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(SourceIndexRequestedEvent.class);
        assertThat(((SourceIndexRequestedEvent) event.getValue()).reread()).isFalse();
    }

    /**
     * Verifies that the directories of an uploaded name are dropped, because a browser may send a
     * path and the name is shown to the user.
     */
    @Test
    void theDirectoriesOfAnUploadedNameAreDropped() {
        final byte[] content = "text".getBytes(StandardCharsets.UTF_8);

        assertThat(this.service.addFile(this.userId, this.notebookId,
                "C:\\Users\\erik\\Documents\\notes.txt", content).displayName()).isEqualTo("notes.txt");
        assertThat(this.service.addFile(this.userId, this.notebookId,
                "/home/erik/notes.txt", content).displayName()).isEqualTo("notes.txt");
    }

    /**
     * Verifies that a file whose name is nothing but a path is given a name of its own, so that the
     * list of sources shows something rather than an empty entry.
     */
    @Test
    void aFileWithoutANameIsNamedAnyway() {
        final byte[] content = "text".getBytes(StandardCharsets.UTF_8);

        assertThat(this.service.addFile(this.userId, this.notebookId, "/", content).displayName())
                .isEqualTo("Untitled");
        assertThat(this.service.addFile(this.userId, this.notebookId, "   ", content).displayName())
                .isEqualTo("Untitled");
    }

    /**
     * Verifies that an upload without bytes is refused before anything is written, because there is
     * nothing to read from it and the user can answer that.
     */
    @Test
    void anUploadWithoutBytesIsRefused() {
        assertThatThrownBy(() ->
                this.service.addFile(this.userId, this.notebookId, "empty.txt", new byte[0]))
                .isInstanceOf(EmptyUploadException.class)
                .hasMessageContaining("empty.txt");
        verify(this.sourceDocumentRepository, never()).saveAndFlush(any());
    }

    /**
     * Verifies that a second upload of the same content is refused as a duplicate rather than
     * indexed twice.
     */
    @Test
    void aSecondUploadOfTheSameContentIsRefused() {
        when(this.sourceDocumentRepository.existsByNotebookIdAndUserIdAndDocumentHash(
                eq(this.notebookId), eq(this.userId), anyString())).thenReturn(true);

        assertThatThrownBy(() -> this.service.addFile(this.userId, this.notebookId, "notes.txt",
                "Entropy never decreases.".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DuplicateSourceException.class);
        verify(this.sourceDocumentRepository, never()).saveAndFlush(any());
    }

    /**
     * Verifies that two uploads of the same content arriving at once end in the same refusal, since
     * the second passes the check and is caught by the table instead.
     */
    @Test
    void twoUploadsArrivingAtOnceEndInTheSameRefusal() {
        when(this.sourceDocumentRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("ux_source_document_notebook_hash"));

        assertThatThrownBy(() -> this.service.addFile(this.userId, this.notebookId, "notes.txt",
                "Entropy never decreases.".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DuplicateSourceException.class);
        verify(this.eventPublisher, never()).publishEvent(any(Object.class));
    }

    /**
     * Verifies that an address enters as a source under the address itself, stripped of the
     * whitespace a paste tends to carry.
     */
    @Test
    void anAddressEntersUnderItself() {
        final SourceDocument added = this.service.addWebPage(
                this.userId, this.notebookId, "  https://example.org/article  ");

        assertThat(added.origin()).isEqualTo("https://example.org/article");
        assertThat(added.displayName()).isEqualTo("https://example.org/article");
        assertThat(added.kind()).isEqualTo(SourceKind.WEB);
    }

    /**
     * Verifies that a source cannot be added to a notebook of another account.
     */
    @Test
    void aSourceCannotBeAddedToANotebookOfAnotherAccount() {
        final UUID foreign = UUID.randomUUID();
        when(this.notebookRepository.touch(eq(foreign), eq(this.userId), any())).thenReturn(0);

        assertThatThrownBy(() ->
                this.service.addWebPage(this.userId, foreign, "https://example.org"))
                .isInstanceOf(NotebookNotFoundException.class);
        verify(this.sourceDocumentRepository, never()).saveAndFlush(any());
    }

    /**
     * Verifies that only the sources that were read and hold text are handed to a summary, so that a
     * source which failed cannot be described as if it had been read.
     */
    @Test
    void onlyReadSourcesAreHandedToASummary() {
        final SourceDocumentEntity ready = entity(this.notebookId, "Entropy never decreases.");
        final SourceDocumentEntity blank = entity(this.notebookId, "   ");
        final SourceDocumentEntity unread = entity(this.notebookId, null);
        final SourceDocumentEntity failed = entity(this.notebookId, "Some text");
        when(this.sourceDocumentRepository.findAllByNotebookIdAndUserIdOrderByCreatedAtAsc(
                this.notebookId, this.userId)).thenReturn(List.of(ready, blank, unread, failed));
        when(this.sourceDocumentMapper.readPayload(ready)).thenReturn(payload(DocumentStatus.READY, "Ready"));
        when(this.sourceDocumentMapper.readPayload(blank)).thenReturn(payload(DocumentStatus.READY, "Blank"));
        when(this.sourceDocumentMapper.readPayload(unread))
                .thenReturn(payload(DocumentStatus.UPLOADED, "Unread"));
        when(this.sourceDocumentMapper.readPayload(failed)).thenReturn(payload(DocumentStatus.ERROR, "Failed"));

        final List<SourceText> texts = this.service.texts(this.userId, this.notebookId);

        assertThat(texts).extracting(SourceText::displayName).containsExactly("Ready");
    }

    /**
     * Verifies that the names of the sources are listed under their identifiers, which is what an
     * answer resolves a retrieved passage to a citation with.
     */
    @Test
    void theNamesOfTheSourcesAreListedUnderTheirIdentifiers() {
        final SourceDocumentEntity first = entity(this.notebookId, "text");
        final SourceDocumentEntity second = entity(this.notebookId, "text");
        when(this.sourceDocumentRepository.findAllByNotebookIdAndUserIdOrderByCreatedAtAsc(
                this.notebookId, this.userId)).thenReturn(List.of(first, second));
        when(this.sourceDocumentMapper.readPayload(first)).thenReturn(payload(DocumentStatus.READY, "First"));
        when(this.sourceDocumentMapper.readPayload(second)).thenReturn(payload(DocumentStatus.READY, "Second"));

        final Map<UUID, String> names = this.service.displayNames(this.userId, this.notebookId);

        assertThat(names).containsEntry(first.getId(), "First").containsEntry(second.getId(), "Second");
    }

    /**
     * Verifies that removing a source announces the removal, so that the segments it was indexed as
     * are cleared from the retrieval index.
     */
    @Test
    void removingASourceAnnouncesIt() {
        final SourceDocumentEntity entity = entity(this.notebookId, "text");
        when(this.sourceDocumentRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));

        this.service.delete(this.userId, this.notebookId, entity.getId());

        verify(this.sourceDocumentRepository).delete(entity);
        verify(this.eventPublisher).publishEvent(new SourceRemovedEvent(entity.getId()));
    }

    /**
     * Verifies that a source of another notebook cannot be removed through this one, even where it
     * belongs to the same account.
     */
    @Test
    void aSourceOfAnotherNotebookCannotBeRemoved() {
        final SourceDocumentEntity elsewhere = entity(UUID.randomUUID(), "text");
        when(this.sourceDocumentRepository.findByIdAndUserId(elsewhere.getId(), this.userId))
                .thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() ->
                this.service.delete(this.userId, this.notebookId, elsewhere.getId()))
                .isInstanceOf(SourceNotFoundException.class);
        verify(this.sourceDocumentRepository, never()).delete(any(SourceDocumentEntity.class));
    }

    /**
     * Verifies that asking for a source to be read again puts it back into the waiting stage and
     * announces a run that is to ignore the text already stored.
     */
    @Test
    void readingASourceAgainIsAnnouncedAsAReread() {
        final SourceDocumentEntity entity = entity(this.notebookId, "old text");
        when(this.sourceDocumentRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.sourceDocumentMapper.readPayload(entity)).thenReturn(payload(DocumentStatus.ERROR, "Page"));

        final SourceDocument refreshed =
                this.service.requestRefresh(this.userId, this.notebookId, entity.getId());

        assertThat(refreshed.status()).isEqualTo(DocumentStatus.UPLOADED);
        verify(this.eventPublisher).publishEvent(
                new SourceIndexRequestedEvent(this.userId, entity.getId(), true));
    }

    /**
     * Verifies that beginning to read a source puts it into the stage that says so and hands out
     * what the pipeline needs, including the text stored from a previous run.
     */
    @Test
    void beginningToReadASourceHandsOutWhatIsStored() {
        final SourceDocumentEntity entity = entity(this.notebookId, "stored text");
        when(this.sourceDocumentRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.sourceDocumentMapper.readPayload(entity)).thenReturn(payload(DocumentStatus.UPLOADED, "Page"));

        final IngestionInput input = this.service.beginIndexing(this.userId, entity.getId());

        assertThat(input.notebookId()).isEqualTo(this.notebookId);
        assertThat(input.extractedText()).isEqualTo("stored text");
        assertThat(input.displayName()).isEqualTo("Page");
        assertThat(writtenPayload().status()).isEqualTo(DocumentStatus.INDEXING);
    }

    /**
     * Verifies that a source read from the store keeps the moment it was last read, because nothing
     * was retrieved and the moment describes the retrieval rather than the indexing.
     */
    @Test
    void aSourceReadFromTheStoreKeepsItsMoment() {
        final SourceDocumentEntity entity = entity(this.notebookId, "stored text");
        final Instant readBefore = NOW.minusSeconds(86_400);
        when(this.sourceDocumentRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.sourceDocumentMapper.readPayload(entity)).thenReturn(new DocumentPayload("Page",
                SourceKind.WEB, "https://example.org", DocumentStatus.INDEXING, 0,
                DocumentFailure.NONE, readBefore));

        this.service.completeIndexing(this.userId, entity.getId(), "Page", 42, "stored text", false);

        final DocumentPayload written = writtenPayload();
        assertThat(written.indexedAt()).isEqualTo(readBefore);
        assertThat(written.status()).isEqualTo(DocumentStatus.READY);
        assertThat(written.tokenCount()).isEqualTo(42);
    }

    /**
     * Verifies that a source that was actually retrieved is stamped with the moment it was read, and
     * that the text it was read as is stored with it.
     */
    @Test
    void aSourceThatWasRetrievedIsStamped() {
        final SourceDocumentEntity entity = entity(this.notebookId, null);
        when(this.sourceDocumentRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.sourceDocumentMapper.readPayload(entity))
                .thenReturn(payload(DocumentStatus.INDEXING, "Page"));

        this.service.completeIndexing(this.userId, entity.getId(), "Entropy explained", 42,
                "Entropy never decreases.", true);

        assertThat(writtenPayload().indexedAt()).isEqualTo(NOW);
        assertThat(writtenPayload().displayName()).isEqualTo("Entropy explained");
        assertThat(entity.getExtractedText()).isEqualTo("Entropy never decreases.");
    }

    /**
     * Verifies that a source which could not be read is stored with the reason, which is what the
     * user is shown.
     */
    @Test
    void aSourceThatCouldNotBeReadIsStoredWithItsReason() {
        final SourceDocumentEntity entity = entity(this.notebookId, null);
        when(this.sourceDocumentRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.sourceDocumentMapper.readPayload(entity))
                .thenReturn(payload(DocumentStatus.INDEXING, "Page"));

        this.service.failIndexing(this.userId, entity.getId(), DocumentFailure.BLOCKED);

        assertThat(writtenPayload().failure()).isEqualTo(DocumentFailure.BLOCKED);
        assertThat(writtenPayload().status()).isEqualTo(DocumentStatus.ERROR);
    }

    /**
     * Verifies that a source of another account cannot be read or written at all, whichever step of
     * the pipeline asks for it.
     */
    @Test
    void aSourceOfAnotherAccountIsOutOfReach() {
        final UUID sourceId = UUID.randomUUID();
        when(this.sourceDocumentRepository.findByIdAndUserId(sourceId, this.userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.beginIndexing(this.userId, sourceId))
                .isInstanceOf(SourceNotFoundException.class);
        assertThatThrownBy(() ->
                this.service.completeIndexing(this.userId, sourceId, "n", 1, "t", true))
                .isInstanceOf(SourceNotFoundException.class);
        assertThatThrownBy(() ->
                this.service.failIndexing(this.userId, sourceId, DocumentFailure.UNEXPECTED))
                .isInstanceOf(SourceNotFoundException.class);
    }

    /**
     * Verifies that the sources of a notebook of another account are not listed.
     */
    @Test
    void theSourcesOfAForeignNotebookAreNotListed() {
        final UUID foreign = UUID.randomUUID();
        when(this.notebookRepository.findByIdAndUserId(foreign, this.userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.list(this.userId, foreign))
                .isInstanceOf(NotebookNotFoundException.class);
        assertThatThrownBy(() -> this.service.texts(this.userId, foreign))
                .isInstanceOf(NotebookNotFoundException.class);
        assertThatThrownBy(() -> this.service.fingerprintOfSources(this.userId, foreign))
                .isInstanceOf(NotebookNotFoundException.class);
    }

    /**
     * Reads the stored part the service wrote most recently.
     *
     * @return the payload that was written
     */
    private DocumentPayload writtenPayload() {
        final ArgumentCaptor<DocumentPayload> payload = ArgumentCaptor.forClass(DocumentPayload.class);
        verify(this.sourceDocumentMapper, atLeastOnce()).writePayload(payload.capture());
        return payload.getValue();
    }

    /**
     * Builds a stored source of the account of the cases.
     *
     * @param notebookId    notebook the source belongs to
     * @param extractedText text the source was read as, or {@code null} if it was never read
     * @return the stored source
     */
    private SourceDocumentEntity entity(final UUID notebookId, final String extractedText) {
        final SourceDocumentEntity entity = new SourceDocumentEntity(UUID.randomUUID(), this.userId,
                notebookId, NOW.minusSeconds(3_600), UUID.randomUUID().toString(), new byte[]{1},
                new byte[]{2}, PayloadSchemaVersion.CURRENT);
        entity.setExtractedText(extractedText);
        return entity;
    }

    /**
     * Builds the stored part of a source in one stage.
     *
     * @param status      stage the source has reached
     * @param displayName name the source is listed under
     * @return the stored part
     */
    private static DocumentPayload payload(final DocumentStatus status, final String displayName) {
        return new DocumentPayload(displayName, SourceKind.WEB, "https://example.org", status, 0,
                status == DocumentStatus.ERROR ? DocumentFailure.UNREACHABLE : DocumentFailure.NONE,
                Instant.EPOCH);
    }

    /**
     * Builds the record a mapper would produce for a stored source.
     *
     * @param entity  stored source
     * @param payload stored part of the source
     * @return the record describing that source
     */
    private static SourceDocument sourceOf(final SourceDocumentEntity entity,
                                           final DocumentPayload payload) {
        return new SourceDocument(entity.getId(), entity.getNotebookId(), entity.getUserId(),
                payload.displayName(), payload.kind(), payload.origin(), payload.status(),
                payload.tokenCount(), payload.failure(),
                Instant.EPOCH.equals(payload.indexedAt()) ? null : payload.indexedAt(),
                entity.getCreatedAt());
    }
}
