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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules one message of a conversation is created under.
 *
 * <h2>Text as It Was Produced</h2>
 * The record neither trims nor rewrites what it is given. That is worth stating, because the
 * citation markers of an answer are Markdown and a message that quietly stripped them would break
 * the link between a sentence and the source it came from.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatMessageTest {

    /**
     * Point in time every case is built with, chosen so that nothing depends on the current clock.
     */
    private static final Instant WHEN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatMessageTest() {
    }

    /**
     * Verifies that the text is kept exactly as it was generated, including the markup a citation is
     * written as.
     */
    @Test
    void theTextIsKeptAsItWasWritten() {
        final String answer = "Entropy grows [1](#source-1).\n\n  Trailing space kept.  ";

        assertThat(new ChatMessage(ChatRole.ASSISTANT, answer, WHEN).text()).isEqualTo(answer);
    }

    /**
     * Verifies that an empty message is accepted, because an answer that was stopped before its
     * first token still has to be describable.
     */
    @Test
    void anEmptyMessageIsAccepted() {
        assertThat(new ChatMessage(ChatRole.ASSISTANT, "", WHEN).text()).isEmpty();
    }

    /**
     * Verifies that every part of a message is required, so that no message can be stored without an
     * author, a text or a moment.
     */
    @Test
    void everyPartIsRequired() {
        assertThatThrownBy(() -> new ChatMessage(null, "t", WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("role");
        assertThatThrownBy(() -> new ChatMessage(ChatRole.USER, null, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
        assertThatThrownBy(() -> new ChatMessage(ChatRole.USER, "t", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("createdAt");
    }
}
