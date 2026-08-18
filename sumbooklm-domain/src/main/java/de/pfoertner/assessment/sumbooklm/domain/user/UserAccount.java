package de.pfoertner.assessment.sumbooklm.domain.user;

import java.util.Objects;
import java.util.UUID;

/**
 * A registered user of the application.
 *
 * <h2>Identity</h2>
 * An account is identified by {@code id}, which is stable for the lifetime of the account. The
 * username is unique as well, but it is a login credential rather than an identifier, and nothing
 * outside the authentication flow resolves an account by it.
 *
 * <h2>Absent Data</h2>
 * The account carries no password hash and no tokens. Both exist only where they are needed: the
 * hash in the persistence layer, the tokens in the security layer.
 *
 * @param id       stable identifier of the account, never {@code null}
 * @param username unique login name of the user, never {@code null}
 * @param profile  name the user is addressed by, never {@code null}
 * @param activity audit metadata of the account, never {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record UserAccount(UUID id, String username, UserProfile profile, AccountActivity activity) {

    /**
     * Creates the account.
     *
     * @param id       stable identifier of the account
     * @param username unique login name of the user
     * @param profile  name the user is addressed by
     * @param activity audit metadata of the account
     * @throws NullPointerException if any argument is {@code null}
     */
    public UserAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(activity, "activity must not be null");
    }
}
