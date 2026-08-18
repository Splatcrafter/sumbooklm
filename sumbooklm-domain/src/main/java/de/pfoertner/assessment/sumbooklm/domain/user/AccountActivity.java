package de.pfoertner.assessment.sumbooklm.domain.user;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit metadata recorded for an account.
 *
 * <h2>Content</h2>
 * The record answers when an account came into existence and when it was last used, and it keeps the
 * network origin of both events. Registration data is written once; the login data is overwritten on
 * every successful authentication, so the record describes the most recent login rather than a
 * history of logins.
 *
 * @param registeredAt           point in time the account was created, never {@code null}
 * @param registrationIpAddress  network address the registration was requested from, never {@code null}
 * @param lastLoginAt            point in time of the most recent successful login, never {@code null}
 * @param lastLoginIpAddress     network address of the most recent successful login, never {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record AccountActivity(Instant registeredAt,
                              String registrationIpAddress,
                              Instant lastLoginAt,
                              String lastLoginIpAddress) {

    /**
     * Creates the activity record.
     *
     * @param registeredAt          point in time the account was created
     * @param registrationIpAddress network address the registration was requested from
     * @param lastLoginAt           point in time of the most recent successful login
     * @param lastLoginIpAddress    network address of the most recent successful login
     * @throws NullPointerException if any argument is {@code null}
     */
    public AccountActivity {
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
        Objects.requireNonNull(registrationIpAddress, "registrationIpAddress must not be null");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt must not be null");
        Objects.requireNonNull(lastLoginIpAddress, "lastLoginIpAddress must not be null");
    }
}
