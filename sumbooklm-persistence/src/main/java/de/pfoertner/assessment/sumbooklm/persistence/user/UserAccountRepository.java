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

package de.pfoertner.assessment.sumbooklm.persistence.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for user accounts.
 *
 * <h2>Lookup by Username</h2>
 * The username is unique, so the lookup below returns at most one row. It is the only query that
 * reaches an account without knowing its identifier, which is what makes the username a credential
 * rather than a second identifier.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    /**
     * Finds the account with the given username.
     *
     * @param username login name to look the account up by
     * @return the matching account, or an empty result if no account carries the username
     */
    Optional<UserAccountEntity> findByUsername(String username);

    /**
     * Reports whether an account with the given username exists.
     *
     * @param username login name to check
     * @return {@code true} if an account carries the username, {@code false} otherwise
     */
    boolean existsByUsername(String username);
}
