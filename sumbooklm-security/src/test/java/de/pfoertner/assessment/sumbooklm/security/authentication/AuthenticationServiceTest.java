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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.user.AccountActivity;
import de.pfoertner.assessment.sumbooklm.domain.user.UserAccount;
import de.pfoertner.assessment.sumbooklm.domain.user.UserProfile;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountEntity;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountMapper;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountPayload;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountRepository;
import de.pfoertner.assessment.sumbooklm.security.cookie.CookieCryptographyService;
import de.pfoertner.assessment.sumbooklm.security.token.IssuedToken;
import de.pfoertner.assessment.sumbooklm.security.token.RefreshTokenService;
import de.pfoertner.assessment.sumbooklm.security.token.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises registration and login.
 *
 * <h2>What the Cases Are About</h2>
 * Two things decide whether this code is safe rather than merely working. The first is that a login
 * with an unknown username costs the same as one with a known username, because a difference in
 * timing is a way of asking which accounts exist. The second is that the check for a taken username
 * is not what actually decides: two registrations of the same name can pass it at once, and the
 * unique constraint of the table has to be answered as the same conflict rather than as a failure.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class AuthenticationServiceTest {

    /**
     * Moment every case is answered at.
     */
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Store of the accounts.
     */
    private UserAccountRepository userAccountRepository;

    /**
     * Reader of the stored part of an account.
     */
    private UserAccountMapper userAccountMapper;

    /**
     * Hash function passwords are compared with.
     */
    private PasswordEncoder passwordEncoder;

    /**
     * Source of the tokens an authentication ends with.
     */
    private RefreshTokenService refreshTokenService;

    /**
     * Source of the handle a client encrypts its stored session with.
     */
    private CookieCryptographyService cookieCryptographyService;

    /**
     * Service under test.
     */
    private AuthenticationService service;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    AuthenticationServiceTest() {
    }

    /**
     * Builds the service and everything it authenticates through.
     */
    @BeforeEach
    void setUp() {
        this.userAccountRepository = mock(UserAccountRepository.class);
        this.userAccountMapper = mock(UserAccountMapper.class);
        this.passwordEncoder = mock(PasswordEncoder.class);
        this.refreshTokenService = mock(RefreshTokenService.class);
        this.cookieCryptographyService = mock(CookieCryptographyService.class);

        when(this.passwordEncoder.encode(anyString())).thenAnswer(
                invocation -> "hash:" + invocation.getArgument(0, String.class));
        when(this.userAccountMapper.writePayload(any())).thenReturn(new byte[]{1, 2});
        when(this.userAccountMapper.readPayload(any())).thenReturn(
                new UserAccountPayload("Erik", "Pfoertner", "203.0.113.7", "203.0.113.7"));
        when(this.userAccountMapper.toDomain(any())).thenAnswer(invocation -> {
            final UserAccountEntity entity = invocation.getArgument(0, UserAccountEntity.class);
            return new UserAccount(entity.getId(), entity.getUsername(),
                    new UserProfile("Erik", "Pfoertner"),
                    new AccountActivity(entity.getRegisteredAt(), "203.0.113.7",
                            entity.getLastLoginAt(), "203.0.113.7"));
        });
        when(this.refreshTokenService.issue(any(), anyString())).thenReturn(new TokenPair(
                new IssuedToken("access", UUID.randomUUID(), NOW, NOW.plusSeconds(300)),
                new IssuedToken("refresh", UUID.randomUUID(), NOW, NOW.plusSeconds(7_776_000))));
        when(this.cookieCryptographyService.issueKeyHandle()).thenReturn("handle");

        this.service = new AuthenticationService(this.userAccountRepository, this.userAccountMapper,
                this.passwordEncoder, this.refreshTokenService, this.cookieCryptographyService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Verifies that a registration stores the account with a hashed password and answers with a
     * token pair, so that a new user does not have to log in again straight away.
     */
    @Test
    void aRegistrationStoresTheAccountAndAnswersWithTokens() {
        when(this.userAccountRepository.existsByUsername("erik")).thenReturn(false);
        when(this.userAccountRepository.saveAndFlush(any())).thenAnswer(
                invocation -> invocation.getArgument(0, UserAccountEntity.class));

        final AuthenticationResult result = this.service.register(new RegistrationCommand(
                "erik", "Erik", "Pfoertner", "a-long-enough-password", "203.0.113.7"));

        final ArgumentCaptor<UserAccountEntity> stored = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(this.userAccountRepository).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getUsername()).isEqualTo("erik");
        assertThat(stored.getValue().getPasswordHash()).isEqualTo("hash:a-long-enough-password");
        assertThat(stored.getValue().getRegisteredAt()).isEqualTo(NOW);
        assertThat(result.tokens().accessToken().value()).isEqualTo("access");
        assertThat(result.cookieKeyHandle()).isEqualTo("handle");
        assertThat(result.account().username()).isEqualTo("erik");
    }

    /**
     * Verifies that the clear text password is never stored, which is the one thing a registration
     * must not do.
     */
    @Test
    void theClearTextPasswordIsNeverStored() {
        when(this.userAccountRepository.saveAndFlush(any())).thenAnswer(
                invocation -> invocation.getArgument(0, UserAccountEntity.class));

        this.service.register(new RegistrationCommand(
                "erik", "Erik", "Pfoertner", "a-long-enough-password", "203.0.113.7"));

        final ArgumentCaptor<UserAccountEntity> stored = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(this.userAccountRepository).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getPasswordHash()).doesNotStartWith("a-long-enough-password");
    }

    /**
     * Verifies that a username somebody already holds is refused before anything is written.
     */
    @Test
    void aTakenUsernameIsRefused() {
        when(this.userAccountRepository.existsByUsername("erik")).thenReturn(true);

        assertThatThrownBy(() -> this.service.register(new RegistrationCommand(
                "erik", "Erik", "Pfoertner", "a-long-enough-password", "203.0.113.7")))
                .isInstanceOf(UsernameAlreadyTakenException.class)
                .hasMessageContaining("erik");
        verify(this.userAccountRepository, never()).saveAndFlush(any());
    }

    /**
     * Verifies that a registration which loses the race against another one of the same username is
     * answered as the same conflict, because the constraint of the table is what actually decides.
     */
    @Test
    void aRegistrationThatLosesTheRaceIsTheSameConflict() {
        when(this.userAccountRepository.existsByUsername("erik")).thenReturn(false);
        when(this.userAccountRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_user_account_username"));

        assertThatThrownBy(() -> this.service.register(new RegistrationCommand(
                "erik", "Erik", "Pfoertner", "a-long-enough-password", "203.0.113.7")))
                .isInstanceOf(UsernameAlreadyTakenException.class);
    }

    /**
     * Verifies that a login with the right password answers with tokens and records where and when
     * the account was used.
     */
    @Test
    void aCorrectPasswordIsAnsweredWithTokens() {
        final UserAccountEntity account = account();
        when(this.userAccountRepository.findByUsername("erik")).thenReturn(Optional.of(account));
        when(this.passwordEncoder.matches("secret-password", account.getPasswordHash())).thenReturn(true);

        final AuthenticationResult result =
                this.service.login(new LoginCommand("erik", "secret-password", "198.51.100.4"));

        assertThat(result.tokens().refreshToken().value()).isEqualTo("refresh");
        assertThat(account.getLastLoginAt()).isEqualTo(NOW);
        verify(this.refreshTokenService).issue(account, "198.51.100.4");
    }

    /**
     * Verifies that the address of the most recent login is written into the stored part of the
     * account while the address of the registration is left as it was.
     */
    @Test
    void theAddressOfTheLoginIsRecordedApartFromTheRegistration() {
        final UserAccountEntity account = account();
        when(this.userAccountRepository.findByUsername("erik")).thenReturn(Optional.of(account));
        when(this.passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        this.service.login(new LoginCommand("erik", "secret-password", "198.51.100.4"));

        final ArgumentCaptor<UserAccountPayload> written =
                ArgumentCaptor.forClass(UserAccountPayload.class);
        verify(this.userAccountMapper, atLeastOnce()).writePayload(written.capture());
        assertThat(written.getValue().lastLoginIpAddress()).isEqualTo("198.51.100.4");
        assertThat(written.getValue().registrationIpAddress()).isEqualTo("203.0.113.7");
    }

    /**
     * Verifies that a wrong password is refused and that nothing about the account is written, so
     * that a failed attempt leaves no trace of having been close.
     */
    @Test
    void aWrongPasswordIsRefused() {
        final UserAccountEntity account = account();
        when(this.userAccountRepository.findByUsername("erik")).thenReturn(Optional.of(account));
        when(this.passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> this.service.login(
                new LoginCommand("erik", "wrong-password", "198.51.100.4")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(account.getLastLoginAt()).isEqualTo(NOW.minusSeconds(86_400));
        verify(this.refreshTokenService, never()).issue(any(), anyString());
    }

    /**
     * Verifies that a login with a username nobody holds still compares a password, so that the two
     * refusals cost the same and the answer does not say which accounts exist.
     */
    @Test
    void aLoginWithAnUnknownUsernameStillComparesAPassword() {
        when(this.userAccountRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.login(
                new LoginCommand("nobody", "some-password", "198.51.100.4")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(this.passwordEncoder).matches(eq("some-password"), anyString());
    }

    /**
     * Verifies that a password stored under an outdated hash is written again under the current one
     * while the user logs in, so that the strength of the stored hashes follows the configuration.
     */
    @Test
    void anOutdatedHashIsWrittenAgainOnLogin() {
        final UserAccountEntity account = account();
        when(this.userAccountRepository.findByUsername("erik")).thenReturn(Optional.of(account));
        when(this.passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(this.passwordEncoder.upgradeEncoding(account.getPasswordHash())).thenReturn(true);

        this.service.login(new LoginCommand("erik", "secret-password", "198.51.100.4"));

        assertThat(account.getPasswordHash()).isEqualTo("hash:secret-password");
    }

    /**
     * Verifies that a password stored under the current hash is left alone, so that a login does not
     * write a column it has no reason to.
     */
    @Test
    void aCurrentHashIsLeftAlone() {
        final UserAccountEntity account = account();
        when(this.userAccountRepository.findByUsername("erik")).thenReturn(Optional.of(account));
        when(this.passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(this.passwordEncoder.upgradeEncoding(anyString())).thenReturn(false);

        this.service.login(new LoginCommand("erik", "secret-password", "198.51.100.4"));

        assertThat(account.getPasswordHash()).isEqualTo("stored-hash");
    }

    /**
     * Builds a stored account that was last used a day ago.
     *
     * @return the stored account
     */
    private static UserAccountEntity account() {
        return new UserAccountEntity(UUID.randomUUID(), "erik", "stored-hash",
                NOW.minusSeconds(2_592_000), NOW.minusSeconds(86_400), new byte[]{1}, 110);
    }
}
