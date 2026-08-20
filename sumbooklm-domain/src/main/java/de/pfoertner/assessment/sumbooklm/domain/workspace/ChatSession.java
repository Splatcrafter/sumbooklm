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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The conversation held inside one notebook.
 *
 * <h2>Whole Transcript</h2>
 * A session carries all of its messages rather than a page of them. What bounds the size is the
 * notebook it belongs to, and a client that opens a notebook needs the transcript in order to display
 * it, so splitting it into pages would add a second request without removing any work.
 *
 * @param id            stable identifier of the session
 * @param notebookId    identifier of the notebook the session belongs to
 * @param title         name the session is listed under, derived from its first question
 * @param messages      messages of the session, oldest first
 * @param createdAt     point in time the session was started
 * @param lastMessageAt point in time the most recent message was exchanged
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatSession(UUID id,
                          UUID notebookId,
                          String title,
                          List<ChatMessage> messages,
                          Instant createdAt,
                          Instant lastMessageAt) {

    /**
     * Creates the session.
     *
     * @param id            stable identifier of the session
     * @param notebookId    identifier of the notebook the session belongs to
     * @param title         name the session is listed under
     * @param messages      messages of the session, oldest first
     * @param createdAt     point in time the session was started
     * @param lastMessageAt point in time the most recent message was exchanged
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(notebookId, "notebookId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(lastMessageAt, "lastMessageAt must not be null");
        messages = List.copyOf(messages);
    }
}
