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
