package de.pfoertner.assessment.sumbooklm.security.token;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the schedule the refresh token cleanup runs on.
 *
 * <h2>Approach</h2>
 * The expression is read from the annotation instead of being repeated in the test, and it is then
 * evaluated. A test that only compared the string would keep passing after the expression was
 * changed into something that no longer means what the requirement states.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class RefreshTokenCleanupJobTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    RefreshTokenCleanupJobTest() {
    }

    /**
     * Verifies that the cleanup runs weekly, at the start of every Sunday.
     *
     * @throws NoSuchMethodException if the scheduled method was renamed
     */
    @Test
    void runsEverySundayAtMidnight() throws NoSuchMethodException {
        final Scheduled schedule = RefreshTokenCleanupJob.class
                .getMethod("removeInvalidatedTokens")
                .getAnnotation(Scheduled.class);
        assertThat(schedule).isNotNull();
        assertThat(schedule.zone()).isEqualTo("UTC");

        final CronExpression expression = CronExpression.parse(schedule.cron());
        final LocalDateTime firstRun = expression.next(LocalDateTime.parse("2026-08-18T12:00:00"));

        assertThat(firstRun).isEqualTo(LocalDateTime.parse("2026-08-23T00:00:00"));
        assertThat(expression.next(firstRun)).isEqualTo(LocalDateTime.parse("2026-08-30T00:00:00"));
    }
}
