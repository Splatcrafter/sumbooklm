package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;
import de.splatgames.aether.datafixers.api.codec.RecordCodecBuilder;
import de.splatgames.aether.datafixers.api.result.DataResult;

/**
 * One message of a conversation as it is stored inside the payload of its session.
 *
 * <h2>Timestamp Encoding</h2>
 * The point in time is stored as the number of microseconds since the epoch rather than as text.
 * Microseconds are the precision every timestamp of this application is truncated to before it is
 * written, so the encoding is lossless for the values that actually occur and stays a single integer
 * in the encoded tree.
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
     * Codec of the timestamp, mapping between an instant and the microseconds since the epoch.
     */
    private static final Codec<Instant> TIMESTAMP_CODEC = Codecs.LONG.xmap(
            ChatMessagePayload::fromEpochMicros, ChatMessagePayload::toEpochMicros);

    /**
     * Codec that maps a message onto the format independent tree the migration pipeline operates on.
     * The field names below are part of the persisted format and must only be changed together with
     * a schema version and a data fix that performs the rename.
     */
    public static final Codec<ChatMessagePayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ROLE_CODEC.fieldOf("role").forGetter(ChatMessagePayload::role),
                    Codecs.STRING.fieldOf("text").forGetter(ChatMessagePayload::text),
                    TIMESTAMP_CODEC.fieldOf("createdAt").forGetter(ChatMessagePayload::createdAt)
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

    /**
     * Converts a stored timestamp into an instant.
     *
     * @param epochMicros microseconds since the epoch
     * @return the instant the value denotes
     */
    private static Instant fromEpochMicros(final long epochMicros) {
        return Instant.EPOCH.plus(epochMicros, ChronoUnit.MICROS);
    }

    /**
     * Converts an instant into the value that is stored for it.
     *
     * @param instant point in time to encode
     * @return microseconds between the epoch and the instant
     */
    private static long toEpochMicros(final Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }
}
