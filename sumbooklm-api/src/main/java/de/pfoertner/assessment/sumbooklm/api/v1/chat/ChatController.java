package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import de.pfoertner.assessment.sumbooklm.api.support.AuthenticatedUserResolver;
import de.pfoertner.assessment.sumbooklm.workspace.chat.ChatTurnContext;
import de.pfoertner.assessment.sumbooklm.workspace.chat.NotebookChatService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Endpoints of the conversation held inside one notebook of the authenticated account.
 *
 * <h2>Order of the Checks</h2>
 * The presented model access is validated before the question is stored, and the permit that bounds
 * how many answers an account may have at once is taken before that. A caller whose settings are
 * incomplete, or who is already waiting for as many answers as they may, therefore receives a
 * rejected request rather than a transcript that has grown a question nobody could answer.
 *
 * <h2>Answering Off the Request Thread</h2>
 * The request returns as soon as the stream exists. Everything after that, from retrieval to the last
 * word of the answer, happens on the executor of the workspace module, so a provider that takes a
 * minute does not hold a thread of the web server for that minute.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@RestController
@Tag(name = "Chat", description = "The conversation held about the sources of one notebook.")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    /**
     * Time an answer may take before the container closes the stream. A provider is given two minutes
     * to reply and the answer itself is streamed, so the value bounds a stream that has stopped
     * producing rather than a long answer.
     */
    private static final long STREAM_TIMEOUT_MILLIS = 180_000L;

    /**
     * Service that answers questions about the sources of a notebook.
     */
    private final NotebookChatService notebookChatService;

    /**
     * Reader of the account an access token was issued for.
     */
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * Creates the controller.
     *
     * @param notebookChatService       service that answers questions about a notebook
     * @param authenticatedUserResolver reader of the account an access token was issued for
     */
    public ChatController(final NotebookChatService notebookChatService,
                          final AuthenticatedUserResolver authenticatedUserResolver) {
        this.notebookChatService = notebookChatService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /**
     * Reads the conversation of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook to read the conversation of
     * @param accessToken access token of the caller, injected from the security context
     * @return the conversation, empty if nothing has been asked in this notebook yet
     */
    @Operation(summary = "Read the conversation of a notebook",
            description = "Returns every message of the conversation, oldest first. A notebook that has "
                    + "not been asked anything answers with an empty conversation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The conversation was returned."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content)
    })
    @GetMapping(ApiPaths.V1_NOTEBOOK_CHAT_MESSAGES)
    public ChatConversationResponse conversation(@PathVariable("notebookId") final UUID notebookId,
                                                 @AuthenticationPrincipal final Jwt accessToken) {
        return this.notebookChatService
                .conversation(this.authenticatedUserResolver.requireUserId(accessToken), notebookId)
                .map(ChatConversationResponse::from)
                .orElseGet(ChatConversationResponse::empty);
    }

    /**
     * Answers a question about the sources of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the question is asked in
     * @param body        question to answer
     * @param provider    name of the service the model is requested from
     * @param model       name the service knows the model under
     * @param apiKey      key the service is addressed with, absent for a service that needs none
     * @param baseUrl     address the service is reached at, absent for its default address
     * @param accessToken access token of the caller, injected from the security context
     * @return a stream carrying the sources, the parts of the answer and its ending
     */
    @Operation(summary = "Ask a question about the sources of a notebook",
            description = "Stores the question and answers it as a stream of server sent events: the "
                    + "sources the answer may cite, then the answer part by part, then either the "
                    + "finished answer or the reason none arrived. The model is the one named by the "
                    + "headers of the request and is addressed with the key they carry.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The answer is being streamed.",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "400", description = "The question or the named model is not usable.",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content),
            @ApiResponse(responseCode = "429",
                    description = "The account already has as many answers being generated as it may have.",
                    content = @Content)
    })
    @PostMapping(value = ApiPaths.V1_NOTEBOOK_CHAT,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@PathVariable("notebookId") final UUID notebookId,
                          @Valid @RequestBody final ChatQuestionRequest body,
                          @RequestHeader(name = ByokHeaders.PROVIDER, required = false) final String provider,
                          @RequestHeader(name = ByokHeaders.MODEL, required = false) final String model,
                          @RequestHeader(name = ByokHeaders.API_KEY, required = false) final String apiKey,
                          @RequestHeader(name = ByokHeaders.BASE_URL, required = false) final String baseUrl,
                          @AuthenticationPrincipal final Jwt accessToken) {
        final UUID userId = this.authenticatedUserResolver.requireUserId(accessToken);
        final ModelSelection selection = ModelSelection.of(provider, model, apiKey, baseUrl);
        final ChatTurnContext context =
                this.notebookChatService.beginTurn(userId, notebookId, body.question().strip());

        final SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        try {
            this.notebookChatService.answer(userId, context, selection, new SseChatStreamHandler(emitter));
        } catch (final RuntimeException e) {
            // The permit taken while the question was stored is returned by whichever ending the
            // answer reaches. A hand-off that never started has no ending, so it is returned here.
            this.notebookChatService.abandonTurn(userId);
            throw e;
        }
        return emitter;
    }
}
