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

package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.persistence.chat.AskedQuestionEntity;
import de.pfoertner.assessment.sumbooklm.persistence.chat.AskedQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises how often one account may ask.
 *
 * <h2>What the Window Means</h2>
 * The bound is over the hour before now rather than over a calendar hour, so it moves with every
 * question. Where an account has reached it, the answer has to say how long it will be until the
 * oldest question inside the window leaves it, because a client that is told only "not now" can do
 * nothing but retry. That span is the part with edges: it can be computed from a question that is
 * about to leave the window, and it can come out negative if the clock moved on while the row was
 * being read.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class QuestionRateLimitTest {

    /**
     * Moment every case is answered at.
     */
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Number of questions the deployment of the cases allows within an hour.
     */
    private static final int ALLOWED = 60;

    /**
     * Store of the questions that were asked.
     */
    private AskedQuestionRepository askedQuestionRepository;

    /**
     * Rule under test.
     */
    private QuestionRateLimit limit;

    /**
     * Account of the cases.
     */
    private final UUID userId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    QuestionRateLimitTest() {
    }

    /**
     * Builds the rule and the store it counts through.
     */
    @BeforeEach
    void setUp() {
        this.askedQuestionRepository = mock(AskedQuestionRepository.class);
        this.limit = new QuestionRateLimit(this.askedQuestionRepository,
                new ChatProperties(ALLOWED), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Verifies that an account below the bound has its question recorded under the moment it was
     * asked, which is what the next count reads.
     */
    @Test
    void aQuestionBelowTheBoundIsRecorded() {
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn(5L);

        assertThatCode(() -> this.limit.record(this.userId)).doesNotThrowAnyException();

        final ArgumentCaptor<AskedQuestionEntity> stored =
                ArgumentCaptor.forClass(AskedQuestionEntity.class);
        verify(this.askedQuestionRepository).save(stored.capture());
        assertThat(stored.getValue().getUserId()).isEqualTo(this.userId);
        assertThat(stored.getValue().getAskedAt()).isEqualTo(NOW);
    }

    /**
     * Verifies that the questions which have left the window are removed before the rest are
     * counted, so that the count is over the window rather than over everything ever asked.
     */
    @Test
    void whatLeftTheWindowIsRemovedBeforeCounting() {
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn(0L);

        this.limit.record(this.userId);

        verify(this.askedQuestionRepository)
                .deleteByUserIdAndAskedAtLessThan(this.userId, NOW.minus(Duration.ofHours(1)));
    }

    /**
     * Verifies that an account which has reached the bound is refused and that nothing further is
     * recorded, so that being refused does not push the window along.
     */
    @Test
    void anAccountAtTheBoundIsRefused() {
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn((long) ALLOWED);
        when(this.askedQuestionRepository.findOldestWithin(eq(this.userId), any()))
                .thenReturn(Optional.of(NOW.minusSeconds(3_000)));

        assertThatThrownBy(() -> this.limit.record(this.userId))
                .isInstanceOf(QuestionsTooOftenException.class);
        verify(this.askedQuestionRepository, never()).save(any());
    }

    /**
     * Verifies that a refused account is told how long it is until the oldest question inside the
     * window leaves it, which is when it may ask again.
     */
    @Test
    void aRefusedAccountIsToldHowLongToWait() {
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn((long) ALLOWED);
        when(this.askedQuestionRepository.findOldestWithin(eq(this.userId), any()))
                .thenReturn(Optional.of(NOW.minusSeconds(3_540)));

        assertThatThrownBy(() -> this.limit.record(this.userId))
                .isInstanceOf(QuestionsTooOftenException.class)
                .extracting(failure -> ((QuestionsTooOftenException) failure).retryAfter())
                .isEqualTo(Duration.ofSeconds(60));
    }

    /**
     * Verifies that a question at the very edge of the window is answered with no wait at all rather
     * than with a negative one, which is what a row that left the window between the two queries
     * would produce.
     */
    @Test
    void aWaitIsNeverNegative() {
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn((long) ALLOWED);
        when(this.askedQuestionRepository.findOldestWithin(eq(this.userId), any()))
                .thenReturn(Optional.of(NOW.minusSeconds(7_200)));

        assertThatThrownBy(() -> this.limit.record(this.userId))
                .isInstanceOf(QuestionsTooOftenException.class)
                .extracting(failure -> ((QuestionsTooOftenException) failure).retryAfter())
                .isEqualTo(Duration.ZERO);
    }

    /**
     * Verifies that an account refused without any question left inside the window is told to wait a
     * whole window, which is the case where the count and the oldest row disagree.
     */
    @Test
    void anAccountWithoutAnOldestQuestionWaitsAWholeWindow() {
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn((long) ALLOWED);
        when(this.askedQuestionRepository.findOldestWithin(eq(this.userId), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.limit.record(this.userId))
                .isInstanceOf(QuestionsTooOftenException.class)
                .extracting(failure -> ((QuestionsTooOftenException) failure).retryAfter())
                .isEqualTo(Duration.ZERO);
    }

    /**
     * Verifies that an account beyond the bound is refused as well, which is the state a deployment
     * that lowered its setting leaves its accounts in.
     */
    @Test
    void anAccountBeyondTheBoundIsRefused() {
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn(ALLOWED + 40L);
        when(this.askedQuestionRepository.findOldestWithin(eq(this.userId), any()))
                .thenReturn(Optional.of(NOW));

        assertThatThrownBy(() -> this.limit.record(this.userId))
                .isInstanceOf(QuestionsTooOftenException.class)
                .hasMessageContaining(this.userId.toString());
    }

    /**
     * Verifies that a deployment which allows nothing refuses every question, because a setting of
     * zero is a way of closing the endpoint and has to behave like one.
     */
    @Test
    void aDeploymentThatAllowsNothingRefusesEverything() {
        final QuestionRateLimit closed = new QuestionRateLimit(this.askedQuestionRepository,
                new ChatProperties(0), Clock.fixed(NOW, ZoneOffset.UTC));
        when(this.askedQuestionRepository.countByUserIdAndAskedAtGreaterThanEqual(eq(this.userId), any()))
                .thenReturn(0L);
        when(this.askedQuestionRepository.findOldestWithin(eq(this.userId), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> closed.record(this.userId))
                .isInstanceOf(QuestionsTooOftenException.class);
    }

    /**
     * Verifies that the sweep removes every question older than the window, of every account, which
     * is what keeps the table from growing with accounts that stopped asking.
     */
    @Test
    void theSweepRemovesWhatLeftTheWindow() {
        when(this.askedQuestionRepository.deleteAskedBefore(any())).thenReturn(12);

        this.limit.sweep();

        verify(this.askedQuestionRepository).deleteAskedBefore(NOW.minus(Duration.ofHours(1)));
    }

    /**
     * Verifies that a sweep which finds nothing is a sweep that does nothing, so that a quiet
     * deployment writes nothing worth reading into its log.
     */
    @Test
    void aSweepThatFindsNothingChangesNothing() {
        when(this.askedQuestionRepository.deleteAskedBefore(any())).thenReturn(0);

        assertThatCode(() -> this.limit.sweep()).doesNotThrowAnyException();
    }
}
