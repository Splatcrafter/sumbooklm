/**
 * Storage of the source documents a notebook grounds its answers in.
 *
 * <h2>Split of a Source</h2>
 * A source is stored as a row whose columns carry the two identifiers it is reached by, its owner
 * and its notebook, plus a CBOR payload holding everything the ingestion pipeline determines about
 * it. The pipeline is the part of the application that changes most, so its results are kept in the
 * migratable half of the row.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence.document;
