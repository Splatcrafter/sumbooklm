/**
 * Relational representation of issued refresh tokens.
 *
 * <h2>Why Refresh Tokens Are Stored</h2>
 * An access token is verified by its signature alone and is deliberately short lived. A refresh
 * token outlives it by months, which makes signature verification insufficient: a token that has
 * been rotated or revoked is still correctly signed. The table in this package is the authority on
 * which refresh tokens are still usable, and it is what the application consults for operations that
 * a valid signature alone must not authorise.
 *
 * <h2>Stored Form</h2>
 * The table holds a digest of the token, never the token itself. A reader of the table can therefore
 * confirm a token presented to the application but cannot reconstruct one from the stored rows.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence.token;
