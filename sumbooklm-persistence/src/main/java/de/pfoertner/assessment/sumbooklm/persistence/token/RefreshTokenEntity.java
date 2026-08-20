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

package de.pfoertner.assessment.sumbooklm.persistence.token;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.jspecify.annotations.Nullable;

/**
 * Relational row of an issued refresh token.
 *
 * <h2>Identity</h2>
 * The identifier of the row is the identifier carried in the {@code jti} claim of the refresh token
 * it describes, and it is also the value an access token references to name the session it belongs
 * to. A token can therefore be located from either half of a token pair.
 *
 * <h2>Digest Instead of Token</h2>
 * The token itself is never stored. {@code token_hash} holds a digest of it, which is enough to
 * confirm a presented token and not enough to issue one.
 *
 * <h2>Lifecycle</h2>
 * A row is written when a token is issued and is never mutated except through {@code revoked_at}.
 * Rotation revokes the previous row and writes a new one, so the table keeps the history of a
 * session until the cleanup job removes the rows that can no longer be used.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Entity
@Table(name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "ix_refresh_token_expires_at", columnList = "expires_at"))
public class RefreshTokenEntity {

    /**
     * Identifier of the token, matching the {@code jti} claim of the refresh token it describes.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Account the token was issued for.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_token_user"))
    private UserAccountEntity user;

    /**
     * Hexadecimal SHA-256 digest of the issued token.
     */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    /**
     * Point in time the token was issued.
     */
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    /**
     * Point in time the token stops being accepted.
     */
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /**
     * Point in time the token was revoked, or {@code null} while the token is still usable.
     */
    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    /**
     * Network address the token was issued to.
     */
    @Column(name = "issued_to_ip", nullable = false, length = 45, updatable = false)
    private String issuedToIpAddress;

    /**
     * Creates an empty row. Required by the persistence provider.
     */
    protected RefreshTokenEntity() {
    }

    /**
     * Creates a row for a newly issued token.
     *
     * @param id                identifier of the token, matching its {@code jti} claim
     * @param user              account the token was issued for
     * @param tokenHash         hexadecimal SHA-256 digest of the issued token
     * @param issuedAt          point in time the token was issued
     * @param expiresAt         point in time the token stops being accepted
     * @param issuedToIpAddress network address the token was issued to
     */
    public RefreshTokenEntity(final UUID id,
                              final UserAccountEntity user,
                              final String tokenHash,
                              final Instant issuedAt,
                              final Instant expiresAt,
                              final String issuedToIpAddress) {
        this.id = id;
        this.user = user;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.issuedToIpAddress = issuedToIpAddress;
    }

    /**
     * Returns the identifier of the token.
     *
     * @return identifier matching the {@code jti} claim of the token
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Returns the account the token was issued for.
     *
     * @return account the token belongs to
     */
    public UserAccountEntity getUser() {
        return this.user;
    }

    /**
     * Returns the digest of the issued token.
     *
     * @return hexadecimal SHA-256 digest of the token
     */
    public String getTokenHash() {
        return this.tokenHash;
    }

    /**
     * Returns the point in time the token was issued.
     *
     * @return issuing timestamp of the token
     */
    public Instant getIssuedAt() {
        return this.issuedAt;
    }

    /**
     * Returns the point in time the token stops being accepted.
     *
     * @return expiry timestamp of the token
     */
    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    /**
     * Returns the point in time the token was revoked.
     *
     * @return revocation timestamp, or {@code null} while the token is still usable
     */
    public @Nullable Instant getRevokedAt() {
        return this.revokedAt;
    }

    /**
     * Marks the token as revoked.
     *
     * @param revokedAt point in time the token was revoked
     */
    public void revoke(final Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    /**
     * Returns the network address the token was issued to.
     *
     * @return network address recorded when the token was issued
     */
    public String getIssuedToIpAddress() {
        return this.issuedToIpAddress;
    }

    /**
     * Reports whether the token is still usable at the given point in time.
     *
     * @param at point in time to evaluate the token at
     * @return {@code true} if the token is neither revoked nor expired, {@code false} otherwise
     */
    public boolean isUsableAt(final Instant at) {
        return this.revokedAt == null && this.expiresAt.isAfter(at);
    }
}
