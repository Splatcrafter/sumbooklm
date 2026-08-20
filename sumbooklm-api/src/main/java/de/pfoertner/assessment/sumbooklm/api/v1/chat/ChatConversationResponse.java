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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of one conversation with its whole transcript.
 *
 * <h2>All of It at Once</h2>
 * A conversation is returned with every message it holds rather than a page of them. What bounds its
 * size is the notebook it belongs to, and a client that opens it needs the transcript in order to
 * show it, so paging would add a request without removing any work.
 *
 * @param id            stable identifier of the conversation
 * @param title         name the conversation is listed under, empty until the first question
 * @param createdAt     point in time the conversation was started
 * @param lastMessageAt point in time the most recent message was exchanged
 * @param messages      messages of the conversation, oldest first
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "One conversation held inside a notebook, with its whole transcript.")
public record ChatConversationResponse(
        @Schema(description = "Stable identifier of the conversation.")
        UUID id,

        @Schema(description = "Name the conversation is listed under, empty until the first question.")
        String title,

        @Schema(description = "Point in time the conversation was started.")
        Instant createdAt,

        @Schema(description = "Point in time the most recent message was exchanged.")
        Instant lastMessageAt,

        @Schema(description = "Messages of the conversation, oldest first.")
        List<ChatMessageResponse> messages) {

    /**
     * Converts a conversation into its transport representation.
     *
     * @param session conversation produced by the workspace module
     * @return the conversation as it is returned to a client
     */
    public static ChatConversationResponse from(final ChatSession session) {
        return new ChatConversationResponse(
                session.id(),
                session.title(),
                session.createdAt(),
                session.lastMessageAt(),
                session.messages().stream().map(ChatMessageResponse::from).toList());
    }
}
