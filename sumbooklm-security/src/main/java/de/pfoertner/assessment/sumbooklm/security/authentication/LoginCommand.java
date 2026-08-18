package de.pfoertner.assessment.sumbooklm.security.authentication;

/**
 * Request to authenticate against an existing account.
 *
 * @param username  login name of the account
 * @param password  clear text password, compared against the stored hash and never retained
 * @param ipAddress network address the login was requested from
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record LoginCommand(String username, String password, String ipAddress) {
}
