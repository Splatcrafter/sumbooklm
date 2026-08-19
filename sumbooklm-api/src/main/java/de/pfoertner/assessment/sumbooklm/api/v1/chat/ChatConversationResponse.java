package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.util.List;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of the conversation held inside a notebook.
 *
 * <h2>Always a Document</h2>
 * A notebook that has never been asked anything answers with an empty conversation rather than with
 * a missing one. The client renders the same view either way, and a status that distinguished the two
 * would make it handle a case that is not an error.
 *
 * @param title    name the conversation is listed under, empty until the first question was asked
 * @param messages messages of the conversation, oldest first
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "The conversation held inside one notebook.")
public record ChatConversationResponse(
        @Schema(description = "Name the conversation is listed under, empty while unused.")
        String title,

        @Schema(description = "Messages of the conversation, oldest first.")
        List<ChatMessageResponse> messages) {

    /**
     * Builds the representation of a notebook that has not been asked anything yet.
     *
     * @return an empty conversation
     */
    public static ChatConversationResponse empty() {
        return new ChatConversationResponse("", List.of());
    }

    /**
     * Converts a conversation into its transport representation.
     *
     * @param session conversation produced by the workspace module
     * @return the conversation as it is returned to a client
     */
    public static ChatConversationResponse from(final ChatSession session) {
        return new ChatConversationResponse(
                session.title(),
                session.messages().stream().map(ChatMessageResponse::from).toList());
    }
}
