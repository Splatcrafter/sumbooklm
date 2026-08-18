/**
 * Additional authorization for operations that a valid access token alone must not authorise.
 *
 * <h2>Reason</h2>
 * An access token is accepted on its signature, which means it stays acceptable until it expires,
 * even after the session it belongs to was closed. For most reads that window is acceptable. For
 * operations that change security relevant state it is not, and those operations verify the session
 * against the database in addition to the signature.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.security.access;
