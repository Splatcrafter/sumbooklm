/**
 * Persistence layer of the application.
 *
 * <h2>Storage Model</h2>
 * Relational tables hold identity, ownership and lookup columns only. The business payload of an
 * aggregate is stored as a single CBOR encoded binary column alongside the integer schema version
 * that the payload was written with.
 *
 * <h2>Payload Evolution</h2>
 * Payloads are migrated with Aether Datafixers instead of relational migration scripts. A stored
 * payload is decoded into a Jackson tree, handed to the data fixer pipeline for the range between
 * its persisted version and {@link de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion#CURRENT},
 * and re-encoded afterwards.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence;
