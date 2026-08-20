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

package de.pfoertner.assessment.sumbooklm.ai.chat;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules one remembered turn of a conversation is described by.
 *
 * <h2>Why It Exists Next to the Stored Message</h2>
 * A turn is what is sent to a model, which is less than what is stored: no moment and no identity,
 * because neither belongs in a request. The cases below hold that reduction to what a request needs.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatTurnTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatTurnTest() {
    }

    /**
     * Verifies that a turn keeps its author and its text.
     */
    @Test
    void theAuthorAndTheTextAreKept() {
        final ChatTurn turn = new ChatTurn(ChatRole.USER, "What is entropy?");

        assertThat(turn.role()).isEqualTo(ChatRole.USER);
        assertThat(turn.text()).isEqualTo("What is entropy?");
    }

    /**
     * Verifies that a turn without an author or without a text is refused, so that no message of a
     * request can be built without saying who is speaking.
     */
    @Test
    void aTurnWithoutAuthorOrTextIsRefused() {
        assertThatThrownBy(() -> new ChatTurn(null, "t"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("role");
        assertThatThrownBy(() -> new ChatTurn(ChatRole.ASSISTANT, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
    }

    /**
     * Verifies that two turns of equal value are equal, which is what lets the rule bounding a
     * conversation be stated as a comparison of lists.
     */
    @Test
    void turnsOfEqualValueAreEqual() {
        assertThat(new ChatTurn(ChatRole.USER, "What is entropy?"))
                .isEqualTo(new ChatTurn(ChatRole.USER, "What is entropy?"))
                .isNotEqualTo(new ChatTurn(ChatRole.ASSISTANT, "What is entropy?"));
    }
}
