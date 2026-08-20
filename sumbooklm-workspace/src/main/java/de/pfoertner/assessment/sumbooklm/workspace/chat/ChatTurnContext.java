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
