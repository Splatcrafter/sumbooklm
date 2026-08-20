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

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatMessage;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of one message of a conversation.
 *
 * <h2>Markup Is Part of the Text</h2>
 * The text is returned as it was stored, including the Markdown an answer carries and the citation
 * links inside it. Rendering it is the decision of the client, which is also the only side that knows
 * what following a citation should do.
 *
 * @param role      author of the message
 * @param text      content of the message as it was written or generated
 * @param createdAt point in time the message was appended to the conversation
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "One message of the conversation held inside a notebook.")
public record ChatMessageResponse(
        @Schema(description = "Author of the message.")
        ChatRole role,

        @Schema(description = "Content of the message, in Markdown for a generated answer.")
        String text,

        @Schema(description = "Point in time the message was appended to the conversation.")
        Instant createdAt) {

    /**
     * Converts a message into its transport representation.
     *
     * @param message message produced by the workspace module
     * @return the message as it is returned to a client
     */
    public static ChatMessageResponse from(final ChatMessage message) {
        return new ChatMessageResponse(message.role(), message.text(), message.createdAt());
    }
}
