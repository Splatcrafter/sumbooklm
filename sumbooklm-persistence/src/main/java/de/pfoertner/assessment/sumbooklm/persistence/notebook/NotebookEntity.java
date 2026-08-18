package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Relational row of a notebook.
 *
 * <h2>Column Contract</h2>
 * The columns hold what a query has to reach without decoding a payload: the identifier, the owner
 * and the two timestamps the overview is ordered by. The title, the pin state and the topic icon
 * live in {@code payload} as CBOR bytes written at the schema version recorded in
 * {@code payload_version}.
 *
 * <h2>Ownership</h2>
 * {@code user_id} is a plain identifier column rather than an association. The persistence layer
 * never navigates from a notebook to its owner, and keeping the column plain is what allows every
 * query to be written as a filter on it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Entity
@Table(name = "notebook",
        indexes = @Index(name = "ix_notebook_user_id", columnList = "user_id"))
public class NotebookEntity {

    /**
     * Stable identifier of the notebook, assigned by the application rather than by the database.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identifier of the account the notebook belongs to.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Point in time the notebook was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Point in time the notebook was last opened or changed.
     */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    /**
     * CBOR encoded payload of the notebook.
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
    protected NotebookEntity() {
    }

    /**
     * Creates a row with all values that are mandatory for a new notebook.
     *
     * @param id             stable identifier of the notebook
     * @param userId         identifier of the account the notebook belongs to
     * @param createdAt      point in time the notebook was created
     * @param lastActivityAt point in time the notebook was last opened or changed
     * @param payload        CBOR encoded payload of the notebook
     * @param payloadVersion payload schema version the payload was written with
     */
    public NotebookEntity(final UUID id,
                          final UUID userId,
                          final Instant createdAt,
                          final Instant lastActivityAt,
                          final byte[] payload,
                          final int payloadVersion) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.payload = payload;
        this.payloadVersion = payloadVersion;
    }

    /**
     * Returns the stable identifier of the notebook.
     *
     * @return identifier of the notebook
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Returns the identifier of the account the notebook belongs to.
     *
     * @return identifier of the owning account
     */
    public UUID getUserId() {
        return this.userId;
    }

    /**
     * Returns the point in time the notebook was created.
     *
     * @return creation timestamp of the notebook
     */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Returns the point in time the notebook was last opened or changed.
     *
     * @return timestamp of the most recent activity
     */
    public Instant getLastActivityAt() {
        return this.lastActivityAt;
    }

    /**
     * Replaces the point in time the notebook was last opened or changed.
     *
     * @param lastActivityAt timestamp of the most recent activity
     */
    public void setLastActivityAt(final Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    /**
     * Returns the CBOR encoded payload of the notebook.
     *
     * @return payload bytes as they are stored
     */
    public byte[] getPayload() {
        return this.payload;
    }

    /**
     * Replaces the CBOR encoded payload of the notebook.
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
