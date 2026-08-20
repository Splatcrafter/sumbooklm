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

package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the stored transcript of a conversation.
 *
 * <h2>Why Appending Returns a New Payload</h2>
 * A turn is written in two steps, the question before the model is asked and the answer once the
 * stream has finished, and the two are separate transactions. Appending therefore has to leave the
 * payload it was read from untouched, or a failure between the steps would carry half of a turn into
 * the row.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatSessionPayloadTest {

    /**
     * Moment the messages of the cases were written at.
     */
    private static final Instant WHEN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatSessionPayloadTest() {
    }

    /**
     * Verifies that a fresh conversation carries neither a name nor a message, which is the state a
     * client opens one in.
     */
    @Test
    void aFreshConversationIsEmpty() {
        assertThat(ChatSessionPayload.empty().title()).isEmpty();
        assertThat(ChatSessionPayload.empty().messages()).isEmpty();
    }

    /**
     * Verifies that appending a message leaves the payload it was appended to untouched.
     */
    @Test
    void appendingLeavesTheOriginalUntouched() {
        final ChatSessionPayload empty = ChatSessionPayload.empty();

        final ChatSessionPayload appended =
                empty.withMessage(new ChatMessagePayload(ChatRole.USER, "What is entropy?", WHEN));

        assertThat(empty.messages()).isEmpty();
        assertThat(appended.messages()).hasSize(1);
    }

    /**
     * Verifies that appended messages keep the order they were appended in, which is what makes the
     * stored payload readable as a conversation.
     */
    @Test
    void theOrderOfTheMessagesIsKept() {
        final ChatSessionPayload payload = ChatSessionPayload.empty()
                .withMessage(new ChatMessagePayload(ChatRole.USER, "First", WHEN))
                .withMessage(new ChatMessagePayload(ChatRole.ASSISTANT, "Second", WHEN))
                .withMessage(new ChatMessagePayload(ChatRole.USER, "Third", WHEN));

        assertThat(payload.messages()).extracting(ChatMessagePayload::text)
                .containsExactly("First", "Second", "Third");
    }

    /**
     * Verifies that naming a conversation keeps its messages, because the name is derived from the
     * first question and is set in the same step the question is appended in.
     */
    @Test
    void namingAConversationKeepsItsMessages() {
        final ChatSessionPayload named = ChatSessionPayload.empty()
                .withMessage(new ChatMessagePayload(ChatRole.USER, "What is entropy?", WHEN))
                .withTitle("What is entropy?");

        assertThat(named.title()).isEqualTo("What is entropy?");
        assertThat(named.messages()).hasSize(1);
    }

    /**
     * Verifies that the stored messages are copied, so that a list handed in cannot be changed
     * behind the payload that was built from it.
     */
    @Test
    void theMessagesAreCopied() {
        final List<ChatMessagePayload> messages = new ArrayList<>();
        messages.add(new ChatMessagePayload(ChatRole.USER, "What is entropy?", WHEN));

        final ChatSessionPayload payload = new ChatSessionPayload("Entropy", messages);
        messages.clear();

        assertThat(payload.messages()).hasSize(1);
    }

    /**
     * Verifies that the transcript cannot be added to through the list it hands out.
     */
    @Test
    void theTranscriptCannotBeAddedTo() {
        final ChatSessionPayload payload = ChatSessionPayload.empty();

        assertThatThrownBy(() -> payload.messages()
                .add(new ChatMessagePayload(ChatRole.USER, "x", WHEN)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Verifies that a payload without a name or without a list of messages is refused, because
     * neither could be encoded.
     */
    @Test
    void aMissingNameOrListIsRefused() {
        assertThatThrownBy(() -> new ChatSessionPayload(null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new ChatSessionPayload("t", null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that a message of a transcript may not be absent, so that no hole can be written into
     * a stored conversation.
     */
    @Test
    void aMissingMessageIsRefused() {
        final List<ChatMessagePayload> withHole = new ArrayList<>();
        withHole.add(null);

        assertThatThrownBy(() -> new ChatSessionPayload("t", withHole))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that a stored message states its author, its text and when it was written, and that
     * none of the three may be absent.
     */
    @Test
    void aStoredMessageStatesItsParts() {
        final ChatMessagePayload message =
                new ChatMessagePayload(ChatRole.ASSISTANT, "A measure of disorder.", WHEN);

        assertThat(message.role()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(message.text()).isEqualTo("A measure of disorder.");
        assertThat(message.createdAt()).isEqualTo(WHEN);
        assertThatThrownBy(() -> new ChatMessagePayload(null, "t", WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("role");
        assertThatThrownBy(() -> new ChatMessagePayload(ChatRole.USER, null, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
        assertThatThrownBy(() -> new ChatMessagePayload(ChatRole.USER, "t", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("createdAt");
    }
}
