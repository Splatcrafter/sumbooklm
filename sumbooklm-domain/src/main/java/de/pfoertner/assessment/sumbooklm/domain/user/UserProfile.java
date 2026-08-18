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
