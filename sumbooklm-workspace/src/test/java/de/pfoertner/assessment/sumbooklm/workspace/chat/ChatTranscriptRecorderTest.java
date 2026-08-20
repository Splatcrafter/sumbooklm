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

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Exercises what happens when a finished answer cannot be stored.
 *
 * <h2>Why Nothing May Escape</h2>
 * The answer is written after the stream has already ended, on a thread nobody is waiting on. A
 * failure there reaches no reader and no request; all it can do is end the thread. Both cases below
 * are therefore about swallowing on purpose: a conversation that was removed while its answer was
 * being written is ordinary, and anything else is worth a line in the log and nothing more.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatTranscriptRecorderTest {

    /**
     * Store the answer is written to.
     */
    private ChatSessionService chatSessionService;

    /**
     * Writer under test.
     */
    private ChatTranscriptRecorder recorder;

    /**
     * Account of the cases.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Conversation of the cases.
     */
    private final UUID sessionId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatTranscriptRecorderTest() {
    }

    /**
     * Builds the writer and the store it writes to.
     */
    @BeforeEach
    void setUp() {
        this.chatSessionService = mock(ChatSessionService.class);
        this.recorder = new ChatTranscriptRecorder(this.chatSessionService);
    }

    /**
     * Verifies that a finished answer is written into the conversation it was asked in.
     */
    @Test
    void aFinishedAnswerIsWritten() {
        this.recorder.record(this.userId, this.sessionId, "A measure of disorder.");

        verify(this.chatSessionService).recordAnswer(
                this.userId, this.sessionId, "A measure of disorder.");
    }

    /**
     * Verifies that an answer to a conversation that was removed while it was being written ends
     * quietly, because a reader who closes a conversation has done nothing wrong.
     */
    @Test
    void anAnswerToARemovedConversationEndsQuietly() {
        doThrow(new ChatSessionNotFoundException(this.sessionId))
                .when(this.chatSessionService).recordAnswer(this.userId, this.sessionId, "An answer.");

        assertThatCode(() -> this.recorder.record(this.userId, this.sessionId, "An answer."))
                .doesNotThrowAnyException();
    }

    /**
     * Verifies that a failure of the store ends quietly as well, because nothing is waiting on the
     * thread the answer is written on and a failure escaping it would only end the thread.
     */
    @Test
    void aFailureOfTheStoreEndsQuietly() {
        doThrow(new IllegalStateException("the connection is gone"))
                .when(this.chatSessionService).recordAnswer(any(), any(), anyString());

        assertThatCode(() -> this.recorder.record(this.userId, this.sessionId, "An answer."))
                .doesNotThrowAnyException();
    }
}
