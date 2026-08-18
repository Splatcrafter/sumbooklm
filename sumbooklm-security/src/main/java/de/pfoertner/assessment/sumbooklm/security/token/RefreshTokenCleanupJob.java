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
