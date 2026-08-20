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

package de.pfoertner.assessment.sumbooklm.api.v1.notebook;

import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import de.pfoertner.assessment.sumbooklm.api.support.AuthenticatedUserResolver;
import de.pfoertner.assessment.sumbooklm.api.v1.chat.ByokHeaders;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints of the summary written about the sources of one notebook of the authenticated account.
 *
 * <h2>Reading and Writing Are Two Requests</h2>
 * Reading the summary needs no model and costs nothing, so it is a {@code GET} that every client
 * makes. Writing one is a request to the provider of the caller and is therefore a {@code POST} that
 * only happens when something asks for it. Answering the read by writing what is missing would mean
 * that opening a notebook spends the money of whoever opened it.
 *
 * <h2>Written to the Same Address</h2>
 * The {@code POST} replaces whatever summary the notebook had and answers with the one it now has,
 * which is the same document the {@code GET} returns. There is no second resource for a summary that
 * is being written, because the request returns when the text does.
 *
 * <h2>Waiting for the Provider</h2>
 * Unlike an answer, a summary is not streamed, so the request is held open until the provider replies
 * or the attempt is given up on. That is the cost of a text that is read as a whole; the client sees a
 * request that takes as long as the model does and no protocol of its own.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@RestController
@Tag(name = "Summary", description = "The summary written about the sources of one notebook.")
@SecurityRequirement(name = "bearerAuth")
public class NotebookSummaryController {

    /**
     * Service that reads and writes the summary of a notebook.
     */
    private final NotebookSummaryService notebookSummaryService;

    /**
     * Reader of the account an access token was issued for.
     */
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * Creates the controller.
     *
     * @param notebookSummaryService    service that reads and writes the summary of a notebook
     * @param authenticatedUserResolver reader of the account an access token was issued for
     */
    public NotebookSummaryController(final NotebookSummaryService notebookSummaryService,
                                     final AuthenticatedUserResolver authenticatedUserResolver) {
        this.notebookSummaryService = notebookSummaryService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /**
     * Reads the summary of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook to read the summary of
     * @param accessToken access token of the caller, injected from the security context
     * @return the stored summary, with an empty text while none has been written
     */
    @Operation(summary = "Read the summary of a notebook",
            description = "Returns the summary the notebook carries. The text is empty while none has "
                    + "been written, and the summary reports itself as stale when the sources have "
                    + "changed since it was written. Nothing is requested from a model here.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The summary was returned."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content)
    })
    @GetMapping(ApiPaths.V1_NOTEBOOK_SUMMARY)
    public NotebookSummaryResponse summary(@PathVariable("notebookId") final UUID notebookId,
                                           @AuthenticationPrincipal final Jwt accessToken) {
        return NotebookSummaryResponse.from(this.notebookSummaryService.read(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId));
    }

    /**
     * Has the summary of one notebook of the authenticated account written.
     *
     * @param notebookId  identifier of the notebook to summarise
     * @param body        language the summary is to be written in, absent for that of the sources
     * @param provider    name of the service the model is requested from
     * @param model       name the service knows the model under
     * @param apiKey      key the service is addressed with, absent for a service that needs none
     * @param baseUrl     address the service is reached at, absent for its default address
     * @param accessToken access token of the caller, injected from the security context
     * @return the summary as it is now stored
     */
    @Operation(summary = "Write the summary of a notebook",
            description = "Sends every source of the notebook that has been read to the model named by "
                    + "the headers of the request, stores the summary it writes in place of the "
                    + "previous one and returns it. The request counts against the same bounds as an "
                    + "asked question.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The summary was written."),
            @ApiResponse(responseCode = "400", description = "The named model or the named language is "
                    + "not usable.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "No source of the notebook has been read.",
                    content = @Content),
            @ApiResponse(responseCode = "429",
                    description = "The account has asked as often as it may.", content = @Content),
            @ApiResponse(responseCode = "502", description = "The selected model wrote no summary.",
                    content = @Content)
    })
    @PostMapping(value = ApiPaths.V1_NOTEBOOK_SUMMARY, produces = MediaType.APPLICATION_JSON_VALUE)
    public NotebookSummaryResponse write(
            @PathVariable("notebookId") final UUID notebookId,
            @Valid @RequestBody(required = false) final NotebookSummaryRequest body,
            @RequestHeader(name = ByokHeaders.PROVIDER, required = false) final String provider,
            @RequestHeader(name = ByokHeaders.MODEL, required = false) final String model,
            @RequestHeader(name = ByokHeaders.API_KEY, required = false) final String apiKey,
            @RequestHeader(name = ByokHeaders.BASE_URL, required = false) final String baseUrl,
            @AuthenticationPrincipal final Jwt accessToken) {
        final ModelSelection selection = ModelSelection.of(provider, model, apiKey, baseUrl);
        final String language = body == null ? "" : body.languageOrEmpty();
        return NotebookSummaryResponse.from(this.notebookSummaryService.write(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId, selection, language));
    }
}
