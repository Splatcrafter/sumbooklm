package de.pfoertner.assessment.sumbooklm.api.v1.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of a token refresh request.
 *
 * <h2>Why the Token Is in the Body</h2>
 * The refresh token is not sent as a bearer credential. Keeping it out of the authorization header
 * keeps it out of the places an access token routinely ends up in, and it makes the endpoint
 * reachable while the access token that belongs to the session has already expired.
 *
 * @param refreshToken refresh token to exchange for a new pair
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Refresh token to exchange for a new token pair.")
public record RefreshRequest(
        @Schema(description = "Refresh token issued by a previous login, registration or refresh.")
        @NotBlank
        String refreshToken) {
}
