package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.time.Instant;

import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceDocument;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadCodec;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes;
import org.springframework.stereotype.Component;

/**
 * Assembles domain sources from rows and payload bytes, and payload bytes from payload objects.
 *
 * <h2>Never Read</h2>
 * The payload stores the moment a source was read as {@link Instant#EPOCH} while it never was, since
 * a codec has a value for every field. The domain says that with an absent value instead, and this is
 * where the two meet.
 *
 * <h2>Why This Exists</h2>
 * A source is stored in two places at once: the columns of its row and the CBOR payload of that row.
 * Callers outside the persistence layer should not have to know which half a field lives in, and
 * this component owns that split, exactly as its counterpart does for notebooks.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class SourceDocumentMapper {

    /**
     * Codec used to read and write the payload column of a source row.
     */
    private final PayloadCodec payloadCodec;

    /**
     * Creates the mapper.
     *
     * @param payloadCodec codec for the payload column of a source row
     */
    public SourceDocumentMapper(final PayloadCodec payloadCodec) {
        this.payloadCodec = payloadCodec;
    }

    /**
     * Decodes the payload of a source row.
     *
     * @param entity row to read the payload from
     * @return the decoded payload, migrated to the current payload schema version
     */
    public DocumentPayload readPayload(final SourceDocumentEntity entity) {
        return this.payloadCodec.decode(
                PayloadTypes.SOURCE_DOCUMENT, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Encodes a payload into the byte form stored in a source row.
     *
     * @param payload payload to encode
     * @return CBOR encoded payload at the current payload schema version
     */
    public byte[] writePayload(final DocumentPayload payload) {
        return this.payloadCodec.encode(PayloadTypes.SOURCE_DOCUMENT, payload);
    }

    /**
     * Combines a row and its payload into the domain representation.
     *
     * @param entity row to convert
     * @return the source as the domain model describes it
     */
    public SourceDocument toDomain(final SourceDocumentEntity entity) {
        return toDomain(entity, readPayload(entity));
    }

    /**
     * Combines a row and an already decoded payload into the domain representation.
     *
     * @param entity  row to convert
     * @param payload payload that belongs to the row
     * @return the source as the domain model describes it
     */
    public SourceDocument toDomain(final SourceDocumentEntity entity, final DocumentPayload payload) {
        return new SourceDocument(
                entity.getId(),
                entity.getNotebookId(),
                entity.getUserId(),
                payload.displayName(),
                payload.kind(),
                payload.origin(),
                payload.status(),
                payload.tokenCount(),
                payload.failure(),
                Instant.EPOCH.equals(payload.indexedAt()) ? null : payload.indexedAt(),
                entity.getCreatedAt());
    }
}
