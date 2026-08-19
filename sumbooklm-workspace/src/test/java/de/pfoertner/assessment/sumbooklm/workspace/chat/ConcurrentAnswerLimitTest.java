package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the bound on how many answers one account may have in flight.
 *
 * <h2>Why It Is Tested Alone</h2>
 * The rule is arithmetic over a map, and the situations it exists for are races. Driving it through
 * the application would mean holding several answers open to observe one refusal, which states the
 * rule far less clearly than counting permits does.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ConcurrentAnswerLimitTest {

    /**
     * Number of permits one account holds, matching the value the rule is configured with.
     */
    private static final int LIMIT = 3;

    /**
     * Rule under test.
     */
    private final ConcurrentAnswerLimit limit = new ConcurrentAnswerLimit();

    /**
     * Creates the test class.
     */
    ConcurrentAnswerLimitTest() {
    }

    /**
     * Verifies that an account may take as many permits as the limit allows and no more.
     */
    @Test
    void anAccountIsRefusedBeyondTheLimit() {
        final UUID account = UUID.randomUUID();

        for (int taken = 0; taken < LIMIT; taken += 1) {
            assertThat(this.limit.tryAcquire(account))
                    .describedAs("permit %d has to be available", taken + 1)
                    .isTrue();
        }

        assertThat(this.limit.tryAcquire(account)).isFalse();
        assertThat(this.limit.tryAcquire(account)).isFalse();
    }

    /**
     * Verifies that a returned permit can be taken again, which is what makes the bound one on
     * answers in flight rather than on answers ever asked for.
     */
    @Test
    void aReturnedPermitCanBeTakenAgain() {
        final UUID account = UUID.randomUUID();
        for (int taken = 0; taken < LIMIT; taken += 1) {
            this.limit.tryAcquire(account);
        }

        this.limit.release(account);

        assertThat(this.limit.tryAcquire(account)).isTrue();
        assertThat(this.limit.tryAcquire(account)).isFalse();
    }

    /**
     * Verifies that an account which returned everything it took starts over, which is also what
     * keeps the rule from remembering every account that ever asked.
     */
    @Test
    void anAccountThatReturnedEverythingStartsOver() {
        final UUID account = UUID.randomUUID();
        for (int taken = 0; taken < LIMIT; taken += 1) {
            this.limit.tryAcquire(account);
        }
        for (int returned = 0; returned < LIMIT; returned += 1) {
            this.limit.release(account);
        }

        for (int taken = 0; taken < LIMIT; taken += 1) {
            assertThat(this.limit.tryAcquire(account)).isTrue();
        }
    }

    /**
     * Verifies that returning a permit that was never taken changes nothing, so that a duplicated
     * ending cannot hand an account more permits than the limit says.
     */
    @Test
    void returningAPermitThatWasNotTakenChangesNothing() {
        final UUID account = UUID.randomUUID();

        this.limit.release(account);
        this.limit.release(account);

        for (int taken = 0; taken < LIMIT; taken += 1) {
            assertThat(this.limit.tryAcquire(account)).isTrue();
        }
        assertThat(this.limit.tryAcquire(account)).isFalse();
    }

    /**
     * Verifies that the accounts are counted apart, so that one account asking many questions cannot
     * refuse another.
     */
    @Test
    void accountsAreCountedApart() {
        final UUID one = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        for (int taken = 0; taken < LIMIT; taken += 1) {
            this.limit.tryAcquire(one);
        }

        assertThat(this.limit.tryAcquire(one)).isFalse();
        assertThat(this.limit.tryAcquire(other)).isTrue();
    }
}
