package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Relational row recording that an account asked a question, and when.
 *
 * <h2>Why It Is a Table of Its Own</h2>
 * The questions themselves are in the transcripts, as CBOR, where nothing can count them. A bound on
 * how often an account asks needs exactly one thing that a query can reach, which is the moment of the
 * question next to the account that asked, and that is all this row is.
 *
 * <h2>No Payload and No Version</h2>
 * Every other row of this application carries a payload for what the user sees. This one has nothing
 * that anybody sees: it is read by one bound and deleted once it is older than the window that bound
 * looks at, so a schema for it would be a schema for nothing.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Entity
@Table(name = "asked_question",
        indexes = @Index(name = "ix_asked_question_user_id_asked_at", columnList = "user_id, asked_at"))
public class AskedQuestionEntity {

    /**
     * Stable identifier of the record, assigned by the application rather than by the database.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identifier of the account that asked.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Point in time the question was asked.
     */
    @Column(name = "asked_at", nullable = false, updatable = false)
    private Instant askedAt;

    /**
     * Creates an empty row. Required by the persistence provider.
     */
    protected AskedQuestionEntity() {
    }

    /**
     * Creates a row for one question.
     *
     * @param id      stable identifier of the record
     * @param userId  identifier of the account that asked
     * @param askedAt point in time the question was asked
     */
    public AskedQuestionEntity(final UUID id, final UUID userId, final Instant askedAt) {
        this.id = id;
        this.userId = userId;
        this.askedAt = askedAt;
    }

    /**
     * Returns the stable identifier of the record.
     *
     * @return identifier of the record
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Returns the identifier of the account that asked.
     *
     * @return identifier of the account
     */
    public UUID getUserId() {
        return this.userId;
    }

    /**
     * Returns the point in time the question was asked.
     *
     * @return moment of the question
     */
    public Instant getAskedAt() {
        return this.askedAt;
    }
}
