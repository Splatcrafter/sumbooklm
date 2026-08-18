package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Relational row of a chat session.
 *
 * <h2>Column Contract</h2>
 * The columns hold the identifier of the session, the notebook it belongs to, the account that owns
 * both, and the two timestamps a list of sessions is ordered by. Everything the user sees of a
 * session lives in {@code payload} as CBOR bytes written at the schema version recorded in
 * {@code payload_version}.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Entity
@Table(name = "chat_session",
        indexes = {
                @Index(name = "ix_chat_session_notebook_id", columnList = "notebook_id"),
                @Index(name = "ix_chat_session_user_id", columnList = "user_id")
        })
public class ChatSessionEntity {

    /**
     * Stable identifier of the session, assigned by the application rather than by the database.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identifier of the account the session belongs to.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Identifier of the notebook the session belongs to.
     */
    @Column(name = "notebook_id", nullable = false, updatable = false)
    private UUID notebookId;

    /**
     * Point in time the session was started.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Point in time the most recent message of the session was exchanged.
     */
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    /**
     * CBOR encoded payload of the session.
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
    protected ChatSessionEntity() {
    }

    /**
     * Creates a row with all values that are mandatory for a new session.
     *
     * @param id             stable identifier of the session
     * @param userId         identifier of the account the session belongs to
     * @param notebookId     identifier of the notebook the session belongs to
     * @param createdAt      point in time the session was started
     * @param lastMessageAt  point in time the most recent message was exchanged
     * @param payload        CBOR encoded payload of the session
     * @param payloadVersion payload schema version the payload was written with
     */
    public ChatSessionEntity(final UUID id,
                             final UUID userId,
                             final UUID notebookId,
                             final Instant createdAt,
                             final Instant lastMessageAt,
                             final byte[] payload,
                             final int payloadVersion) {
        this.id = id;
        this.userId = userId;
        this.notebookId = notebookId;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
        this.payload = payload;
        this.payloadVersion = payloadVersion;
    }

    /**
     * Returns the stable identifier of the session.
     *
     * @return identifier of the session
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Returns the identifier of the account the session belongs to.
     *
     * @return identifier of the owning account
     */
    public UUID getUserId() {
        return this.userId;
    }

    /**
     * Returns the identifier of the notebook the session belongs to.
     *
     * @return identifier of the owning notebook
     */
    public UUID getNotebookId() {
        return this.notebookId;
    }

    /**
     * Returns the point in time the session was started.
     *
     * @return creation timestamp of the session
     */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Returns the point in time the most recent message was exchanged.
     *
     * @return timestamp of the most recent message
     */
    public Instant getLastMessageAt() {
        return this.lastMessageAt;
    }

    /**
     * Replaces the point in time the most recent message was exchanged.
     *
     * @param lastMessageAt timestamp of the most recent message
     */
    public void setLastMessageAt(final Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    /**
     * Returns the CBOR encoded payload of the session.
     *
     * @return payload bytes as they are stored
     */
    public byte[] getPayload() {
        return this.payload;
    }

    /**
     * Replaces the CBOR encoded payload of the session.
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
