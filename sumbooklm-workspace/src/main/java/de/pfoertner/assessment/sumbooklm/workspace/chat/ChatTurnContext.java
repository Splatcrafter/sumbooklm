package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ChatTurn;

/**
 * Everything an answer is generated from, read while the question was being stored.
 *
 * <h2>Why It Is Passed Around</h2>
 * Opening a turn and generating its answer happen in different transactions and on different threads.
 * This record is what travels between them, so that the second half neither repeats the reads of the
 * first nor has to resolve the notebook a second time.
 *
 * @param sessionId  identifier of the conversation the question was appended to
 * @param notebookId identifier of the notebook the conversation belongs to
 * @param question   question that was asked
 * @param history    messages exchanged before this question, oldest first
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatTurnContext(UUID sessionId, UUID notebookId, String question, List<ChatTurn> history) {

    /**
     * Creates the context.
     *
     * @param sessionId  identifier of the conversation the question was appended to
     * @param notebookId identifier of the notebook the conversation belongs to
     * @param question   question that was asked
     * @param history    messages exchanged before this question, oldest first
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatTurnContext {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(notebookId, "notebookId must not be null");
        Objects.requireNonNull(question, "question must not be null");
        history = List.copyOf(history);
    }
}
