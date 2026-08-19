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
