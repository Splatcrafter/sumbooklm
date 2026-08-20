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

package de.pfoertner.assessment.sumbooklm.security.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes refresh tokens that can no longer be used.
 *
 * <h2>Schedule</h2>
 * The job runs weekly, at midnight between Saturday and Sunday. A refresh token stays valid for
 * ninety days, so rows accumulate slowly and there is nothing to gain from a shorter interval.
 *
 * <h2>What Is Removed</h2>
 * Only rows that can no longer authorise anything are deleted: expired tokens and tokens that were
 * revoked, either by rotation or by an explicit logout. Deleting them does not change what any
 * client is able to do, it only keeps the table proportional to the number of open sessions.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class RefreshTokenCleanupJob {

    /**
     * Logger reporting how many rows a run removed.
     */
    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    /**
     * Service performing the deletion.
     */
    private final RefreshTokenService refreshTokenService;

    /**
     * Creates the job.
     *
     * @param refreshTokenService service performing the deletion
     */
    public RefreshTokenCleanupJob(final RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Deletes every revoked and every expired refresh token.
     */
    @Scheduled(cron = "0 0 0 * * SUN", zone = "UTC")
    public void removeInvalidatedTokens() {
        final int removed = this.refreshTokenService.deleteInvalidatedTokens();
        LOG.info("Removed {} invalidated refresh tokens", removed);
    }
}
