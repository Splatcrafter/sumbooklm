package de.pfoertner.assessment.sumbooklm.security.cookie;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import de.pfoertner.assessment.sumbooklm.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the derivation of the parameters a client encrypts its stored token pair with.
 *
 * <h2>Approach</h2>
 * The test performs the encryption a browser would perform, with the values the service hands out.
 * That is what makes it meaningful: the parameters are not compared against expected constants, they
 * are used, and a change that breaks the client would break the round trip here first.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class CookieCryptographyServiceTest {

    /**
     * Derivation secret the service under test is configured with.
     */
    private static final String SECRET = "test-only-cookie-derivation-secret-value";

    /**
     * Service under test.
     */
    private final CookieCryptographyService service = new CookieCryptographyService(properties(SECRET));

    /**
     * Creates the test class. The instance is created by JUnit and holds no state beyond the service.
     */
    CookieCryptographyServiceTest() {
    }

    /**
     * Verifies that two issued handles differ, so that two clients never share a key.
     */
    @Test
    void issuedHandlesAreUnique() {
        assertThat(this.service.issueKeyHandle()).isNotEqualTo(this.service.issueKeyHandle());
    }

    /**
     * Verifies that the key of a handle is reproducible, because a client has to decrypt with the
     * same key it encrypted with, and that a different handle yields a different key.
     */
    @Test
    void keysAreBoundToTheHandle() {
        final String handle = this.service.issueKeyHandle();

        assertThat(this.service.parametersFor(handle).key())
                .isEqualTo(this.service.parametersFor(handle).key());
        assertThat(this.service.parametersFor(handle).key())
                .isNotEqualTo(this.service.parametersFor(this.service.issueKeyHandle()).key());
    }

    /**
     * Verifies that a rotated derivation secret invalidates every previously stored client cookie.
     */
    @Test
    void rotatingTheSecretChangesEveryKey() {
        final String handle = this.service.issueKeyHandle();
        final CookieCryptographyService rotated =
                new CookieCryptographyService(properties("a-completely-different-derivation-secret"));

        assertThat(rotated.parametersFor(handle).key())
                .isNotEqualTo(this.service.parametersFor(handle).key());
    }

    /**
     * Verifies that every call hands out a fresh initialization vector, because reusing one with the
     * same key would break the authentication of the cipher.
     */
    @Test
    void everyCallReturnsAFreshInitializationVector() {
        final String handle = this.service.issueKeyHandle();

        assertThat(this.service.parametersFor(handle).initializationVector())
                .isNotEqualTo(this.service.parametersFor(handle).initializationVector());
    }

    /**
     * Verifies that the handed out parameters actually encrypt and decrypt.
     *
     * @throws Exception if the cipher rejects the parameters
     */
    @Test
    void parametersEncryptAndDecryptATokenPair() throws Exception {
        final String handle = this.service.issueKeyHandle();
        final CookieCryptographyParameters parameters = this.service.parametersFor(handle);
        final byte[] plaintext = "{\"accessToken\":\"a\",\"refreshToken\":\"r\"}"
                .getBytes(StandardCharsets.UTF_8);

        final byte[] ciphertext = transform(Cipher.ENCRYPT_MODE, parameters, plaintext);
        final byte[] roundTripped = transform(Cipher.DECRYPT_MODE, parameters, ciphertext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(roundTripped).isEqualTo(plaintext);
    }

    /**
     * Runs the cipher the parameters describe.
     *
     * @param mode       cipher mode to run in
     * @param parameters parameters handed out by the service
     * @param input      data to transform
     * @return the transformed data
     * @throws Exception if the cipher rejects the parameters
     */
    private static byte[] transform(final int mode,
                                    final CookieCryptographyParameters parameters,
                                    final byte[] input) throws Exception {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,
                new SecretKeySpec(Base64.getDecoder().decode(parameters.key()), "AES"),
                new GCMParameterSpec(parameters.authenticationTagLength(),
                        Base64.getDecoder().decode(parameters.initializationVector())));
        return cipher.doFinal(input);
    }

    /**
     * Builds settings carrying a derivation secret.
     *
     * @param secret secret the keys are derived from
     * @return settings for the service under test
     */
    private static SecurityProperties properties(final String secret) {
        return new SecurityProperties(
                new SecurityProperties.Jwt("", "sumbooklm", Duration.ofMinutes(5), Duration.ofDays(90)),
                new SecurityProperties.Cookie(secret, "sumbooklm_key_handle", "sumbooklm_auth", false));
    }
}
