/**
 * Storage of notebooks.
 *
 * <h2>Split of a Notebook</h2>
 * A notebook is stored as a row whose columns carry what has to be queryable, plus a CBOR payload
 * that carries the rest. The column set is deliberately small: the owner, the two timestamps and the
 * payload envelope. Everything the user edits lives in the payload and is therefore versioned and
 * migratable rather than bound to a table definition.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence.notebook;
