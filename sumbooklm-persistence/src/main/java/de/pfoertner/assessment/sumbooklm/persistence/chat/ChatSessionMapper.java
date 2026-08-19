package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.util.List;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatMessage;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadCodec;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes;
import org.springframework.stereotype.Component;

/**
 * Assembles domain sessions from rows and payload bytes, and payload bytes from payload objects.
 *
 * <h2>Why This Exists</h2>
 * A session is stored in two places at once: the columns of its row and the CBOR payload of that row.
 * Callers outside the persistence layer should not have to know which half a field lives in, and this
 * component owns that split, exactly as its counterparts do for notebooks and for sources.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class ChatSessionMapper {

    /**
     * Codec used to read and write the payload column of a session row.
     */
    private final PayloadCodec payloadCodec;

    /**
     * Creates the mapper.
     *
     * @param payloadCodec codec for the payload column of a session row
     */
    public ChatSessionMapper(final PayloadCodec payloadCodec) {
        this.payloadCodec = payloadCodec;
    }

    /**
     * Decodes the payload of a session row.
     *
     * @param entity row to read the payload from
     * @return the decoded payload, migrated to the current payload schema version
     */
    public ChatSessionPayload readPayload(final ChatSessionEntity entity) {
        return this.payloadCodec.decode(
                PayloadTypes.CHAT_SESSION, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Encodes a payload into the byte form stored in a session row.
     *
     * @param payload payload to encode
     * @return CBOR encoded payload at the current payload schema version
     */
    public byte[] writePayload(final ChatSessionPayload payload) {
        return this.payloadCodec.encode(PayloadTypes.CHAT_SESSION, payload);
    }

    /**
     * Combines a row and its payload into the domain representation.
     *
     * @param entity row to convert
     * @return the session as the domain model describes it
     */
    public ChatSession toDomain(final ChatSessionEntity entity) {
        return toDomain(entity, readPayload(entity));
    }

    /**
     * Combines a row and an already decoded payload into the domain representation.
     *
     * @param entity  row to convert
     * @param payload payload that belongs to the row
     * @return the session as the domain model describes it
     */
    public ChatSession toDomain(final ChatSessionEntity entity, final ChatSessionPayload payload) {
        final List<ChatMessage> messages = payload.messages().stream()
                .map(message -> new ChatMessage(message.role(), message.text(), message.createdAt()))
                .toList();
        return new ChatSession(
                entity.getId(),
                entity.getNotebookId(),
                payload.title(),
                messages,
                entity.getCreatedAt(),
                entity.getLastMessageAt());
    }
}
