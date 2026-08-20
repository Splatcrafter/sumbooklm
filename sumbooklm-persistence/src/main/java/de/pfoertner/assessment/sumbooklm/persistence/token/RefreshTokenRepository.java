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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for issued refresh tokens.
 *
 * <h2>Cleanup</h2>
 * {@link #deleteInvalidated(Instant)} is a bulk statement rather than a derived delete, so that the
 * weekly cleanup removes rows in one round trip instead of loading every row it is about to delete.
 * Bulk statements bypass the persistence context, which is acceptable here because the job runs
 * without any other work in its transaction.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Deletes every token that can no longer be used, meaning every revoked and every expired one.
     *
     * @param threshold point in time a token counts as expired before
     * @return number of deleted rows
     */
    @Modifying
    @Query("delete from RefreshTokenEntity token where token.revokedAt is not null or token.expiresAt < :threshold")
    int deleteInvalidated(@Param("threshold") Instant threshold);

    /**
     * Revokes every token of an account that is still usable.
     *
     * @param userId    identifier of the account whose tokens are revoked
     * @param revokedAt point in time the tokens are revoked at
     * @return number of revoked rows
     */
    @Modifying
    @Query("update RefreshTokenEntity token set token.revokedAt = :revokedAt "
            + "where token.user.id = :userId and token.revokedAt is null")
    int revokeAllOfUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
