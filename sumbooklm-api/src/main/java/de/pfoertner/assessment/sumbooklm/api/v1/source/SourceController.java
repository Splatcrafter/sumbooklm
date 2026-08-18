package de.pfoertner.assessment.sumbooklm.api.v1.source;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import de.pfoertner.assessment.sumbooklm.api.support.AuthenticatedUserResolver;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceDocument;
import de.pfoertner.assessment.sumbooklm.security.access.SensitiveOperation;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints that manage the sources of one notebook of the authenticated account.
 *
 * <h2>Two Ways In, Two Endpoints</h2>
 * An upload and an address are submitted in different media types, and one operation that accepted
 * both would be described in the specification as a body that is sometimes one and sometimes the
 * other. Two endpoints keep the generated client honest about which of the two a caller is sending.
 *
 * <h2>Answering Before the Work Is Done</h2>
 * A source is returned as soon as it is stored, while it is still waiting to be indexed. The stage
 * it has reached is part of its representation, so a client that keeps reading the collection sees
 * it progress rather than having to wait for the first answer.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@RestController
@Tag(name = "Sources", description = "Management of the sources of one notebook.")
@SecurityRequirement(name = "bearerAuth")
public class SourceController {

    /**
     * Service that manages sources.
     */
    private final SourceDocumentService sourceDocumentService;

    /**
     * Reader of the account an access token was issued for.
     */
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * Creates the controller.
     *
     * @param sourceDocumentService     service that manages sources
     * @param authenticatedUserResolver reader of the account an access token was issued for
     */
    public SourceController(final SourceDocumentService sourceDocumentService,
                            final AuthenticatedUserResolver authenticatedUserResolver) {
        this.sourceDocumentService = sourceDocumentService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /**
     * Lists the sources of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook to list the sources of
     * @param accessToken access token of the caller, injected from the security context
     * @return the sources of the notebook, in the order they were added
     */
    @Operation(summary = "List the sources of a notebook",
            description = "Returns every source of the notebook, oldest first, each with the stage it "
                    + "has reached on its way into the retrieval index.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The sources were returned."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content)
    })
    @GetMapping(ApiPaths.V1_NOTEBOOK_SOURCES)
    public List<SourceResponse> list(@PathVariable("notebookId") final UUID notebookId,
                                     @AuthenticationPrincipal final Jwt accessToken) {
        return this.sourceDocumentService
                .list(this.authenticatedUserResolver.requireUserId(accessToken), notebookId)
                .stream()
                .map(SourceResponse::from)
                .toList();
    }

    /**
     * Adds an uploaded file to one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the file is added to
     * @param file        uploaded file
     * @param accessToken access token of the caller, injected from the security context
     * @return the stored source, with its location in the header
     * @throws IOException if the uploaded bytes cannot be read
     */
    @Operation(summary = "Add an uploaded file as a source",
            description = "Stores the file and starts indexing it. The response describes the source "
                    + "as it is stored, which is before indexing has finished.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The source was stored."),
            @ApiResponse(responseCode = "400", description = "The upload carries no bytes.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "The notebook already holds this content.",
                    content = @Content),
            @ApiResponse(responseCode = "413", description = "The upload is larger than the accepted size.",
                    content = @Content)
    })
    @PostMapping(value = ApiPaths.V1_NOTEBOOK_SOURCE_FILES, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SourceResponse> addFile(@PathVariable("notebookId") final UUID notebookId,
                                                  @RequestPart("file") final MultipartFile file,
                                                  @AuthenticationPrincipal final Jwt accessToken)
            throws IOException {
        final String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        final SourceDocument source = this.sourceDocumentService.addFile(
                this.authenticatedUserResolver.requireUserId(accessToken),
                notebookId,
                fileName,
                file.getBytes());
        return created(notebookId, source);
    }

    /**
     * Adds a web page to one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the page is added to
     * @param body        address of the page
     * @param accessToken access token of the caller, injected from the security context
     * @return the stored source, with its location in the header
     */
    @Operation(summary = "Add a web page as a source",
            description = "Stores the address and starts retrieving and indexing it. The response "
                    + "describes the source as it is stored, which is before the page has been read.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The source was stored."),
            @ApiResponse(responseCode = "400", description = "The request body is not valid.", content = @Content),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "The notebook already holds this address.",
                    content = @Content)
    })
    @PostMapping(ApiPaths.V1_NOTEBOOK_SOURCE_LINKS)
    public ResponseEntity<SourceResponse> addWebPage(@PathVariable("notebookId") final UUID notebookId,
                                                     @Valid @RequestBody final WebSourceRequest body,
                                                     @AuthenticationPrincipal final Jwt accessToken) {
        final SourceDocument source = this.sourceDocumentService.addWebPage(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId, body.url());
        return created(notebookId, source);
    }

    /**
     * Removes one source of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the source belongs to
     * @param sourceId    identifier of the source to remove
     * @param accessToken access token of the caller, injected from the security context
     * @return an empty response
     */
    @Operation(summary = "Delete a source",
            description = "Removes the source and its segments from the retrieval index. The session of "
                    + "the presented access token has to be open, which is verified against the database.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The source was removed."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "The session of the access token is closed.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The notebook holds no such source.",
                    content = @Content)
    })
    @SensitiveOperation
    @DeleteMapping(ApiPaths.V1_NOTEBOOK_SOURCE)
    public ResponseEntity<Void> delete(@PathVariable("notebookId") final UUID notebookId,
                                       @PathVariable("sourceId") final UUID sourceId,
                                       @AuthenticationPrincipal final Jwt accessToken) {
        this.sourceDocumentService.delete(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId, sourceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Builds the response of a stored source.
     *
     * @param notebookId identifier of the notebook the source belongs to
     * @param source     source that was stored
     * @return a created response carrying the source and its location
     */
    private static ResponseEntity<SourceResponse> created(final UUID notebookId, final SourceDocument source) {
        final URI location = URI.create(
                ApiPaths.V1_NOTEBOOKS + "/" + notebookId + "/sources/" + source.id());
        return ResponseEntity.created(location).body(SourceResponse.from(source));
    }
}
