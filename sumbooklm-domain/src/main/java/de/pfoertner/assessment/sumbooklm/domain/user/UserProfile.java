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

package de.pfoertner.assessment.sumbooklm.domain.user;

import java.util.Objects;

/**
 * Name a user is addressed by.
 *
 * <h2>Storage</h2>
 * Both components are descriptive rather than identifying: the application never resolves a user by
 * their name, only by their username. The profile is therefore part of the evolvable payload of an
 * account and not part of its relational contract.
 *
 * @param firstName given name of the user, never {@code null}
 * @param lastName  family name of the user, never {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record UserProfile(String firstName, String lastName) {

    /**
     * Creates the profile.
     *
     * @param firstName given name of the user
     * @param lastName  family name of the user
     * @throws NullPointerException if {@code firstName} or {@code lastName} is {@code null}
     */
    public UserProfile {
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
    }
}
