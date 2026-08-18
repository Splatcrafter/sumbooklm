package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.util.Objects;

import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;
import de.splatgames.aether.datafixers.api.codec.RecordCodecBuilder;

/**
 * Evolvable part of a chat session as it is stored in the payload column.
 *
 * <h2>Boundary</h2>
 * The record holds what a list of conversations displays about a session. It is deliberately narrow
 * for now, because the shape of a conversation is decided by the chat pipeline and every field added
 * here before that pipeline exists would be a guess that a data fixer has to correct later.
 *
 * @param title name the session is listed under, derived from its first question
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatSessionPayload(String title) {

    /**
     * Codec that maps the payload onto the format independent tree the migration pipeline operates
     * on. The field name below is part of the persisted format and must only be changed together
     * with a schema version and a data fix that performs the rename.
     */
    public static final Codec<ChatSessionPayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.STRING.fieldOf("title").forGetter(ChatSessionPayload::title)
            ).apply(instance, ChatSessionPayload::new));

    /**
     * Creates the payload.
     *
     * @param title name the session is listed under
     * @throws NullPointerException if {@code title} is {@code null}
     */
    public ChatSessionPayload {
        Objects.requireNonNull(title, "title must not be null");
    }
}
