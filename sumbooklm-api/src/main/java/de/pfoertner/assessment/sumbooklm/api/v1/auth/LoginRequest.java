package de.pfoertner.assessment.sumbooklm.api.v1.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a login request.
 *
 * <h2>Validation</h2>
 * The constraints below only reject bodies that cannot describe any account. They deliberately do
 * not mirror the registration rules, so that tightening those rules later does not lock out accounts
 * created under the previous ones.
 *
 * @param username login name of the account
 * @param password clear text password of the account
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Credentials of an existing account.")
public record LoginRequest(
        @Schema(description = "Login name of the account.", example = "erik")
        @NotBlank @Size(max = 64)
        String username,

        @Schema(description = "Clear text password of the account.")
        @NotBlank @Size(max = 256)
        String password) {
}
