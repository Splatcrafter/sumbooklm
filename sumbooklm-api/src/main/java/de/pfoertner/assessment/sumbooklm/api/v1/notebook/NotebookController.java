package de.pfoertner.assessment.sumbooklm.api.v1.notebook;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import de.pfoertner.assessment.sumbooklm.api.support.AuthenticatedUserResolver;
import de.pfoertner.assessment.sumbooklm.domain.workspace.Notebook;
import de.pfoertner.assessment.sumbooklm.security.access.SensitiveOperation;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints that manage the notebooks of the authenticated account.
 *
 * <h2>Scope of Every Call</h2>
 * The account is taken from the presented access token and never from the request, and it is passed
 * into every call of the workspace module. There is no endpoint that reads a notebook without naming
 * an account, so an identifier of a foreign notebook resolves to nothing rather than to data.
 *
 * <h2>Partial Update</h2>
 * Renaming and pinning are the same operation on different fields, so both are performed with a
 * {@code PATCH} that carries only what changes. A {@code PUT} would have required the client to send
 * back the fields it does not intend to touch.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@RestController
@Tag(name = "Notebooks", description = "Management of the notebooks of the authenticated account.")
@SecurityRequirement(name = "bearerAuth")
public class NotebookController {

    /**
     * Service that manages notebooks.
     */
    private final NotebookService notebookService;

    /**
     * Reader of the account an access token was issued for.
     */
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * Creates the controller.
     *
     * @param notebookService           service that manages notebooks
     * @param authenticatedUserResolver reader of the account an access token was issued for
     */
    public NotebookController(final NotebookService notebookService,
                              final AuthenticatedUserResolver authenticatedUserResolver) {
        this.notebookService = notebookService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /**
     * Lists the notebooks of the authenticated account.
     *
     * @param accessToken access token of the caller, injected from the security context
     * @return the notebooks of the account, most recently active first
     */
    @Operation(summary = "List the notebooks of the authenticated account",
            description = "Returns every notebook of the account, ordered by its activity timestamp "
                    + "descending. Pinned and unpinned notebooks are returned in one list; the pin "
                    + "state of each notebook is part of its representation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The notebooks were returned."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content)
    })
    @GetMapping(ApiPaths.V1_NOTEBOOKS)
    public List<NotebookResponse> list(@AuthenticationPrincipal final Jwt accessToken) {
        return this.notebookService.list(this.authenticatedUserResolver.requireUserId(accessToken))
                .stream()
                .map(NotebookResponse::from)
                .toList();
    }

    /**
     * Creates a notebook for the authenticated account.
     *
     * @param body        name the notebook is created under
     * @param accessToken access token of the caller, injected from the security context
     * @return the created notebook, with its location in the header
     */
    @Operation(summary = "Create a notebook",
            description = "Creates an empty notebook owned by the authenticated account.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The notebook was created."),
            @ApiResponse(responseCode = "400", description = "The request body is not valid.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content)
    })
    @PostMapping(ApiPaths.V1_NOTEBOOKS)
    public ResponseEntity<NotebookResponse> create(@Valid @RequestBody final NotebookCreationRequest body,
                                                   @AuthenticationPrincipal final Jwt accessToken) {
        final Notebook notebook = this.notebookService.create(
                this.authenticatedUserResolver.requireUserId(accessToken), body.title());
        return ResponseEntity.created(URI.create(ApiPaths.V1_NOTEBOOKS + "/" + notebook.id()))
                .body(NotebookResponse.from(notebook));
    }

    /**
     * Changes the title, the pin state or both of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook to change
     * @param body        fields to change, where an omitted field keeps its stored value
     * @param accessToken access token of the caller, injected from the security context
     * @return the notebook as it is stored after the change
     */
    @Operation(summary = "Change a notebook",
            description = "Changes the title, the pin state or both. An omitted field keeps its stored value.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The notebook was changed."),
            @ApiResponse(responseCode = "400", description = "The request body is not valid.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content)
    })
    @PatchMapping(ApiPaths.V1_NOTEBOOK)
    public NotebookResponse update(@PathVariable("notebookId") final UUID notebookId,
                                   @Valid @RequestBody final NotebookUpdateRequest body,
                                   @AuthenticationPrincipal final Jwt accessToken) {
        return NotebookResponse.from(this.notebookService.update(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId, body.toCommand()));
    }

    /**
     * Removes one notebook of the authenticated account together with everything below it.
     *
     * @param notebookId  identifier of the notebook to remove
     * @param accessToken access token of the caller, injected from the security context
     * @return an empty response
     */
    @Operation(summary = "Delete a notebook",
            description = "Removes the notebook together with its sources and chat sessions. The session "
                    + "of the presented access token has to be open, which is verified against the database.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The notebook was removed."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "The session of the access token is closed.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content)
    })
    @SensitiveOperation
    @DeleteMapping(ApiPaths.V1_NOTEBOOK)
    public ResponseEntity<Void> delete(@PathVariable("notebookId") final UUID notebookId,
                                       @AuthenticationPrincipal final Jwt accessToken) {
        this.notebookService.delete(this.authenticatedUserResolver.requireUserId(accessToken), notebookId);
        return ResponseEntity.noContent().build();
    }
}
