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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the question a session is judged by.
 *
 * <h2>Why the Two Reasons Are Held Apart</h2>
 * A session may be unusable because it was ended and because it ran out, and both answers are the
 * same to a caller. What differs is when each happens, and the moment they meet is the case that is
 * easy to get wrong: a token expiring exactly now is no longer usable, because a request arriving at
 * that instant is one instant too late.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class RefreshTokenEntityTest {

    /**
     * Moment the sessions of the cases were issued at.
     */
    private static final Instant ISSUED = Instant.parse("2026-08-20T10:00:00Z");

    /**
     * Moment the sessions of the cases run out at.
     */
    private static final Instant EXPIRES = Instant.parse("2026-11-18T10:00:00Z");

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    RefreshTokenEntityTest() {
    }

    /**
     * Verifies that a session which was neither ended nor ran out is usable.
     */
    @Test
    void anOpenSessionIsUsable() {
        assertThat(session().isUsableAt(ISSUED.plusSeconds(60))).isTrue();
    }

    /**
     * Verifies that a session which was ended is unusable from then on, even though it has not run
     * out, which is what logging out and reuse detection both rest on.
     */
    @Test
    void anEndedSessionIsUnusable() {
        final RefreshTokenEntity token = session();
        token.revoke(ISSUED.plusSeconds(60));

        assertThat(token.isUsableAt(ISSUED.plusSeconds(61))).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(ISSUED.plusSeconds(60));
    }

    /**
     * Verifies that a session is unusable at the very moment it runs out, rather than one instant
     * later.
     */
    @Test
    void aSessionIsUnusableAtTheMomentItRunsOut() {
        assertThat(session().isUsableAt(EXPIRES.minusMillis(1))).isTrue();
        assertThat(session().isUsableAt(EXPIRES)).isFalse();
        assertThat(session().isUsableAt(EXPIRES.plusSeconds(1))).isFalse();
    }

    /**
     * Verifies that a session which was ended before it was even issued is unusable throughout,
     * which is the state a session revoked by another one of the same account is left in.
     */
    @Test
    void aSessionEndedAtOnceIsNeverUsable() {
        final RefreshTokenEntity token = session();
        token.revoke(ISSUED);

        assertThat(token.isUsableAt(ISSUED)).isFalse();
        assertThat(token.isUsableAt(ISSUED.plusSeconds(3_600))).isFalse();
    }

    /**
     * Verifies that a fresh session carries no moment of ending, which is what tells the revocation
     * of a session apart from the ending of one that was already closed.
     */
    @Test
    void aFreshSessionWasNotEnded() {
        assertThat(session().getRevokedAt()).isNull();
    }

    /**
     * Verifies that a session states what it was issued with, because the hash is what a presented
     * token is compared against and the address is what a deployment reads.
     */
    @Test
    void aSessionStatesWhatItWasIssuedWith() {
        final RefreshTokenEntity token = session();

        assertThat(token.getTokenHash()).isEqualTo("abc123");
        assertThat(token.getIssuedToIpAddress()).isEqualTo("203.0.113.7");
        assertThat(token.getIssuedAt()).isEqualTo(ISSUED);
        assertThat(token.getExpiresAt()).isEqualTo(EXPIRES);
        assertThat(token.getId()).isNotNull();
        assertThat(token.getUser()).isNotNull();
    }

    /**
     * Builds the session the cases judge.
     *
     * @return a session that was issued and neither ended nor ran out
     */
    private static RefreshTokenEntity session() {
        final UserAccountEntity user = new UserAccountEntity(UUID.randomUUID(), "erik", "hash",
                ISSUED, ISSUED, new byte[]{1}, 110);
        return new RefreshTokenEntity(UUID.randomUUID(), user, "abc123", ISSUED, EXPIRES, "203.0.113.7");
    }
}
