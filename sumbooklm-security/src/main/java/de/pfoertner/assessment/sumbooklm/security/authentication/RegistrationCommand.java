package de.pfoertner.assessment.sumbooklm.security.authentication;

/**
 * Request to create an account.
 *
 * <h2>Network Address</h2>
 * The address is not part of what a client sends. It is determined by the transport layer from the
 * connection and passed in here, so that the security module records where an account was created
 * from without depending on the servlet API.
 *
 * @param username  login name the account is created under, unique across all accounts
 * @param firstName given name of the user
 * @param lastName  family name of the user
 * @param password  clear text password, hashed before it is stored and never retained
 * @param ipAddress network address the registration was requested from
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record RegistrationCommand(String username,
                                  String firstName,
                                  String lastName,
                                  String password,
                                  String ipAddress) {
}
