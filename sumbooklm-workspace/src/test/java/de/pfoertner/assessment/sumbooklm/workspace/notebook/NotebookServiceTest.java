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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.Notebook;
import de.pfoertner.assessment.sumbooklm.domain.workspace.NotebookSummary;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionRepository;
import de.pfoertner.assessment.sumbooklm.persistence.document.NotebookSourceCount;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentRepository;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookEntity;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookMapper;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookPayload;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookRepository;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises how the notebooks of one account are read and changed.
 *
 * <h2>Two Things Every Case Watches</h2>
 * The first is ownership: every read and every write names the account as well as the notebook, and
 * a notebook of somebody else has to be answered as one that does not exist rather than as one that
 * is forbidden. The second is what a partial change touches, because the stored part of a notebook
 * is written back as a whole and a rename that also cleared a summary would look like a rename.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookServiceTest {

    /**
     * Moment every case is answered at.
     */
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Store of the notebooks.
     */
    private NotebookRepository notebookRepository;

    /**
     * Store of the sources, which the count of a notebook is read from.
     */
    private SourceDocumentRepository sourceDocumentRepository;

    /**
     * Store of the conversations, which a removal clears as well.
     */
    private ChatSessionRepository chatSessionRepository;

    /**
     * Reader of the stored part of a notebook.
     */
    private NotebookMapper notebookMapper;

    /**
     * Channel a removal is announced over.
     */
    private ApplicationEventPublisher eventPublisher;

    /**
     * Service under test.
     */
    private NotebookService service;

    /**
     * Account the notebooks of the cases belong to.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    NotebookServiceTest() {
    }

    /**
     * Builds the service and everything it reads and writes through.
     */
    @BeforeEach
    void setUp() {
        this.notebookRepository = mock(NotebookRepository.class);
        this.sourceDocumentRepository = mock(SourceDocumentRepository.class);
        this.chatSessionRepository = mock(ChatSessionRepository.class);
        this.notebookMapper = mock(NotebookMapper.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.service = new NotebookService(this.notebookRepository, this.sourceDocumentRepository,
                this.chatSessionRepository, this.notebookMapper, this.eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(this.notebookMapper.writePayload(any())).thenReturn(new byte[]{1, 2});
        when(this.notebookMapper.toDomain(any(NotebookEntity.class), any(NotebookPayload.class), anyLong()))
                .thenAnswer(invocation -> notebookOf(
                        invocation.getArgument(0, NotebookEntity.class),
                        invocation.getArgument(1, NotebookPayload.class),
                        invocation.getArgument(2, Long.class)));
    }

    /**
     * Verifies that every notebook of an account is listed with the number of sources it holds, and
     * that a notebook the count query said nothing about is listed as holding none.
     */
    @Test
    void everyNotebookIsListedWithItsNumberOfSources() {
        final NotebookEntity withSources = entity();
        final NotebookEntity withoutSources = entity();
        when(this.notebookRepository.findAllByUserIdOrderByLastActivityAtDesc(this.userId))
                .thenReturn(List.of(withSources, withoutSources));
        when(this.sourceDocumentRepository.countPerNotebook(this.userId))
                .thenReturn(List.of(count(withSources.getId(), 3L)));
        when(this.notebookMapper.toDomain(any(NotebookEntity.class), anyLong()))
                .thenAnswer(invocation -> notebookOf(
                        invocation.getArgument(0, NotebookEntity.class),
                        new NotebookPayload("Thermodynamics", false, "", "", ""),
                        invocation.getArgument(1, Long.class)));

        final List<Notebook> notebooks = this.service.list(this.userId);

        assertThat(notebooks).extracting(Notebook::sourceCount).containsExactly(3L, 0L);
    }

    /**
     * Verifies that a notebook of another account is answered as one that does not exist, so that
     * the answer does not say whether it exists at all.
     */
    @Test
    void aNotebookOfAnotherAccountDoesNotExist() {
        final UUID notebookId = UUID.randomUUID();
        when(this.notebookRepository.findByIdAndUserId(notebookId, this.userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.get(this.userId, notebookId))
                .isInstanceOf(NotebookNotFoundException.class)
                .hasMessageContaining(notebookId.toString());
    }

    /**
     * Verifies that a notebook is created under the name it was given without the whitespace around
     * it, unpinned, without an icon and without sources.
     */
    @Test
    void aNotebookIsCreatedUnderItsStrippedName() {
        when(this.notebookRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0, NotebookEntity.class));

        final Notebook created = this.service.create(this.userId, "  Thermodynamics  ");

        assertThat(created.title()).isEqualTo("Thermodynamics");
        assertThat(created.pinned()).isFalse();
        assertThat(created.topicIcon()).isEmpty();
        assertThat(created.sourceCount()).isZero();
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    /**
     * Verifies that renaming a notebook keeps its pin and its summary, because the stored part is
     * written back as a whole.
     */
    @Test
    void renamingKeepsThePinAndTheSummary() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("Old", true, "@", "About entropy.", "abc123"));

        this.service.update(this.userId, entity.getId(),
                new NotebookUpdateCommand("  New name  ", null));

        final NotebookPayload written = writtenPayload();
        assertThat(written.title()).isEqualTo("New name");
        assertThat(written.pinned()).isTrue();
        assertThat(written.summary()).isEqualTo("About entropy.");
    }

    /**
     * Verifies that pinning a notebook does not count as activity, because the order of the overview
     * is by activity and pinning one would otherwise move every other notebook down.
     */
    @Test
    void pinningIsNotActivity() {
        final NotebookEntity entity = entity();
        final Instant before = entity.getLastActivityAt();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("Thermodynamics", false, "", "", ""));

        this.service.update(this.userId, entity.getId(), new NotebookUpdateCommand(null, true));

        assertThat(entity.getLastActivityAt()).isEqualTo(before);
        assertThat(writtenPayload().pinned()).isTrue();
    }

    /**
     * Verifies that renaming a notebook does count as activity, so that the overview shows what was
     * worked on most recently.
     */
    @Test
    void renamingIsActivity() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("Thermodynamics", false, "", "", ""));

        this.service.update(this.userId, entity.getId(), new NotebookUpdateCommand("New", null));

        assertThat(entity.getLastActivityAt()).isEqualTo(NOW);
    }

    /**
     * Verifies that a change naming no field at all leaves everything as it was, which is what an
     * empty request amounts to.
     */
    @Test
    void aChangeNamingNothingLeavesEverything() {
        final NotebookEntity entity = entity();
        final Instant before = entity.getLastActivityAt();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("Thermodynamics", true, "@", "About entropy.", "abc"));

        this.service.update(this.userId, entity.getId(), new NotebookUpdateCommand(null, null));

        final NotebookPayload written = writtenPayload();
        assertThat(written.title()).isEqualTo("Thermodynamics");
        assertThat(written.pinned()).isTrue();
        assertThat(written.summary()).isEqualTo("About entropy.");
        assertThat(entity.getLastActivityAt()).isEqualTo(before);
    }

    /**
     * Verifies that a notebook which was never summarised is not reported as out of date, because
     * there is nothing that could have become so.
     */
    @Test
    void anUnwrittenSummaryIsNotOutOfDate() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("Thermodynamics", false, "", "", ""));
        when(this.sourceDocumentRepository.findStampsOfNotebook(entity.getId(), this.userId))
                .thenReturn(List.of());

        final NotebookSummary summary = this.service.readSummary(this.userId, entity.getId());

        assertThat(summary.isWritten()).isFalse();
        assertThat(summary.stale()).isFalse();
    }

    /**
     * Verifies that a summary whose sources have changed is reported as out of date rather than
     * being rewritten behind the reader, because writing one costs a request to a model.
     */
    @Test
    void aSummaryOfChangedSourcesIsOutOfDate() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("T", false, "", "About entropy.", "stored-fingerprint"));
        when(this.sourceDocumentRepository.findStampsOfNotebook(entity.getId(), this.userId))
                .thenReturn(List.of());

        final NotebookSummary summary = this.service.readSummary(this.userId, entity.getId());

        assertThat(summary.isWritten()).isTrue();
        assertThat(summary.stale()).isTrue();
    }

    /**
     * Verifies that a summary written for the sources the notebook still holds is current.
     */
    @Test
    void aSummaryOfTheCurrentSourcesIsNotOutOfDate() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("T", false, "", "About entropy.", ""));
        when(this.sourceDocumentRepository.findStampsOfNotebook(entity.getId(), this.userId))
                .thenReturn(List.of());

        assertThat(this.service.readSummary(this.userId, entity.getId()).stale()).isFalse();
    }

    /**
     * Verifies that storing a summary writes it together with the fingerprint of the sources it was
     * written from, so that it can be judged later.
     */
    @Test
    void aStoredSummaryIsWrittenWithItsFingerprint() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("T", true, "@", "", ""));
        when(this.sourceDocumentRepository.findStampsOfNotebook(entity.getId(), this.userId))
                .thenReturn(List.of());

        final NotebookSummary stored = this.service.storeSummary(
                this.userId, entity.getId(), "About entropy.", "");

        final NotebookPayload written = writtenPayload();
        assertThat(written.summary()).isEqualTo("About entropy.");
        assertThat(written.summaryFingerprint()).isEmpty();
        assertThat(written.title()).isEqualTo("T");
        assertThat(stored.stale()).isFalse();
        assertThat(entity.getPayloadVersion()).isEqualTo(PayloadSchemaVersion.CURRENT);
    }

    /**
     * Verifies that a summary written from sources that changed while it was being written is
     * reported as out of date at once, rather than looking current until the next change.
     */
    @Test
    void aSummaryOvertakenWhileItWasWrittenIsOutOfDateAtOnce() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.notebookMapper.readPayload(entity))
                .thenReturn(new NotebookPayload("T", false, "", "", ""));
        when(this.sourceDocumentRepository.findStampsOfNotebook(entity.getId(), this.userId))
                .thenReturn(List.of());

        final NotebookSummary stored = this.service.storeSummary(
                this.userId, entity.getId(), "About entropy.", "fingerprint-of-then");

        assertThat(stored.stale()).isTrue();
    }

    /**
     * Verifies that removing a notebook removes its sources and its conversations as well and then
     * announces the removal, so that the retrieval index can be cleared of what is gone.
     */
    @Test
    void removingANotebookRemovesWhatBelongsToIt() {
        final NotebookEntity entity = entity();
        when(this.notebookRepository.findByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));

        this.service.delete(this.userId, entity.getId());

        verify(this.sourceDocumentRepository).deleteByNotebookIdAndUserId(entity.getId(), this.userId);
        verify(this.chatSessionRepository).deleteByNotebookIdAndUserId(entity.getId(), this.userId);
        verify(this.notebookRepository).delete(entity);
        verify(this.eventPublisher).publishEvent(new NotebookRemovedEvent(entity.getId()));
    }

    /**
     * Verifies that removing a notebook of another account removes nothing and announces nothing.
     */
    @Test
    void removingANotebookOfAnotherAccountRemovesNothing() {
        final UUID notebookId = UUID.randomUUID();
        when(this.notebookRepository.findByIdAndUserId(notebookId, this.userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.delete(this.userId, notebookId))
                .isInstanceOf(NotebookNotFoundException.class);
        verify(this.sourceDocumentRepository, never()).deleteByNotebookIdAndUserId(any(), any());
        verify(this.eventPublisher, never()).publishEvent(any(Object.class));
    }

    /**
     * Reads the stored part the service wrote most recently.
     *
     * @return the payload that was written
     */
    private NotebookPayload writtenPayload() {
        final ArgumentCaptor<NotebookPayload> payload = ArgumentCaptor.forClass(NotebookPayload.class);
        verify(this.notebookMapper).writePayload(payload.capture());
        return payload.getValue();
    }

    /**
     * Builds a stored notebook of the account of the cases.
     *
     * @return the stored notebook
     */
    private NotebookEntity entity() {
        return new NotebookEntity(UUID.randomUUID(), this.userId, NOW.minusSeconds(86_400),
                NOW.minusSeconds(3_600), new byte[]{1}, PayloadSchemaVersion.CURRENT);
    }

    /**
     * Builds the record a mapper would produce for a stored notebook.
     *
     * @param entity      stored notebook
     * @param payload     stored part of the notebook
     * @param sourceCount number of sources the notebook holds
     * @return the record describing that notebook
     */
    private static Notebook notebookOf(final NotebookEntity entity,
                                       final NotebookPayload payload,
                                       final long sourceCount) {
        return new Notebook(entity.getId(), entity.getUserId(), payload.title(), payload.pinned(),
                payload.topicIcon(), entity.getCreatedAt(), entity.getLastActivityAt(), sourceCount);
    }

    /**
     * Builds one row of the query that counts the sources per notebook.
     *
     * @param notebookId  notebook the row describes
     * @param sourceCount number of sources it holds
     * @return the row
     */
    private static NotebookSourceCount count(final UUID notebookId, final long sourceCount) {
        return new NotebookSourceCount() {

            @Override
            public UUID getNotebookId() {
                return notebookId;
            }

            @Override
            public long getSourceCount() {
                return sourceCount;
            }
        };
    }
}
