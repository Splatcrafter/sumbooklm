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

package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ModelSelection;
import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import de.pfoertner.assessment.sumbooklm.api.support.AuthenticatedUserResolver;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import de.pfoertner.assessment.sumbooklm.workspace.chat.ChatTurnContext;
import de.pfoertner.assessment.sumbooklm.workspace.chat.NotebookChatService;
import java.net.URI;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * <h2>A Notebook Holds Conversations</h2>
 * Questions are asked inside a conversation rather than at a notebook, and a notebook holds as many
 * of them as its user starts. A client that has none creates one, which is one request more on the
 * first question and one way of asking rather than two.
 *
 * <h2>Stopping Is Its Own Request</h2>
 * The connection an answer is written to cannot carry a message back, so stopping arrives as a second
 * request naming the conversation. It answers the same whether or not something was running, because
 * an answer that has just finished and one that never started are the same thing to ask about.
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
     * Lists the conversations of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook to list the conversations of
     * @param accessToken access token of the caller, injected from the security context
     * @return the conversations, most recently used first, without their transcripts
     */
    @Operation(summary = "List the conversations of a notebook",
            description = "Returns every conversation of the notebook, most recently used first, with "
                    + "its title and its timestamps but without its messages.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The conversations were returned."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content)
    })
    @GetMapping(ApiPaths.V1_NOTEBOOK_CHATS)
    public List<ChatSummaryResponse> conversations(@PathVariable("notebookId") final UUID notebookId,
                                                   @AuthenticationPrincipal final Jwt accessToken) {
        return this.notebookChatService
                .conversations(this.authenticatedUserResolver.requireUserId(accessToken), notebookId)
                .stream()
                .map(ChatSummaryResponse::from)
                .toList();
    }

    /**
     * Starts a conversation in one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param accessToken access token of the caller, injected from the security context
     * @return the new conversation, without a title and without messages
     */
    @Operation(summary = "Start a conversation",
            description = "Creates an empty conversation in the notebook. It has no title until the "
                    + "first question is asked in it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The conversation was started."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The account owns no such notebook.",
                    content = @Content)
    })
    @PostMapping(ApiPaths.V1_NOTEBOOK_CHATS)
    public ResponseEntity<ChatConversationResponse> start(
            @PathVariable("notebookId") final UUID notebookId,
            @AuthenticationPrincipal final Jwt accessToken) {
        final ChatSession started = this.notebookChatService.startConversation(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId);
        return ResponseEntity
                .created(URI.create(ApiPaths.V1_NOTEBOOKS + "/" + notebookId + "/chats/" + started.id()))
                .body(ChatConversationResponse.from(started));
    }

    /**
     * Reads one conversation of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation to read
     * @param accessToken access token of the caller, injected from the security context
     * @return the conversation with its whole transcript
     */
    @Operation(summary = "Read a conversation",
            description = "Returns the conversation with every message it holds, oldest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The conversation was returned."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The notebook holds no such conversation.",
                    content = @Content)
    })
    @GetMapping(ApiPaths.V1_NOTEBOOK_CHAT)
    public ChatConversationResponse conversation(@PathVariable("notebookId") final UUID notebookId,
                                                 @PathVariable("sessionId") final UUID sessionId,
                                                 @AuthenticationPrincipal final Jwt accessToken) {
        return ChatConversationResponse.from(this.notebookChatService.conversation(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId, sessionId));
    }

    /**
     * Removes one conversation of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation to remove
     * @param accessToken access token of the caller, injected from the security context
     * @return an empty response
     */
    @Operation(summary = "Delete a conversation",
            description = "Removes the conversation and its transcript. An answer being generated in "
                    + "it is stopped.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The conversation was removed."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "The notebook holds no such conversation.",
                    content = @Content)
    })
    @DeleteMapping(ApiPaths.V1_NOTEBOOK_CHAT)
    public ResponseEntity<Void> delete(@PathVariable("notebookId") final UUID notebookId,
                                       @PathVariable("sessionId") final UUID sessionId,
                                       @AuthenticationPrincipal final Jwt accessToken) {
        this.notebookChatService.deleteConversation(
                this.authenticatedUserResolver.requireUserId(accessToken), notebookId, sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Stops the answer being generated in one conversation of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the conversation belongs to
     * @param sessionId   identifier of the conversation whose answer is to stop
     * @param accessToken access token of the caller, injected from the security context
     * @return an empty response
     */
    @Operation(summary = "Stop an answer",
            description = "Stops the answer being generated in the conversation. What was generated so "
                    + "far is kept, because the reader has already seen it. The response is the same "
                    + "whether or not an answer was running.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Nothing is being generated any more."),
            @ApiResponse(responseCode = "401", description = "No valid access token was presented.",
                    content = @Content)
    })
    @PostMapping(ApiPaths.V1_NOTEBOOK_CHAT_STOP)
    public ResponseEntity<Void> stop(@PathVariable("notebookId") final UUID notebookId,
                                     @PathVariable("sessionId") final UUID sessionId,
                                     @AuthenticationPrincipal final Jwt accessToken) {
        this.notebookChatService.stopAnswer(
                this.authenticatedUserResolver.requireUserId(accessToken), sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Answers a question about the sources of one notebook of the authenticated account.
     *
     * @param notebookId  identifier of the notebook the question is asked in
     * @param sessionId   identifier of the conversation the question continues
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
            @ApiResponse(responseCode = "404", description = "The notebook holds no such conversation.",
                    content = @Content),
            @ApiResponse(responseCode = "429",
                    description = "The account already has as many answers being generated as it may have.",
                    content = @Content)
    })
    @PostMapping(value = ApiPaths.V1_NOTEBOOK_CHAT_QUESTIONS,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@PathVariable("notebookId") final UUID notebookId,
                          @PathVariable("sessionId") final UUID sessionId,
                          @Valid @RequestBody final ChatQuestionRequest body,
                          @RequestHeader(name = ByokHeaders.PROVIDER, required = false) final String provider,
                          @RequestHeader(name = ByokHeaders.MODEL, required = false) final String model,
                          @RequestHeader(name = ByokHeaders.API_KEY, required = false) final String apiKey,
                          @RequestHeader(name = ByokHeaders.BASE_URL, required = false) final String baseUrl,
                          @AuthenticationPrincipal final Jwt accessToken) {
        final UUID userId = this.authenticatedUserResolver.requireUserId(accessToken);
        final ModelSelection selection = ModelSelection.of(provider, model, apiKey, baseUrl);
        final ChatTurnContext context =
                this.notebookChatService.beginTurn(userId, notebookId, sessionId, body.question().strip());

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
