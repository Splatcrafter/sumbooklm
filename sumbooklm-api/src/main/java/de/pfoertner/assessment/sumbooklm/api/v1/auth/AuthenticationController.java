/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.pfoertner.assessment.sumbooklm.api.v1.auth;

import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import de.pfoertner.assessment.sumbooklm.api.support.ClientAddressResolver;
import de.pfoertner.assessment.sumbooklm.api.support.KeyHandleCookieFactory;
import de.pfoertner.assessment.sumbooklm.security.access.SensitiveOperation;
import de.pfoertner.assessment.sumbooklm.security.authentication.AuthenticationResult;
import de.pfoertner.assessment.sumbooklm.security.authentication.AuthenticationService;
import de.pfoertner.assessment.sumbooklm.security.authentication.LoginCommand;
import de.pfoertner.assessment.sumbooklm.security.authentication.RegistrationCommand;
import de.pfoertner.assessment.sumbooklm.security.token.RefreshTokenService;
import de.pfoertner.assessment.sumbooklm.security.token.TokenClaims;
import de.pfoertner.assessment.sumbooklm.security.token.TokenPair;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints of registration, login and the token lifecycle.
 *
 * <h2>Response Shape</h2>
 * Registration and login answer identically, because a registration authenticates the new account
 * right away. Both attach the key handle cookie the client needs before it can store its token pair.
 *
 * <h2>Where the Tokens Go</h2>
 * The tokens themselves are returned in the body and never written into a cookie by the server. What
 * the client does with them, including encrypting them into its own cookie, is a client decision the
 * server only supports with the parameters it hands out separately.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@RestController
@Tag(name = "Authentication", description = "Registration, login and token lifecycle.")
public class AuthenticationController {

    /**
     * Service that creates accounts and verifies credentials.
     */
    private final AuthenticationService authenticationService;

    /**
     * Service that issues, rotates and revokes refresh tokens.
     */
    private final RefreshTokenService refreshTokenService;

    /**
     * Resolver of the network address a request originated from.
     */
    private final ClientAddressResolver clientAddressResolver;

    /**
     * Factory of the cookie carrying the key handle of a client.
     */
    private final KeyHandleCookieFactory keyHandleCookieFactory;

    /**
     * Creates the controller.
     *
     * @param authenticationService  service that creates accounts and verifies credentials
     * @param refreshTokenService    service that issues, rotates and revokes refresh tokens
     * @param clientAddressResolver  resolver of the network address of a request
     * @param keyHandleCookieFactory factory of the key handle cookie
     */
    public AuthenticationController(final AuthenticationService authenticationService,
                                    final RefreshTokenService refreshTokenService,
                                    final ClientAddressResolver clientAddressResolver,
                                    final KeyHandleCookieFactory keyHandleCookieFactory) {
        this.authenticationService = authenticationService;
        this.refreshTokenService = refreshTokenService;
        this.clientAddressResolver = clientAddressResolver;
        this.keyHandleCookieFactory = keyHandleCookieFactory;
    }

    /**
     * Creates an account and authenticates it.
     *
     * @param body    data of the account to create
     * @param request request the registration arrived with, used to record its network address
     * @return the issued token pair and the created account, with the key handle cookie attached
     */
    @Operation(summary = "Create an account and authenticate it",
            description = "Creates the account, records registration metadata and returns a token pair "
                    + "as if the new account had logged in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The account was created and authenticated."),
            @ApiResponse(responseCode = "400", description = "The request body is not valid.", content = @Content),
            @ApiResponse(responseCode = "409", description = "The username is already taken.", content = @Content)
    })
    @PostMapping(ApiPaths.V1_REGISTER)
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody final RegistrationRequest body,
                                                           final HttpServletRequest request) {
        final AuthenticationResult result = this.authenticationService.register(new RegistrationCommand(
                body.username(),
                body.firstName(),
                body.lastName(),
                body.password(),
                this.clientAddressResolver.resolve(request)));
        return authenticated(result);
    }

    /**
     * Verifies credentials and issues a token pair.
     *
     * @param body    credentials to verify
     * @param request request the login arrived with, used to record its network address
     * @return the issued token pair and the account, with the key handle cookie attached
     */
    @Operation(summary = "Authenticate with credentials",
            description = "Verifies the credentials, records the login and returns a token pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The credentials were accepted."),
            @ApiResponse(responseCode = "400", description = "The request body is not valid.", content = @Content),
            @ApiResponse(responseCode = "401", description = "The credentials were rejected.", content = @Content)
    })
    @PostMapping(ApiPaths.V1_LOGIN)
    public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody final LoginRequest body,
                                                        final HttpServletRequest request) {
        final AuthenticationResult result = this.authenticationService.login(new LoginCommand(
                body.username(),
                body.password(),
                this.clientAddressResolver.resolve(request)));
        return authenticated(result);
    }

    /**
     * Exchanges a refresh token for a new token pair and consumes the presented one.
     *
     * @param body    refresh token to exchange
     * @param request request the exchange arrived with, used to record its network address
     * @return the newly issued token pair
     */
    @Operation(summary = "Exchange a refresh token for a new token pair",
            description = "Consumes the presented refresh token and issues a new pair. Presenting a token "
                    + "that was already consumed revokes every session of the account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A new token pair was issued."),
            @ApiResponse(responseCode = "401", description = "The refresh token was rejected.", content = @Content)
    })
    @PostMapping(ApiPaths.V1_TOKEN_REFRESH)
    public TokenPairResponse refresh(@Valid @RequestBody final RefreshRequest body,
                                     final HttpServletRequest request) {
        final TokenPair tokens = this.refreshTokenService.rotate(
                body.refreshToken(), this.clientAddressResolver.resolve(request));
        return TokenPairResponse.from(tokens);
    }

    /**
     * Closes the session the presented access token belongs to.
     *
     * @param accessToken access token of the caller, injected from the security context
     * @return an empty response that removes the key handle cookie
     */
    @Operation(summary = "Close the current session",
            description = "Revokes the refresh token of the session the presented access token belongs to.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The session was closed."),
            @ApiResponse(responseCode = "401", description = "No access token was presented.", content = @Content),
            @ApiResponse(responseCode = "403", description = "The session is already closed.", content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @SensitiveOperation
    @PostMapping(ApiPaths.V1_LOGOUT)
    public ResponseEntity<Void> logout(@AuthenticationPrincipal final Jwt accessToken) {
        this.refreshTokenService.revokeSession(
                UUID.fromString(accessToken.getClaimAsString(TokenClaims.SESSION_ID)));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, this.keyHandleCookieFactory.expire().toString())
                .build();
    }

    /**
     * Builds the response of a successful authentication.
     *
     * @param result outcome of the registration or login
     * @return the response body together with the key handle cookie
     */
    private ResponseEntity<AuthenticationResponse> authenticated(final AuthenticationResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        this.keyHandleCookieFactory.create(result.cookieKeyHandle()).toString())
                .body(AuthenticationResponse.from(result));
    }
}
