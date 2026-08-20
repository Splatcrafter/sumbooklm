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

package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;
import de.splatgames.aether.datafixers.api.codec.RecordCodecBuilder;

/**
 * Evolvable part of a chat session as it is stored in the payload column.
 *
 * <h2>Transcript in the Payload</h2>
 * The messages live inside the payload of their session rather than in a table of their own. A
 * message is never read without the conversation it belongs to and never changes once it has been
 * appended, so a row per message would add a join to every read without ever being addressed on its
 * own. The bound on the size is the notebook: a conversation grows with what one user asks about one
 * set of sources.
 *
 * <h2>Title</h2>
 * The title is derived from the first question and is empty until one has been asked. Deriving it
 * from the answer instead would leave a conversation unnamed for as long as the model is generating.
 *
 * @param title    name the session is listed under, empty until the first question was asked
 * @param messages messages of the session, oldest first
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatSessionPayload(String title, List<ChatMessagePayload> messages) {

    /**
     * Codec that maps the payload onto the format independent tree the migration pipeline operates
     * on. The field names below are part of the persisted format and must only be changed together
     * with a schema version and a data fix that performs the rename.
     */
    public static final Codec<ChatSessionPayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.STRING.fieldOf("title").forGetter(ChatSessionPayload::title),
                    Codecs.list(ChatMessagePayload.CODEC).fieldOf("messages")
                            .forGetter(ChatSessionPayload::messages)
            ).apply(instance, ChatSessionPayload::new));

    /**
     * Creates the payload.
     *
     * @param title    name the session is listed under
     * @param messages messages of the session, oldest first
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatSessionPayload {
        Objects.requireNonNull(title, "title must not be null");
        messages = List.copyOf(messages);
    }

    /**
     * Creates the payload of a session that has not been used yet.
     *
     * @return a payload without a title and without messages
     */
    public static ChatSessionPayload empty() {
        return new ChatSessionPayload("", List.of());
    }

    /**
     * Returns a copy that carries one more message at the end.
     *
     * @param message message to append
     * @return a payload equal to this one except for the appended message
     */
    public ChatSessionPayload withMessage(final ChatMessagePayload message) {
        final List<ChatMessagePayload> appended = new ArrayList<>(this.messages);
        appended.add(message);
        return new ChatSessionPayload(this.title, appended);
    }

    /**
     * Returns a copy carrying a different title.
     *
     * @param newTitle name the session is listed under from now on
     * @return a payload equal to this one except for its title
     */
    public ChatSessionPayload withTitle(final String newTitle) {
        return new ChatSessionPayload(newTitle, this.messages);
    }
}
