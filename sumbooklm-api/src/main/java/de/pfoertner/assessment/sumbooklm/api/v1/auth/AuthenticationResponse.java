package de.pfoertner.assessment.sumbooklm.api.v1.auth;

import de.pfoertner.assessment.sumbooklm.security.authentication.AuthenticationResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response of a successful registration or login.
 *
 * <h2>Content</h2>
 * The response carries the token pair the client authenticates with and the account it belongs to,
 * so that a client can render its authenticated state without a second request.
 *
 * @param tokens the issued token pair
 * @param user   the account the pair was issued for
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Issued token pair together with the account it belongs to.")
public record AuthenticationResponse(
        @Schema(description = "Issued token pair.")
        TokenPairResponse tokens,

        @Schema(description = "Account the token pair belongs to.")
        AuthenticatedUser user) {

    /**
     * Converts an authentication result into its transport representation.
     *
     * @param result result produced by the security module
     * @return the result as it is returned to a client
     */
    public static AuthenticationResponse from(final AuthenticationResult result) {
        return new AuthenticationResponse(
                TokenPairResponse.from(result.tokens()),
                AuthenticatedUser.from(result.account()));
    }
}
