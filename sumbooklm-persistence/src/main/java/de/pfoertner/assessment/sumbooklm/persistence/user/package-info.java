/**
 * Relational representation of user accounts.
 *
 * <h2>Column Contract</h2>
 * The table carries only what has to be queryable or is required to authenticate: the identifier,
 * the unique username, the password hash and the two timestamps the application sorts and reports
 * on. Everything else about a user lives in the CBOR payload column and is opaque to SQL.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence.user;
