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

package de.pfoertner.assessment.sumbooklm.api.v1.security;

import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import de.pfoertner.assessment.sumbooklm.api.support.KeyHandleCookieFactory;
import de.pfoertner.assessment.sumbooklm.security.cookie.CookieCryptographyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands a client the parameters its stored token pair is encrypted with.
 *
 * <h2>Authorization</h2>
 * The endpoint is reachable without an access token on purpose. A client that has just restarted
 * holds nothing but its encrypted cookie, and it cannot present a token before it is able to decrypt
 * one. What authorizes the call is the key handle cookie, which the browser attaches automatically
 * and which no script on the page can read.
 *
 * <h2>Caching</h2>
 * The response is marked as not storable. It contains key material, and the vector it carries must
 * not be reused across encryptions.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@RestController
@Tag(name = "Security", description = "Support for the client side protection of stored tokens.")
public class CookieCryptographyController {

    /**
     * Service deriving the parameters of a key handle.
     */
    private final CookieCryptographyService cookieCryptographyService;

    /**
     * Reader of the key handle cookie of a request.
     */
    private final KeyHandleCookieFactory keyHandleCookieFactory;

    /**
     * Creates the controller.
     *
     * @param cookieCryptographyService service deriving the parameters of a key handle
     * @param keyHandleCookieFactory    reader of the key handle cookie
     */
    public CookieCryptographyController(final CookieCryptographyService cookieCryptographyService,
                                        final KeyHandleCookieFactory keyHandleCookieFactory) {
        this.cookieCryptographyService = cookieCryptographyService;
        this.keyHandleCookieFactory = keyHandleCookieFactory;
    }

    /**
     * Returns the encryption parameters belonging to the key handle of the calling client.
     *
     * @param request request to read the key handle cookie from
     * @return the parameters, or a response without body if the request carries no key handle
     */
    @Operation(summary = "Read the parameters of the client side token store",
            description = "Derives the key belonging to the key handle cookie of the caller and returns it "
                    + "together with a freshly generated initialization vector.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The parameters were derived."),
            @ApiResponse(responseCode = "401", description = "The request carries no key handle.", content = @Content)
    })
    @GetMapping(ApiPaths.V1_SECURITY_COOKIE_IV)
    public ResponseEntity<CookieCryptographyResponse> parameters(final HttpServletRequest request) {
        final String keyHandle = this.keyHandleCookieFactory.read(request);
        if (keyHandle == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CookieCryptographyResponse.from(
                        this.cookieCryptographyService.parametersFor(keyHandle)));
    }
}
