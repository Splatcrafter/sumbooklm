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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ChatTurn;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatMessage;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatMessagePayload;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionEntity;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionMapper;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionPayload;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionRepository;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookEntity;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookRepository;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises how the conversations of one notebook are read and written.
 *
 * <h2>What the Cases Watch</h2>
 * A conversation is reached through a notebook, and both have to belong to the account asking. The
 * name of a conversation is derived from the first question and never again, so a question that is
 * too long has to be cut and a second question must not rename anything. And what is sent to a model
 * is the most recent part of the transcript rather than all of it, which is a rule with an edge that
 * only shows up once a conversation is long.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatSessionServiceTest {

    /**
     * Moment every case is answered at.
     */
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Store of the notebooks.
     */
    private NotebookRepository notebookRepository;

    /**
     * Store of the conversations.
     */
    private ChatSessionRepository chatSessionRepository;

    /**
     * Reader of the stored part of a conversation.
     */
    private ChatSessionMapper chatSessionMapper;

    /**
     * Service under test.
     */
    private ChatSessionService service;

    /**
     * Account the conversations of the cases belong to.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Notebook the conversations of the cases belong to.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatSessionServiceTest() {
    }

    /**
     * Builds the service and everything it reads and writes through.
     */
    @BeforeEach
    void setUp() {
        this.notebookRepository = mock(NotebookRepository.class);
        this.chatSessionRepository = mock(ChatSessionRepository.class);
        this.chatSessionMapper = mock(ChatSessionMapper.class);
        this.service = new ChatSessionService(this.notebookRepository, this.chatSessionRepository,
                this.chatSessionMapper, Clock.fixed(NOW, ZoneOffset.UTC));

        when(this.chatSessionMapper.writePayload(any())).thenReturn(new byte[]{1, 2});
        when(this.notebookRepository.touch(eq(this.notebookId), eq(this.userId), any())).thenReturn(1);
        when(this.notebookRepository.findByIdAndUserId(this.notebookId, this.userId))
                .thenReturn(Optional.of(new NotebookEntity(this.notebookId, this.userId, NOW, NOW,
                        new byte[]{1}, PayloadSchemaVersion.CURRENT)));
    }

    /**
     * Verifies that starting a conversation in a notebook of another account is refused before
     * anything is written.
     */
    @Test
    void aConversationCannotBeStartedInANotebookOfAnotherAccount() {
        final UUID foreign = UUID.randomUUID();
        when(this.notebookRepository.touch(eq(foreign), eq(this.userId), any())).thenReturn(0);

        assertThatThrownBy(() -> this.service.create(this.userId, foreign))
                .isInstanceOf(NotebookNotFoundException.class);
        verify(this.chatSessionRepository, never()).save(any());
    }

    /**
     * Verifies that a conversation is started empty, without a name and without messages, because
     * both come from the first question.
     */
    @Test
    void aConversationIsStartedEmpty() {
        when(this.chatSessionRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0, ChatSessionEntity.class));
        when(this.chatSessionMapper.toDomain(any(ChatSessionEntity.class)))
                .thenAnswer(invocation -> sessionOf(
                        invocation.getArgument(0, ChatSessionEntity.class), "", List.of()));

        final ChatSession session = this.service.create(this.userId, this.notebookId);

        assertThat(session.title()).isEmpty();
        assertThat(session.messages()).isEmpty();
        assertThat(session.createdAt()).isEqualTo(NOW);
        verify(this.chatSessionMapper).writePayload(ChatSessionPayload.empty());
    }

    /**
     * Verifies that a conversation of another notebook is answered as one that does not exist, even
     * where it belongs to the account asking, so that a conversation cannot be reached through a
     * notebook it is not part of.
     */
    @Test
    void aConversationOfAnotherNotebookDoesNotExist() {
        final ChatSessionEntity elsewhere = entity(UUID.randomUUID());
        when(this.chatSessionRepository.findByIdAndUserId(elsewhere.getId(), this.userId))
                .thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() ->
                this.service.conversation(this.userId, this.notebookId, elsewhere.getId()))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    /**
     * Verifies that the first question names the conversation, so that a list of them can be read
     * without opening each.
     */
    @Test
    void theFirstQuestionNamesTheConversation() {
        final ChatSessionEntity entity = entity(this.notebookId);
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.chatSessionMapper.readPayload(entity)).thenReturn(ChatSessionPayload.empty());

        this.service.beginTurn(this.userId, this.notebookId, entity.getId(), "  What is entropy?  ");

        final ChatSessionPayload written = writtenPayload();
        assertThat(written.title()).isEqualTo("What is entropy?");
        assertThat(written.messages()).extracting(ChatMessagePayload::text)
                .containsExactly("  What is entropy?  ");
    }

    /**
     * Verifies that a question too long to be a name is cut and marked as cut, rather than being
     * stored whole where a list is expected.
     */
    @Test
    void aQuestionTooLongToBeANameIsCut() {
        final ChatSessionEntity entity = entity(this.notebookId);
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.chatSessionMapper.readPayload(entity)).thenReturn(ChatSessionPayload.empty());

        this.service.beginTurn(this.userId, this.notebookId, entity.getId(),
                "What does the second chapter say about entropy and about the way it is measured in "
                        + "an isolated system over time?");

        final String title = writtenPayload().title();
        assertThat(title).hasSizeLessThanOrEqualTo(80).endsWith("...");
    }

    /**
     * Verifies that a later question does not rename a conversation, because the name is what a
     * reader recognises it by.
     */
    @Test
    void aLaterQuestionDoesNotRenameTheConversation() {
        final ChatSessionEntity entity = entity(this.notebookId);
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.chatSessionMapper.readPayload(entity)).thenReturn(new ChatSessionPayload(
                "What is entropy?",
                List.of(new ChatMessagePayload(ChatRole.USER, "What is entropy?", NOW))));

        this.service.beginTurn(this.userId, this.notebookId, entity.getId(), "And the second law?");

        assertThat(writtenPayload().title()).isEqualTo("What is entropy?");
    }

    /**
     * Verifies that the question of a turn is not part of the conversation that is sent with it,
     * because it is asked separately and would otherwise be sent twice.
     */
    @Test
    void theQuestionIsNotPartOfTheHistorySentWithIt() {
        final ChatSessionEntity entity = entity(this.notebookId);
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.chatSessionMapper.readPayload(entity)).thenReturn(new ChatSessionPayload("T",
                List.of(new ChatMessagePayload(ChatRole.USER, "What is entropy?", NOW),
                        new ChatMessagePayload(ChatRole.ASSISTANT, "A measure of disorder.", NOW))));

        final ChatTurnContext context = this.service.beginTurn(
                this.userId, this.notebookId, entity.getId(), "And the second law?");

        assertThat(context.history()).extracting(ChatTurn::text)
                .containsExactly("What is entropy?", "A measure of disorder.");
        assertThat(context.question()).isEqualTo("And the second law?");
        assertThat(context.sessionId()).isEqualTo(entity.getId());
        assertThat(context.notebookId()).isEqualTo(this.notebookId);
    }

    /**
     * Verifies that a long conversation is sent as its most recent messages only, because a follow
     * up question refers to what was said last and a request has a size.
     */
    @Test
    void aLongConversationIsSentAsItsMostRecentMessages() {
        final ChatSessionEntity entity = entity(this.notebookId);
        final List<ChatMessagePayload> messages = new ArrayList<>();
        for (int message = 0; message < 25; message += 1) {
            messages.add(new ChatMessagePayload(ChatRole.USER, "Message " + message, NOW));
        }
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.chatSessionMapper.readPayload(entity))
                .thenReturn(new ChatSessionPayload("T", messages));

        final ChatTurnContext context = this.service.beginTurn(
                this.userId, this.notebookId, entity.getId(), "And now?");

        assertThat(context.history()).hasSize(10);
        assertThat(context.history().getLast().text()).isEqualTo("Message 24");
        assertThat(context.history().getFirst().text()).isEqualTo("Message 15");
    }

    /**
     * Verifies that a turn started in a conversation of another notebook is refused, so that a
     * question cannot be answered from the sources of a notebook the conversation is not in.
     */
    @Test
    void aTurnInAConversationOfAnotherNotebookIsRefused() {
        final ChatSessionEntity elsewhere = entity(UUID.randomUUID());
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(elsewhere.getId(), this.userId))
                .thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> this.service.beginTurn(
                this.userId, this.notebookId, elsewhere.getId(), "What is entropy?"))
                .isInstanceOf(ChatSessionNotFoundException.class);
        verify(this.chatSessionMapper, never()).writePayload(any());
    }

    /**
     * Verifies that a finished answer is appended to the conversation and moves the moment it was
     * last spoken in, which is what the list of conversations is ordered by.
     */
    @Test
    void aFinishedAnswerIsAppended() {
        final ChatSessionEntity entity = entity(this.notebookId);
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(entity.getId(), this.userId))
                .thenReturn(Optional.of(entity));
        when(this.chatSessionMapper.readPayload(entity)).thenReturn(new ChatSessionPayload("T",
                List.of(new ChatMessagePayload(ChatRole.USER, "What is entropy?", NOW))));

        this.service.recordAnswer(this.userId, entity.getId(), "A measure of disorder.");

        final ChatSessionPayload written = writtenPayload();
        assertThat(written.messages()).extracting(ChatMessagePayload::role)
                .containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
        assertThat(written.messages().getLast().text()).isEqualTo("A measure of disorder.");
        assertThat(entity.getLastMessageAt()).isEqualTo(NOW);
    }

    /**
     * Verifies that an answer to a conversation that no longer exists is refused rather than being
     * written somewhere else, which is what a reader who removed it mid answer produces.
     */
    @Test
    void anAnswerToARemovedConversationIsRefused() {
        final UUID sessionId = UUID.randomUUID();
        when(this.chatSessionRepository.findForUpdateByIdAndUserId(sessionId, this.userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.recordAnswer(this.userId, sessionId, "An answer."))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    /**
     * Verifies that removing a conversation of another notebook removes nothing.
     */
    @Test
    void removingAConversationOfAnotherNotebookRemovesNothing() {
        final ChatSessionEntity elsewhere = entity(UUID.randomUUID());
        when(this.chatSessionRepository.findByIdAndUserId(elsewhere.getId(), this.userId))
                .thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() ->
                this.service.delete(this.userId, this.notebookId, elsewhere.getId()))
                .isInstanceOf(ChatSessionNotFoundException.class);
        verify(this.chatSessionRepository, never()).delete(any());
    }

    /**
     * Verifies that listing the conversations of a notebook of another account is refused rather
     * than answered with nothing, so that the answer does not say whether it holds any.
     */
    @Test
    void listingTheConversationsOfAForeignNotebookIsRefused() {
        final UUID foreign = UUID.randomUUID();
        when(this.notebookRepository.findByIdAndUserId(foreign, this.userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.conversations(this.userId, foreign))
                .isInstanceOf(NotebookNotFoundException.class);
    }

    /**
     * Reads the stored part the service wrote most recently.
     *
     * @return the payload that was written
     */
    private ChatSessionPayload writtenPayload() {
        final ArgumentCaptor<ChatSessionPayload> payload =
                ArgumentCaptor.forClass(ChatSessionPayload.class);
        verify(this.chatSessionMapper).writePayload(payload.capture());
        return payload.getValue();
    }

    /**
     * Builds a stored conversation of the account of the cases.
     *
     * @param notebookId notebook the conversation belongs to
     * @return the stored conversation
     */
    private ChatSessionEntity entity(final UUID notebookId) {
        return new ChatSessionEntity(UUID.randomUUID(), this.userId, notebookId,
                NOW.minusSeconds(3_600), NOW.minusSeconds(60), new byte[]{1},
                PayloadSchemaVersion.CURRENT);
    }

    /**
     * Builds the record a mapper would produce for a stored conversation.
     *
     * @param entity   stored conversation
     * @param title    name of the conversation
     * @param messages messages of the conversation
     * @return the record describing that conversation
     */
    private static ChatSession sessionOf(final ChatSessionEntity entity,
                                         final String title,
                                         final List<ChatMessage> messages) {
        return new ChatSession(entity.getId(), entity.getNotebookId(), title, messages,
                entity.getCreatedAt(), entity.getLastMessageAt());
    }
}
