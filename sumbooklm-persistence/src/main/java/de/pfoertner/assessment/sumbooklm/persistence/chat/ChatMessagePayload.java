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

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadCodecs;
import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;
import de.splatgames.aether.datafixers.api.codec.RecordCodecBuilder;
import de.splatgames.aether.datafixers.api.result.DataResult;

/**
 * One message of a conversation as it is stored inside the payload of its session.
 *

 * @param role      author of the message
 * @param text      content of the message as it was written or generated
 * @param createdAt point in time the message was appended to its conversation
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatMessagePayload(ChatRole role, String text, Instant createdAt) {

    /**
     * Codec of the author. The role is written by name, so that inserting a constant into
     * {@link ChatRole} cannot change the meaning of data that is already stored.
     */
    private static final Codec<ChatRole> ROLE_CODEC = Codecs.STRING.comapFlatMap(
            ChatMessagePayload::parseRole, ChatRole::name);

    /**
     * Codec that maps a message onto the format independent tree the migration pipeline operates on.
     * The field names below are part of the persisted format and must only be changed together with
     * a schema version and a data fix that performs the rename.
     */
    public static final Codec<ChatMessagePayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ROLE_CODEC.fieldOf("role").forGetter(ChatMessagePayload::role),
                    Codecs.STRING.fieldOf("text").forGetter(ChatMessagePayload::text),
                    PayloadCodecs.INSTANT.fieldOf("createdAt").forGetter(ChatMessagePayload::createdAt)
            ).apply(instance, ChatMessagePayload::new));

    /**
     * Creates the message.
     *
     * @param role      author of the message
     * @param text      content of the message
     * @param createdAt point in time the message was appended to its conversation
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatMessagePayload {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Resolves a stored role name into its constant.
     *
     * @param name name as it was read from a stored payload
     * @return the matching constant, or a failure if the name belongs to no constant
     */
    private static DataResult<ChatRole> parseRole(final String name) {
        try {
            return DataResult.success(ChatRole.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException e) {
            return DataResult.error("Unknown chat role: " + name);
        }
    }
}
