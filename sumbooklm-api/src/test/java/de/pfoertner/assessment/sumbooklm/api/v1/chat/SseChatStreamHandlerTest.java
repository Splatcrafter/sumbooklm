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

package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.workspace.chat.RetrievedSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Exercises what is written into the stream of an answer.
 *
 * <h2>Why the Reader Is Assumed to Be Gone</h2>
 * A stream is open for as long as an answer takes, and the reader may close the tab at any point
 * inside it. Everything written after that fails, and the failure arrives on the thread generating
 * the answer, where nothing can be reported to anybody. The stream therefore has to notice the first
 * failure, stop writing and close itself, because a stream that kept trying would turn one
 * disconnect into a failure per token.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SseChatStreamHandlerTest {

    /**
     * Stream the answer is written into.
     */
    private SseEmitter emitter;

    /**
     * Writer under test.
     */
    private SseChatStreamHandler handler;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SseChatStreamHandlerTest() {
    }

    /**
     * Builds the writer and the stream it writes into.
     */
    @BeforeEach
    void setUp() {
        this.emitter = mock(SseEmitter.class);
        this.handler = new SseChatStreamHandler(this.emitter);
    }

    /**
     * Verifies that the sources, the parts and the ending are all written, and that the stream is
     * closed once the answer has ended.
     *
     * @throws IOException if the stream of the case reports the reader is gone
     */
    @Test
    void anAnswerIsWrittenAndTheStreamIsClosed() throws IOException {
        this.handler.onSources(List.of(new RetrievedSource(1, UUID.randomUUID(), "Source.pdf")));
        this.handler.onToken("Entropy ");
        this.handler.onToken("never decreases.");
        this.handler.onCompleted("Entropy never decreases.");

        verify(this.emitter, times(4)).send(any(SseEmitter.SseEventBuilder.class));
        verify(this.emitter).complete();
    }

    /**
     * Verifies that a failed answer is written as a failure and closes the stream, so that a reader
     * is told why nothing further is coming.
     *
     * @throws IOException if the stream of the case reports the reader is gone
     */
    @Test
    void aFailedAnswerClosesTheStreamAsWell() throws IOException {
        this.handler.onFailed(new IllegalStateException("the provider refused"));

        verify(this.emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(this.emitter).complete();
    }

    /**
     * Verifies that a reader who is gone is noticed once and that nothing further is written, so
     * that one disconnect does not become a failure per part of the answer.
     *
     * @throws IOException if the stream of the case reports the reader is gone
     */
    @Test
    void aReaderWhoIsGoneIsNoticedOnce() throws IOException {
        doThrow(new IOException("broken pipe"))
                .when(this.emitter).send(any(SseEmitter.SseEventBuilder.class));

        this.handler.onToken("Entropy ");
        this.handler.onToken("never decreases.");
        this.handler.onCompleted("Entropy never decreases.");

        verify(this.emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(this.emitter).complete();
    }

    /**
     * Verifies that a stream already closed by the container is not written to further and that
     * nothing escapes the writer, because the answer is generated on a thread nobody is waiting on.
     *
     * @throws IOException if the stream of the case reports the reader is gone
     */
    @Test
    void aStreamTheContainerClosedIsLeftAlone() throws IOException {
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(this.emitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(() -> this.handler.onToken("Entropy ")).doesNotThrowAnyException();
        assertThatCode(() -> this.handler.onCompleted("Entropy ")).doesNotThrowAnyException();
        verify(this.emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    /**
     * Verifies that a stream that cannot be closed either does not let the failure escape, which is
     * the state a container that timed the request out leaves it in.
     */
    @Test
    void aStreamThatCannotBeClosedIsStillEnded() {
        doThrow(new IllegalStateException("already completed")).when(this.emitter).complete();

        assertThatCode(() -> this.handler.onCompleted("An answer.")).doesNotThrowAnyException();
    }

    /**
     * Verifies that an answer which ended is not written to again, so that a second ending cannot
     * reopen a stream that was already closed.
     *
     * @throws IOException if the stream of the case reports the reader is gone
     */
    @Test
    void anAnswerThatEndedIsNotWrittenToAgain() throws IOException {
        this.handler.onCompleted("An answer.");

        this.handler.onToken("More text.");
        this.handler.onFailed(new IllegalStateException("too late"));

        verify(this.emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(this.emitter).complete();
    }

    /**
     * Verifies that a failure carrying no message at all is still written as something, because the
     * reader is shown what the stream says and an empty reason says nothing.
     *
     * @throws IOException if the stream of the case reports the reader is gone
     */
    @Test
    void aFailureWithoutAMessageIsStillWritten() throws IOException {
        this.handler.onFailed(new IllegalStateException());

        verify(this.emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(this.emitter, never()).send(any(Object.class));
    }
}
