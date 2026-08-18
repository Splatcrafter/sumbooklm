package de.pfoertner.assessment.sumbooklm.persistence.schema;

/**
 * Version identifiers of the CBOR payload format persisted alongside every aggregate.
 *
 * <h2>Encoding</h2>
 * A semantic version {@code MAJOR.MINOR.PATCH} is encoded as a single integer using the formula
 * {@code MAJOR * 100 + MINOR * 10 + PATCH}. Schema version {@code 1.0.0} is therefore written as
 * {@code 100}. The encoding keeps version identifiers monotonically increasing, which is the
 * ordering required by the Aether Datafixers pipeline when it selects the fixes to apply between a
 * persisted version and the current one.
 *
 * <h2>Usage</h2>
 * Every write persists {@link #CURRENT} into the version column of the owning row. Every read
 * compares the persisted value against {@link #CURRENT} and routes the payload through the data
 * fixer pipeline when the values differ.
 *
 * <h2>Adding a Version</h2>
 * A new constant is added whenever the payload layout changes. The previous constant is retained so
 * that data fixers can continue to reference the version they migrate from.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PayloadSchemaVersion {

    /**
     * Initial payload schema version {@code 1.0.0}.
     */
    public static final int V1_0_0 = 100;

    /**
     * Payload schema version written by the running application.
     */
    public static final int CURRENT = V1_0_0;

    private PayloadSchemaVersion() {
        throw new AssertionError("PayloadSchemaVersion is a constant holder and must not be instantiated");
    }
}
