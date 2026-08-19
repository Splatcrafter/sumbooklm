package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.pfoertner.assessment.sumbooklm.workspace.chat.ChatStreamHandler;
import de.pfoertner.assessment.sumbooklm.workspace.chat.RetrievedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Writes a generated answer onto an open server sent event stream.
 *
 * <h2>A Reader Who Left</h2>
 * A stream whose reader has gone away fails on the next write. That is recorded and the stream is
 * closed, and generating the rest of the answer is not interrupted: it is already paid for, and the
 * transcript is still worth completing for the next time the notebook is opened.
 *
 * <h2>Closing Once</h2>
 * The stream is closed by exactly one of the endings, whichever arrives first, including a write that
 * failed. Closing an emitter twice is what turns a disconnected reader into an exception in the log.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class SseChatStreamHandler implements ChatStreamHandler {

    /**
     * Log the loss of a reader is reported to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SseChatStreamHandler.class);

    /**
     * Greatest number of characters a reported reason may have. What a provider returns can be a whole
     * response body, and the field is meant to be read by the user who has to correct their settings.
     */
    private static final int MAX_REASON_LENGTH = 300;

    /**
     * Stream the events are written to.
     */
    private final SseEmitter emitter;

    /**
     * Whether the stream is still open. The endings and a failed write race for it, and only the
     * first of them closes.
     */
    private final AtomicBoolean open = new AtomicBoolean(true);

    /**
     * Creates the handler.
     *
     * @param emitter stream the events are written to
     */
    public SseChatStreamHandler(final SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onSources(final List<RetrievedSource> sources) {
        send(ChatStreamEvent.SOURCES, sources.stream().map(ChatSourceResponse::from).toList());
    }

    @Override
    public void onToken(final String token) {
        send(ChatStreamEvent.TOKEN, new ChatStreamEvent.Token(token));
    }

    @Override
    public void onCompleted(final String answer) {
        send(ChatStreamEvent.DONE, new ChatStreamEvent.Answer(answer));
        close();
    }

    @Override
    public void onFailed(final Throwable error) {
        send(ChatStreamEvent.ERROR, new ChatStreamEvent.Failure(reasonOf(error)));
        close();
    }

    /**
     * Writes one event, unless the stream has already been closed.
     *
     * @param name name of the event
     * @param data payload of the event, serialized as JSON
     */
    private void send(final String name, final Object data) {
        if (!this.open.get()) {
            return;
        }
        try {
            this.emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (final IOException | RuntimeException e) {
            if (this.open.compareAndSet(true, false)) {
                LOG.debug("The reader of an answer stream is gone, event {} was not delivered", name);
                complete();
            }
        }
    }

    /**
     * Closes the stream, unless something else already did.
     */
    private void close() {
        if (this.open.compareAndSet(true, false)) {
            complete();
        }
    }

    /**
     * Completes the emitter and swallows what a completed one throws.
     */
    private void complete() {
        try {
            this.emitter.complete();
        } catch (final RuntimeException e) {
            LOG.debug("The answer stream was already closed by the container");
        }
    }

    /**
     * Reduces a failure to what the caller is told about it.
     *
     * @param error cause of the failure
     * @return the message of the failure, or the name of its type when it carries none
     */
    private static String reasonOf(final Throwable error) {
        final String message = error.getMessage();
        final String reason = message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.strip();
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
