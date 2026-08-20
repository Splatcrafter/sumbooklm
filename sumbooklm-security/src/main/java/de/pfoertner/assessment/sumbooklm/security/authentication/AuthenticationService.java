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

package de.pfoertner.assessment.sumbooklm.security.authentication;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountEntity;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountMapper;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountPayload;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountRepository;
import de.pfoertner.assessment.sumbooklm.security.cookie.CookieCryptographyService;
import de.pfoertner.assessment.sumbooklm.security.token.RefreshTokenService;
import de.pfoertner.assessment.sumbooklm.security.token.TokenPair;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts and verifies credentials.
 *
 * <h2>Registration</h2>
 * A new account is stored with a hashed password and is authenticated immediately, so a client
 * receives the same token pair it would receive from a login. The registration timestamp, the login
 * timestamp and the network address are recorded at that moment; the login timestamp starts out
 * equal to the registration timestamp because the registration is the first login.
 *
 * <h2>Password Storage</h2>
 * Passwords are stored in the encoded form of the configured encoder, which carries the algorithm as
 * a prefix. A successful login re-encodes the password when the stored form no longer matches the
 * current encoding, which is what lets the algorithm be changed without invalidating accounts.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class AuthenticationService {

    /**
     * Storage of the accounts.
     */
    private final UserAccountRepository userAccountRepository;

    /**
     * Translator between account rows, their payload and the domain model.
     */
    private final UserAccountMapper userAccountMapper;

    /**
     * Encoder that produces and verifies password hashes.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Issuer of the token pair a successful authentication returns.
     */
    private final RefreshTokenService refreshTokenService;

    /**
     * Source of the key handle the client encrypts its stored token pair with.
     */
    private final CookieCryptographyService cookieCryptographyService;

    /**
     * Source of the current time, so that the recorded timestamps are deterministic in tests.
     */
    private final Clock clock;

    /**
     * Hash of a value nobody can present, verified against when no account matches a login. Running
     * the encoder on the failing path as well keeps the duration of a login from revealing whether
     * the username exists.
     */
    private final String unmatchableHash;

    /**
     * Creates the service.
     *
     * @param userAccountRepository     storage of the accounts
     * @param userAccountMapper         translator between rows, payload and the domain model
     * @param passwordEncoder           encoder that produces and verifies password hashes
     * @param refreshTokenService       issuer of the token pair
     * @param cookieCryptographyService source of the client key handle
     * @param clock                     source of the current time
     */
    public AuthenticationService(final UserAccountRepository userAccountRepository,
                                 final UserAccountMapper userAccountMapper,
                                 final PasswordEncoder passwordEncoder,
                                 final RefreshTokenService refreshTokenService,
                                 final CookieCryptographyService cookieCryptographyService,
                                 final Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.cookieCryptographyService = cookieCryptographyService;
        this.clock = clock;
        this.unmatchableHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Creates an account and authenticates it.
     *
     * @param command data of the account to create
     * @return the created account together with a freshly issued token pair
     * @throws UsernameAlreadyTakenException if the username is already in use
     */
    @Transactional
    public AuthenticationResult register(final RegistrationCommand command) {
        if (this.userAccountRepository.existsByUsername(command.username())) {
            throw new UsernameAlreadyTakenException(command.username());
        }

        final Instant now = Instant.now(this.clock);
        final UserAccountPayload payload = new UserAccountPayload(
                command.firstName(), command.lastName(), command.ipAddress(), command.ipAddress());
        final UserAccountEntity account = new UserAccountEntity(
                UUID.randomUUID(),
                command.username(),
                this.passwordEncoder.encode(command.password()),
                now,
                now,
                this.userAccountMapper.writePayload(payload),
                PayloadSchemaVersion.CURRENT);

        final UserAccountEntity stored;
        try {
            stored = this.userAccountRepository.saveAndFlush(account);
        } catch (final DataIntegrityViolationException e) {
            // The check above loses against a concurrent registration of the same username; the
            // unique constraint is what actually decides, so its violation is reported as the
            // same conflict.
            throw new UsernameAlreadyTakenException(command.username());
        }

        return authenticated(stored, command.ipAddress());
    }

    /**
     * Verifies credentials and authenticates the matching account.
     *
     * @param command credentials to verify
     * @return the authenticated account together with a freshly issued token pair
     * @throws InvalidCredentialsException if no account matches the username and password
     */
    @Transactional
    public AuthenticationResult login(final LoginCommand command) {
        final UserAccountEntity account = this.userAccountRepository.findByUsername(command.username())
                .orElse(null);
        if (account == null) {
            this.passwordEncoder.matches(command.password(), this.unmatchableHash);
            throw new InvalidCredentialsException();
        }
        if (!this.passwordEncoder.matches(command.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (this.passwordEncoder.upgradeEncoding(account.getPasswordHash())) {
            account.setPasswordHash(this.passwordEncoder.encode(command.password()));
        }

        recordLogin(account, command.ipAddress());
        return authenticated(account, command.ipAddress());
    }

    /**
     * Writes the login timestamp and the network address of the current login into the account.
     *
     * @param account   account that was authenticated
     * @param ipAddress network address the login was requested from
     */
    private void recordLogin(final UserAccountEntity account, final String ipAddress) {
        final UserAccountPayload payload = this.userAccountMapper.readPayload(account);
        account.setLastLoginAt(Instant.now(this.clock));
        account.setPayload(this.userAccountMapper.writePayload(new UserAccountPayload(
                payload.firstName(), payload.lastName(), payload.registrationIpAddress(), ipAddress)));
        account.setPayloadVersion(PayloadSchemaVersion.CURRENT);
    }

    /**
     * Issues the token pair and the key handle of an authenticated account.
     *
     * @param account   account that was authenticated
     * @param ipAddress network address the tokens are issued to
     * @return the account in its domain form together with the issued tokens
     */
    private AuthenticationResult authenticated(final UserAccountEntity account, final String ipAddress) {
        final TokenPair tokens = this.refreshTokenService.issue(account, ipAddress);
        return new AuthenticationResult(
                this.userAccountMapper.toDomain(account),
                tokens,
                this.cookieCryptographyService.issueKeyHandle());
    }
}
