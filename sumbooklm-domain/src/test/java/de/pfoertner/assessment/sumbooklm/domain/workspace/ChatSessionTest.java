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

package de.pfoertner.assessment.sumbooklm.domain.workspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules a conversation is created under.
 *
 * <h2>Why the Copy Matters</h2>
 * A session is handed out of the persistence layer and travels through two more before it is
 * written to a response. If the list it carries were the one it was built from, a caller could
 * append to a transcript that has already been read, which is the case the copy in the constructor
 * exists for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatSessionTest {

    /**
     * Point in time every case is built with, chosen so that nothing depends on the current clock.
     */
    private static final Instant WHEN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Identifier of the session under test.
     */
    private final UUID id = UUID.randomUUID();

    /**
     * Identifier of the notebook the session belongs to.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatSessionTest() {
    }

    /**
     * Verifies that the messages are copied, so that changing the list afterwards cannot change a
     * transcript that was already handed out.
     */
    @Test
    void theMessagesAreCopied() {
        final List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatRole.USER, "What is entropy?", WHEN));

        final ChatSession session = new ChatSession(
                this.id, this.notebookId, "Entropy", messages, WHEN, WHEN);
        messages.add(new ChatMessage(ChatRole.ASSISTANT, "A measure of disorder.", WHEN));

        assertThat(session.messages()).hasSize(1);
    }

    /**
     * Verifies that the transcript of a session cannot be added to through the list it hands out.
     */
    @Test
    void theTranscriptCannotBeAddedTo() {
        final ChatSession session = new ChatSession(
                this.id, this.notebookId, "Entropy", List.of(), WHEN, WHEN);

        assertThatThrownBy(() -> session.messages().add(new ChatMessage(ChatRole.USER, "x", WHEN)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Verifies that a conversation nobody has spoken in yet is accepted, because that is the state a
     * session is created in and the one a client opens it in.
     */
    @Test
    void aSessionWithoutMessagesIsAccepted() {
        final ChatSession session = new ChatSession(this.id, this.notebookId, "", List.of(), WHEN, WHEN);

        assertThat(session.messages()).isEmpty();
        assertThat(session.title()).isEmpty();
    }

    /**
     * Verifies that a missing list of messages is refused rather than read as an empty conversation,
     * because the two mean different things to the layer that stored it.
     */
    @Test
    void aMissingListOfMessagesIsRefused() {
        assertThatThrownBy(() -> new ChatSession(this.id, this.notebookId, "T", null, WHEN, WHEN))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that a list holding a missing message is refused, so that a transcript cannot carry a
     * hole where a message was expected.
     */
    @Test
    void aMissingMessageInTheListIsRefused() {
        final List<ChatMessage> withHole = Arrays.asList(
                new ChatMessage(ChatRole.USER, "What is entropy?", WHEN), null);

        assertThatThrownBy(() -> new ChatSession(this.id, this.notebookId, "T", withHole, WHEN, WHEN))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that every other reference is required, so that a session always states who it
     * belongs to and when it was spoken in.
     */
    @Test
    void everyOtherReferenceIsRequired() {
        assertThatThrownBy(() -> new ChatSession(null, this.notebookId, "T", List.of(), WHEN, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> new ChatSession(this.id, null, "T", List.of(), WHEN, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("notebookId");
        assertThatThrownBy(() -> new ChatSession(this.id, this.notebookId, null, List.of(), WHEN, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new ChatSession(this.id, this.notebookId, "T", List.of(), null, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("createdAt");
        assertThatThrownBy(() -> new ChatSession(this.id, this.notebookId, "T", List.of(), WHEN, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("lastMessageAt");
    }

    /**
     * Verifies that the messages keep the order they were given in, which is what makes the
     * transcript readable as a conversation rather than as a set of statements.
     */
    @Test
    void theOrderOfTheMessagesIsKept() {
        final ChatMessage question = new ChatMessage(ChatRole.USER, "What is entropy?", WHEN);
        final ChatMessage answer = new ChatMessage(ChatRole.ASSISTANT, "A measure of disorder.", WHEN);

        final ChatSession session = new ChatSession(
                this.id, this.notebookId, "Entropy", List.of(question, answer), WHEN, WHEN);

        assertThat(session.messages()).containsExactly(question, answer);
    }
}
