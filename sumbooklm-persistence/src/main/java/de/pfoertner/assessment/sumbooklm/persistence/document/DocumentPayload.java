package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.Locale;
import java.util.Objects;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentStatus;
import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;
import de.splatgames.aether.datafixers.api.codec.RecordCodecBuilder;
import de.splatgames.aether.datafixers.api.result.DataResult;

/**
 * Evolvable part of a source document as it is stored in the payload column.
 *
 * <h2>Boundary</h2>
 * The record holds what the ingestion pipeline knows about a source. All of it is derived rather than
 * relational, so a change to the pipeline changes this record and is carried into stored data by a
 * data fixer instead of by a schema migration.
 *
 * <h2>Content Hash</h2>
 * The hash identifies the content of a source independently of the name it was uploaded under, which
 * is what lets the same document be recognised when it is added a second time. It is stored in the
 * payload rather than in a column, which means duplicate detection compares decoded payloads within
 * one notebook rather than asking the database for a match; promoting the hash to an indexed column
 * is the change to make once detection has to span notebooks.
 *
 * @param displayName  name the source is listed under
 * @param status       stage the source has reached on its way into the retrieval index
 * @param tokenCount   number of tokens the extracted text was counted as, zero while unknown
 * @param documentHash hash of the extracted content, empty while unknown
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record DocumentPayload(String displayName, DocumentStatus status, int tokenCount, String documentHash) {

    /**
     * Codec of the processing stage. The stage is written by name, so that inserting a constant into
     * {@link DocumentStatus} cannot change the meaning of data that is already stored.
     */
    private static final Codec<DocumentStatus> STATUS_CODEC = Codecs.STRING.comapFlatMap(
            DocumentPayload::parseStatus, DocumentStatus::name);

    /**
     * Codec that maps the payload onto the format independent tree the migration pipeline operates
     * on. The field names below are part of the persisted format and must only be changed together
     * with a schema version and a data fix that performs the rename.
     */
    public static final Codec<DocumentPayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.STRING.fieldOf("displayName").forGetter(DocumentPayload::displayName),
                    STATUS_CODEC.fieldOf("status").forGetter(DocumentPayload::status),
                    Codecs.INT.fieldOf("tokenCount").forGetter(DocumentPayload::tokenCount),
                    Codecs.STRING.fieldOf("documentHash").forGetter(DocumentPayload::documentHash)
            ).apply(instance, DocumentPayload::new));

    /**
     * Creates the payload.
     *
     * @param displayName  name the source is listed under
     * @param status       stage the source has reached on its way into the retrieval index
     * @param tokenCount   number of tokens the extracted text was counted as, zero while unknown
     * @param documentHash hash of the extracted content, empty while unknown
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code tokenCount} is negative
     */
    public DocumentPayload {
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(documentHash, "documentHash must not be null");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must not be negative");
        }
    }

    /**
     * Resolves a stored stage name into its constant.
     *
     * @param name name as it was read from a stored payload
     * @return the matching constant, or a failure if the name belongs to no constant
     */
    private static DataResult<DocumentStatus> parseStatus(final String name) {
        try {
            return DataResult.success(DocumentStatus.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException e) {
            return DataResult.error("Unknown document status: " + name);
        }
    }
}
