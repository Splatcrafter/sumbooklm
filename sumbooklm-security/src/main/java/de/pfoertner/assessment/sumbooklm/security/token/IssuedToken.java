package de.pfoertner.assessment.sumbooklm.security.token;

import java.time.Instant;
import java.util.UUID;

/**
 * A signed token together with the data the application keeps about it.
 *
 * <h2>Why the Identifier Is Carried</h2>
 * The identifier is the {@code jti} claim of the token. Returning it next to the encoded value spares
 * every caller from decoding the token again just to learn which row it belongs to.
 *
 * @param value     encoded and signed token as it is handed to the client
 * @param id        identifier of the token, matching its {@code jti} claim
 * @param issuedAt  point in time the token was issued
 * @param expiresAt point in time the token stops being accepted
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record IssuedToken(String value, UUID id, Instant issuedAt, Instant expiresAt) {
}
