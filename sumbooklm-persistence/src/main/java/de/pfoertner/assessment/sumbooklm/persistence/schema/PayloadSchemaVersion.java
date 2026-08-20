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
     * Payload schema version {@code 1.1.0}, which adds the written summary of a notebook and the
     * fingerprint of the sources it was written from.
     */
    public static final int V1_1_0 = 110;

    /**
     * Payload schema version written by the running application.
     */
    public static final int CURRENT = V1_1_0;

    /**
     * Prevents instantiation of this constant holder.
     */
    private PayloadSchemaVersion() {
        throw new AssertionError("PayloadSchemaVersion is a constant holder and must not be instantiated");
    }
}
