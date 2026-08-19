package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Stores a finished answer without holding up the connection that delivered it.
 *
 * <h2>Why It Is Its Own Component</h2>
 * The last part of an answer is delivered on a thread of the provider, and that thread is what
 * finishes the response of the client. Writing to the database there would make the user wait for a
 * transaction after they have already read the answer, so the write is handed on and the stream is
 * closed immediately.
 *
 * <h2>A Lost Answer Is Not a Failed One</h2>
 * A conversation that is gone by the time the answer is stored is recorded as such and nothing else
 * happens. The user has read the answer, the notebook it belonged to is deleted, and there is no one
 * left to report a failure to.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class ChatTranscriptRecorder {

    /**
     * Log the failures of a write are reported to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ChatTranscriptRecorder.class);

    /**
     * Service that owns the transcript.
     */
    private final ChatSessionService chatSessionService;

    /**
     * Creates the recorder.
     *
     * @param chatSessionService service that owns the transcript
     */
    public ChatTranscriptRecorder(final ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    /**
     * Appends a generated answer to its conversation.
     *
     * @param userId    identifier of the account the conversation belongs to
     * @param sessionId identifier of the conversation the answer belongs to
     * @param answer    answer as the model produced it
     */
    @Async(NotebookChatService.CHAT_EXECUTOR)
    public void record(final UUID userId, final UUID sessionId, final String answer) {
        try {
            this.chatSessionService.recordAnswer(userId, sessionId, answer);
        } catch (final ChatSessionNotFoundException e) {
            LOG.debug("Conversation {} was removed before its answer could be stored", sessionId);
        } catch (final RuntimeException e) {
            LOG.warn("Storing the answer of conversation {} failed", sessionId, e);
        }
    }
}
