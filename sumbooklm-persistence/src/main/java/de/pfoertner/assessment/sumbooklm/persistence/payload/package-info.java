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

/**
 * Encoding, migration and decoding of the evolvable part of an aggregate.
 *
 * <h2>Pipeline</h2>
 * A payload leaves the application as a Java record, is encoded through an Aether Datafixers codec
 * into a format independent tree, and is written to the database as CBOR bytes together with the
 * schema version it was written with. Reading reverses the direction and inserts one additional
 * step: a payload whose stored version is older than the current one is routed through the data
 * fixer pipeline before it is decoded.
 *
 * <h2>Type References</h2>
 * Every payload kind is identified by a {@link de.splatgames.aether.datafixers.api.TypeReference}
 * declared in {@link de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes}. The
 * reference selects both the codec used for encoding and the fixes applied during migration.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence.payload;
