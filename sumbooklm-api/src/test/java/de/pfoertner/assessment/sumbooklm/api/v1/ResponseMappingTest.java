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

package de.pfoertner.assessment.sumbooklm.api.v1;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.api.v1.auth.AuthenticatedUser;
import de.pfoertner.assessment.sumbooklm.api.v1.chat.ChatConversationResponse;
import de.pfoertner.assessment.sumbooklm.api.v1.chat.ChatMessageResponse;
import de.pfoertner.assessment.sumbooklm.api.v1.chat.ChatSourceResponse;
import de.pfoertner.assessment.sumbooklm.api.v1.chat.ChatSummaryResponse;
import de.pfoertner.assessment.sumbooklm.api.v1.notebook.NotebookResponse;
import de.pfoertner.assessment.sumbooklm.api.v1.notebook.NotebookSummaryResponse;
import de.pfoertner.assessment.sumbooklm.api.v1.source.SourceResponse;
import de.pfoertner.assessment.sumbooklm.domain.user.AccountActivity;
import de.pfoertner.assessment.sumbooklm.domain.user.UserAccount;
import de.pfoertner.assessment.sumbooklm.domain.user.UserProfile;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatMessage;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentStatus;
import de.pfoertner.assessment.sumbooklm.domain.workspace.Notebook;
import de.pfoertner.assessment.sumbooklm.domain.workspace.NotebookSummary;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceDocument;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import de.pfoertner.assessment.sumbooklm.workspace.chat.RetrievedSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises what the API hands out for what the application holds.
 *
 * <h2>What Is Watched</h2>
 * Two things. What crosses, because a response is where a value stops being internal: the account of
 * a notebook stays behind, and so does the network address a session was opened from. And what is
 * left out on purpose: a list of conversations carries the number of messages rather than the
 * messages, because the overview would otherwise hand out every transcript of every conversation
 * each time it is opened.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ResponseMappingTest {

    /**
     * Moment the records of the cases were created at.
     */
    private static final Instant CREATED = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Moment the records of the cases were last touched at.
     */
    private static final Instant TOUCHED = Instant.parse("2026-08-20T11:00:00Z");

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ResponseMappingTest() {
    }

    /**
     * Verifies that a notebook is handed out with everything the overview shows and without the
     * account it belongs to, which the caller already is.
     */
    @Test
    void aNotebookIsHandedOutWithoutItsOwner() {
        final UUID id = UUID.randomUUID();
        final UUID ownerId = UUID.randomUUID();
        final Notebook notebook =
                new Notebook(id, ownerId, "Thermodynamics", true, "@", CREATED, TOUCHED, 3L);

        final NotebookResponse response = NotebookResponse.from(notebook);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.title()).isEqualTo("Thermodynamics");
        assertThat(response.pinned()).isTrue();
        assertThat(response.topicIcon()).isEqualTo("@");
        assertThat(response.createdAt()).isEqualTo(CREATED);
        assertThat(response.lastActivityAt()).isEqualTo(TOUCHED);
        assertThat(response.sourceCount()).isEqualTo(3L);
        assertThat(response.toString()).doesNotContain(ownerId.toString());
    }

    /**
     * Verifies that a source is handed out with the stage it reached, the reason it failed and the
     * moment it was read, and without the account it belongs to.
     */
    @Test
    void aSourceIsHandedOutWithItsStageAndItsReason() {
        final UUID id = UUID.randomUUID();
        final UUID notebookId = UUID.randomUUID();
        final UUID ownerId = UUID.randomUUID();
        final SourceDocument source = new SourceDocument(id, notebookId, ownerId, "Entropy explained",
                SourceKind.WEB, "https://example.org", DocumentStatus.ERROR, 0,
                DocumentFailure.BLOCKED, null, CREATED);

        final SourceResponse response = SourceResponse.from(source);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.notebookId()).isEqualTo(notebookId);
        assertThat(response.status()).isEqualTo(DocumentStatus.ERROR);
        assertThat(response.failure()).isEqualTo(DocumentFailure.BLOCKED);
        assertThat(response.indexedAt()).isNull();
        assertThat(response.toString()).doesNotContain(ownerId.toString());
    }

    /**
     * Verifies that a source which was read hands out the moment it was read at, because that is
     * what says how old the material behind an answer is.
     */
    @Test
    void aReadSourceHandsOutWhenItWasRead() {
        final SourceDocument source = new SourceDocument(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Entropy explained", SourceKind.WEB, "https://example.org",
                DocumentStatus.READY, 512, DocumentFailure.NONE, TOUCHED, CREATED);

        final SourceResponse response = SourceResponse.from(source);

        assertThat(response.indexedAt()).isEqualTo(TOUCHED);
        assertThat(response.tokenCount()).isEqualTo(512);
    }

    /**
     * Verifies that a conversation is handed out with its whole transcript, in the order it was held
     * in, because the client that opened it displays exactly that.
     */
    @Test
    void aConversationIsHandedOutWithItsTranscript() {
        final ChatSession session = session(
                new ChatMessage(ChatRole.USER, "What is entropy?", CREATED),
                new ChatMessage(ChatRole.ASSISTANT, "A measure of disorder [1](#source-1).", TOUCHED));

        final ChatConversationResponse response = ChatConversationResponse.from(session);

        assertThat(response.messages()).extracting(ChatMessageResponse::role)
                .containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
        assertThat(response.messages().getLast().text())
                .isEqualTo("A measure of disorder [1](#source-1).");
        assertThat(response.title()).isEqualTo("What is entropy?");
        assertThat(response.lastMessageAt()).isEqualTo(TOUCHED);
    }

    /**
     * Verifies that a listed conversation carries the number of its messages rather than the
     * messages, so that the overview does not hand out every transcript at once.
     */
    @Test
    void aListedConversationCarriesOnlyItsCount() {
        final ChatSession session = session(
                new ChatMessage(ChatRole.USER, "What is entropy?", CREATED),
                new ChatMessage(ChatRole.ASSISTANT, "A measure of disorder.", TOUCHED));

        final ChatSummaryResponse response = ChatSummaryResponse.from(session);

        assertThat(response.messageCount()).isEqualTo(2);
        assertThat(response.toString()).doesNotContain("A measure of disorder.");
    }

    /**
     * Verifies that a conversation nobody has spoken in is handed out as empty rather than left out,
     * because a client that just started one has to be able to open it.
     */
    @Test
    void aConversationWithoutMessagesIsStillHandedOut() {
        final ChatSession session = session();

        assertThat(ChatConversationResponse.from(session).messages()).isEmpty();
        assertThat(ChatSummaryResponse.from(session).messageCount()).isZero();
    }

    /**
     * Verifies that a citable source is handed out under the number the answer cites it by, together
     * with the identity a client resolves it to.
     */
    @Test
    void aCitableSourceIsHandedOutUnderItsNumber() {
        final UUID sourceId = UUID.randomUUID();

        final ChatSourceResponse response =
                ChatSourceResponse.from(new RetrievedSource(2, sourceId, "Thermodynamics.pdf"));

        assertThat(response.number()).isEqualTo(2);
        assertThat(response.sourceDocumentId()).isEqualTo(sourceId);
        assertThat(response.displayName()).isEqualTo("Thermodynamics.pdf");
    }

    /**
     * Verifies that an account is handed out with the name of its user and without the addresses it
     * was used from, which are recorded for a deployment rather than for a client.
     */
    @Test
    void anAccountIsHandedOutWithoutItsAddresses() {
        final UUID id = UUID.randomUUID();
        final UserAccount account = new UserAccount(id, "erik",
                new UserProfile("Erik", "Pfoertner"),
                new AccountActivity(CREATED, "203.0.113.7", TOUCHED, "198.51.100.4"));

        final AuthenticatedUser user = AuthenticatedUser.from(account);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.username()).isEqualTo("erik");
        assertThat(user.firstName()).isEqualTo("Erik");
        assertThat(user.lastName()).isEqualTo("Pfoertner");
        assertThat(user.registeredAt()).isEqualTo(CREATED);
        assertThat(user.lastLoginAt()).isEqualTo(TOUCHED);
        assertThat(user.toString()).doesNotContain("203.0.113.7").doesNotContain("198.51.100.4");
    }

    /**
     * Verifies that a summary is handed out with both of the questions it answers: what was written
     * and whether it still describes the notebook.
     */
    @Test
    void aSummaryIsHandedOutWithBothOfItsAnswers() {
        final UUID notebookId = UUID.randomUUID();

        assertThat(NotebookSummaryResponse.from(
                new NotebookSummary(notebookId, "About entropy.", true)))
                .isEqualTo(new NotebookSummaryResponse("About entropy.", true));
        assertThat(NotebookSummaryResponse.from(new NotebookSummary(notebookId, "", false)))
                .isEqualTo(new NotebookSummaryResponse("", false));
    }

    /**
     * Builds a conversation holding the messages of a case.
     *
     * @param messages messages the conversation holds
     * @return the conversation
     */
    private static ChatSession session(final ChatMessage... messages) {
        return new ChatSession(UUID.randomUUID(), UUID.randomUUID(), "What is entropy?",
                List.of(messages), CREATED, TOUCHED);
    }
}
