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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ChatTurn;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises what one turn of a conversation is described by.
 *
 * <h2>Why the Conversation Is Copied</h2>
 * The turn is built inside a transaction and answered outside of it, on another thread. Its
 * conversation therefore has to be a copy, or the answer would be written from a list that a later
 * turn is still appending to.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatTurnContextTest {

    /**
     * Conversation the turn of the cases belongs to.
     */
    private final UUID sessionId = UUID.randomUUID();

    /**
     * Notebook the turn of the cases belongs to.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Creates the test class.
     */
    ChatTurnContextTest() {
    }

    /**
     * Verifies that the conversation is copied, so that the answer is written from what was read.
     */
    @Test
    void theConversationIsCopied() {
        final List<ChatTurn> history = new ArrayList<>();
        history.add(new ChatTurn(ChatRole.USER, "What is entropy?"));

        final ChatTurnContext context =
                new ChatTurnContext(this.sessionId, this.notebookId, "And the second law?", history);
        history.clear();

        assertThat(context.history()).hasSize(1);
    }

    /**
     * Verifies that the first turn of a conversation is described without any history, which is the
     * state every conversation starts in.
     */
    @Test
    void theFirstTurnHasNoHistory() {
        final ChatTurnContext context =
                new ChatTurnContext(this.sessionId, this.notebookId, "What is entropy?", List.of());

        assertThat(context.history()).isEmpty();
        assertThat(context.question()).isEqualTo("What is entropy?");
    }

    /**
     * Verifies that no part of a turn may be absent, because the answer is written from all of them
     * on a thread that can no longer ask.
     */
    @Test
    void noPartMayBeAbsent() {
        assertThatThrownBy(() ->
                new ChatTurnContext(null, this.notebookId, "q", List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("sessionId");
        assertThatThrownBy(() ->
                new ChatTurnContext(this.sessionId, null, "q", List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("notebookId");
        assertThatThrownBy(() ->
                new ChatTurnContext(this.sessionId, this.notebookId, null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("question");
        assertThatThrownBy(() ->
                new ChatTurnContext(this.sessionId, this.notebookId, "q", null))
                .isInstanceOf(NullPointerException.class);
    }
}
