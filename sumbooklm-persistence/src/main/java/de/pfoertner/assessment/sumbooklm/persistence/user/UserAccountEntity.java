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

package de.pfoertner.assessment.sumbooklm.persistence.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Relational row of a user account.
 *
 * <h2>Column Contract</h2>
 * The columns are the part of an account that has to be queryable or that authentication needs
 * before any payload can be decoded: the identifier, the unique username, the password hash and the
 * two timestamps. The remaining data of the account lives in {@code payload} as CBOR bytes written
 * at the schema version recorded in {@code payload_version}.
 *
 * <h2>Password Hash</h2>
 * The column holds the encoded form produced by the configured password encoder, including its
 * algorithm prefix. Storing the prefix is what allows the encoding to be changed later without
 * invalidating existing accounts.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Entity
@Table(name = "user_account",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_account_username", columnNames = "username"))
public class UserAccountEntity {

    /**
     * Stable identifier of the account, assigned by the application rather than by the database.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Unique login name of the user.
     */
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /**
     * Encoded password of the user, including the algorithm prefix of the encoder that produced it.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Point in time the account was created.
     */
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    /**
     * Point in time of the most recent successful login.
     */
    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    /**
     * CBOR encoded payload of the account.
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
    protected UserAccountEntity() {
    }

    /**
     * Creates a row with all values that are mandatory for a new account.
     *
     * @param id             stable identifier of the account
     * @param username       unique login name of the user
     * @param passwordHash   encoded password including the algorithm prefix
     * @param registeredAt   point in time the account was created
     * @param lastLoginAt    point in time of the most recent successful login
     * @param payload        CBOR encoded payload of the account
     * @param payloadVersion payload schema version the payload was written with
     */
    public UserAccountEntity(final UUID id,
                             final String username,
                             final String passwordHash,
                             final Instant registeredAt,
                             final Instant lastLoginAt,
                             final byte[] payload,
                             final int payloadVersion) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.registeredAt = registeredAt;
        this.lastLoginAt = lastLoginAt;
        this.payload = payload;
        this.payloadVersion = payloadVersion;
    }

    /**
     * Returns the stable identifier of the account.
     *
     * @return identifier of the account
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Returns the unique login name of the user.
     *
     * @return username of the account
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Returns the encoded password of the user.
     *
     * @return encoded password including the algorithm prefix
     */
    public String getPasswordHash() {
        return this.passwordHash;
    }

    /**
     * Replaces the encoded password of the user.
     *
     * @param passwordHash encoded password including the algorithm prefix
     */
    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Returns the point in time the account was created.
     *
     * @return registration timestamp of the account
     */
    public Instant getRegisteredAt() {
        return this.registeredAt;
    }

    /**
     * Returns the point in time of the most recent successful login.
     *
     * @return timestamp of the most recent login
     */
    public Instant getLastLoginAt() {
        return this.lastLoginAt;
    }

    /**
     * Replaces the point in time of the most recent successful login.
     *
     * @param lastLoginAt timestamp of the most recent login
     */
    public void setLastLoginAt(final Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * Returns the CBOR encoded payload of the account.
     *
     * @return payload bytes as they are stored
     */
    public byte[] getPayload() {
        return this.payload;
    }

    /**
     * Replaces the CBOR encoded payload of the account.
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
