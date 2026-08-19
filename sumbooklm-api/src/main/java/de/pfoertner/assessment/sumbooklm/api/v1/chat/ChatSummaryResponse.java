package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transport representation of one conversation without its transcript.
 *
 * <h2>What a List Needs</h2>
 * A list of conversations shows what each is about and when it was last used, which is the title, the
 * timestamps and how much was said. Returning the transcripts as well would send the whole chat
 * history of a notebook every time a user opens it in order to display a few lines.
 *
 * @param id            stable identifier of the conversation
 * @param title         name the conversation is listed under, empty until the first question
 * @param createdAt     point in time the conversation was started
 * @param lastMessageAt point in time the most recent message was exchanged
 * @param messageCount  number of messages the conversation holds
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "One conversation held inside a notebook, without its transcript.")
public record ChatSummaryResponse(
        @Schema(description = "Stable identifier of the conversation.")
        UUID id,

        @Schema(description = "Name the conversation is listed under, empty until the first question.")
        String title,

        @Schema(description = "Point in time the conversation was started.")
        Instant createdAt,

        @Schema(description = "Point in time the most recent message was exchanged.")
        Instant lastMessageAt,

        @Schema(description = "Number of messages the conversation holds.")
        int messageCount) {

    /**
     * Converts a conversation into the representation a list shows.
     *
     * @param session conversation produced by the workspace module
     * @return the conversation as a list entry
     */
    public static ChatSummaryResponse from(final ChatSession session) {
        return new ChatSummaryResponse(
                session.id(),
                session.title(),
                session.createdAt(),
                session.lastMessageAt(),
                session.messages().size());
    }
}
