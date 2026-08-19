package de.pfoertner.assessment.sumbooklm.persistence.payload;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;

/**
 * Codecs shared by more than one payload.
 *
 * <h2>Why Here</h2>
 * A codec is part of the persisted format, so two payloads that encode the same kind of value have to
 * encode it the same way. Declaring such a codec once is what makes that true by construction rather
 * than by two classes happening to agree.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PayloadCodecs {

    /**
     * Codec of a point in time, stored as the number of microseconds since the epoch.
     *
     * <p>Microseconds are the precision every timestamp of this application is truncated to before it
     * is written, so the encoding is lossless for the values that actually occur and stays a single
     * integer in the encoded tree.
     */
    public static final Codec<Instant> INSTANT = Codecs.LONG.xmap(
            PayloadCodecs::fromEpochMicros, PayloadCodecs::toEpochMicros);

    /**
     * Prevents instantiation of this constant holder.
     */
    private PayloadCodecs() {
        throw new AssertionError("PayloadCodecs is a constant holder and must not be instantiated");
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
