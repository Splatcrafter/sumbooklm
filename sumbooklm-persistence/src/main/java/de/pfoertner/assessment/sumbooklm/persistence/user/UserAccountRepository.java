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
