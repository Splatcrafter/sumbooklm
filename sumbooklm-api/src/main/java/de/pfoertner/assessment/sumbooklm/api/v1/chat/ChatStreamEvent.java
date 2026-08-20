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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The events one answer is streamed as.
 *
 * <h2>Why Objects Rather Than Bare Text</h2>
 * A part of an answer may contain line breaks, and a line break is what separates the fields of a
 * server sent event. Every payload is therefore a JSON object, which survives the encoding untouched
 * and leaves room for a field to be added to an event without changing how it is parsed.
 *
 * <h2>Names of the Events</h2>
 * A stream sends {@code sources} once, then {@code token} repeatedly, and ends in either {@code done}
 * or {@code error}. A client that receives neither ending has lost the connection.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChatStreamEvent {

    /**
     * Name of the event carrying the sources the answer may cite.
     */
    public static final String SOURCES = "sources";

    /**
     * Name of the event carrying the next part of the answer.
     */
    public static final String TOKEN = "token";

    /**
     * Name of the event carrying the finished answer.
     */
    public static final String DONE = "done";

    /**
     * Name of the event carrying the reason no answer will arrive.
     */
    public static final String ERROR = "error";

    /**
     * Prevents instantiation of this constant holder.
     */
    private ChatStreamEvent() {
        throw new AssertionError("ChatStreamEvent is a constant holder and must not be instantiated");
    }

    /**
     * Payload of a {@link #TOKEN} event.
     *
     * @param text text generated since the previous event
     * @author Erik Pförtner
     * @since 0.1.0
     */
    @Schema(description = "The next part of an answer that is being generated.")
    public record Token(
            @Schema(description = "Text generated since the previous event.")
            String text) {
    }

    /**
     * Payload of a {@link #DONE} event.
     *
     * <p>The finished answer is repeated in full rather than only announced. It is what the provider
     * closed the stream with, so a client that missed a part can replace what it assembled with the
     * text that is also being stored.
     *
     * @param answer the complete answer
     * @author Erik Pförtner
     * @since 0.1.0
     */
    @Schema(description = "The finished answer, as it is stored in the transcript.")
    public record Answer(
            @Schema(description = "The complete answer.")
            String answer) {
    }

    /**
     * Payload of an {@link #ERROR} event.
     *
     * @param reason what the provider or the client reported, shortened to a displayable length
     * @author Erik Pförtner
     * @since 0.1.0
     */
    @Schema(description = "The reason an answer could not be generated.")
    public record Failure(
            @Schema(description = "What the provider or the client reported.")
            String reason) {
    }
}
