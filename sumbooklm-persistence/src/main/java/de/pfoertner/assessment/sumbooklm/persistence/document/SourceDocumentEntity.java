package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.Length;

/**
 * Relational row of a source document.
 *
 * <h2>Column Contract</h2>
 * The columns hold the identifier of the source, the notebook it belongs to, the account that owns
 * both, and the point in time it was added. Its name, its processing state, its token count and its
 * content hash live in {@code payload} as CBOR bytes written at the schema version recorded in
 * {@code payload_version}.
 *
 * <h2>Stored Content</h2>
 * {@code content} holds the bytes of an uploaded file, so that the source can be parsed again
 * without asking the user to upload it a second time. A source that names a web page leaves the
 * column empty: its content lives at its address, and a copy taken at upload time would silently
 * become a different document than the one the address resolves to.
 *
 * <h2>Extracted Text</h2>
 * {@code extracted_text} holds the text the last successful run read out of the source, whatever the
 * source was. It is what the stored segments were produced from, so rebuilding them needs neither the
 * parser nor the network, and it stays empty for a source that has never been read successfully.
 *
 * <h2>Two Identifier Columns</h2>
 * The owner is carried next to the notebook instead of being reached through it. A source is always
 * read for one account, and having the account on the row itself keeps that filter a column
 * comparison rather than a join.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Entity
@Table(name = "source_document",
        indexes = {
                @Index(name = "ix_source_document_notebook_id", columnList = "notebook_id"),
                @Index(name = "ix_source_document_user_id", columnList = "user_id")
        })
public class SourceDocumentEntity {

    /**
     * Stable identifier of the source, assigned by the application rather than by the database.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identifier of the account the source belongs to.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Identifier of the notebook the source belongs to.
     */
    @Column(name = "notebook_id", nullable = false, updatable = false)
    private UUID notebookId;

    /**
     * Point in time the source was added to its notebook.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Bytes of the uploaded file, or {@code null} for a source that names a web page.
     */
    @Lob
    @Column(name = "content")
    private byte[] content;

    /**
     * Text the last successful run extracted from the source, or {@code null} while there was none.
     *
     * <p>The column is declared with the greatest length the provider offers, which is what makes it
     * choose an unbounded text type rather than the large object type a length free declaration would
     * select.
     */
    @Column(name = "extracted_text", length = Length.LONG32)
    private String extractedText;

    /**
     * CBOR encoded payload of the source.
     */
    @Column(name = "payload", nullable = false, length = 65_536)
    private byte[] payload;

    /**
     * Payload schema version the content of {@code payload} was written with.
     */
    @Column(name = "payload_version", nullable = false)
    private int payloadVersion;

    /**
     * Optimistic locking counter maintained by the persistence provider.
     */
    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    /**
     * Creates an empty row. Required by the persistence provider.
     */
    protected SourceDocumentEntity() {
    }

    /**
     * Creates a row with all values that are mandatory for a new source.
     *
     * @param id             stable identifier of the source
     * @param userId         identifier of the account the source belongs to
     * @param notebookId     identifier of the notebook the source belongs to
     * @param createdAt      point in time the source was added
     * @param content        bytes of the uploaded file, or {@code null} for a web page
     * @param payload        CBOR encoded payload of the source
     * @param payloadVersion payload schema version the payload was written with
     */
    public SourceDocumentEntity(final UUID id,
                                final UUID userId,
                                final UUID notebookId,
                                final Instant createdAt,
                                final byte[] content,
                                final byte[] payload,
                                final int payloadVersion) {
        this.id = id;
        this.userId = userId;
        this.notebookId = notebookId;
        this.createdAt = createdAt;
        this.content = content;
        this.payload = payload;
        this.payloadVersion = payloadVersion;
    }

    /**
     * Returns the stable identifier of the source.
     *
     * @return identifier of the source
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Returns the identifier of the account the source belongs to.
     *
     * @return identifier of the owning account
     */
    public UUID getUserId() {
        return this.userId;
    }

    /**
     * Returns the identifier of the notebook the source belongs to.
     *
     * @return identifier of the owning notebook
     */
    public UUID getNotebookId() {
        return this.notebookId;
    }

    /**
     * Returns the point in time the source was added to its notebook.
     *
     * @return creation timestamp of the source
     */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Returns the bytes of the uploaded file.
     *
     * @return the stored bytes, or {@code null} for a source that names a web page
     */
    public byte[] getContent() {
        return this.content;
    }

    /**
     * Returns the text the last successful run extracted from the source.
     *
     * @return the extracted text, or {@code null} if the source has never been read successfully
     */
    public String getExtractedText() {
        return this.extractedText;
    }

    /**
     * Replaces the text the last successful run extracted from the source.
     *
     * @param extractedText text that was extracted
     */
    public void setExtractedText(final String extractedText) {
        this.extractedText = extractedText;
    }

    /**
     * Returns the CBOR encoded payload of the source.
     *
     * @return payload bytes as they are stored
     */
    public byte[] getPayload() {
        return this.payload;
    }

    /**
     * Replaces the CBOR encoded payload of the source.
     *
     * @param payload payload bytes to store
     */
    public void setPayload(final byte[] payload) {
        this.payload = payload;
    }

    /**
     * Returns the payload schema version the stored payload was written with.
     *
     * @return payload schema version of the stored payload
     */
    public int getPayloadVersion() {
        return this.payloadVersion;
    }

    /**
     * Replaces the payload schema version of the stored payload.
     *
     * @param payloadVersion payload schema version the payload was written with
     */
    public void setPayloadVersion(final int payloadVersion) {
        this.payloadVersion = payloadVersion;
    }

    /**
     * Returns the optimistic locking counter of the row.
     *
     * @return current value of the locking counter
     */
    public long getRecordVersion() {
        return this.recordVersion;
    }
}
