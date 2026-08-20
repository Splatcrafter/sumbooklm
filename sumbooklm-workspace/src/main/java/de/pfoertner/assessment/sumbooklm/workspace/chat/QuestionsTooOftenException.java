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

import java.time.Duration;
import java.util.UUID;

/**
 * Raised when an account has asked as many questions within the last hour as it may ask.
 *
 * <h2>Not the Same as Too Many at Once</h2>
 * {@link TooManyQuestionsException} says that an account is busy with its own answers and passes as
 * soon as one of them arrives. This one says that it has asked often enough for a while, and how long
 * that while is has to travel with it, because a caller that cannot tell the two apart would retry a
 * request that will be refused for the next hour.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class QuestionsTooOftenException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Time after which the account may ask again.
     */
    private final Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param userId     identifier of the account that asked
     * @param retryAfter time after which the oldest question leaves the window
     */
    public QuestionsTooOftenException(final UUID userId, final Duration retryAfter) {
        super("The account " + userId + " has asked as many questions as it may ask for the next "
                + retryAfter.toSeconds() + " seconds");
        this.retryAfter = retryAfter;
    }

    /**
     * Returns the time after which the account may ask again.
     *
     * @return time until the oldest question leaves the window, never negative
     */
    public Duration retryAfter() {
        return this.retryAfter;
    }
}
