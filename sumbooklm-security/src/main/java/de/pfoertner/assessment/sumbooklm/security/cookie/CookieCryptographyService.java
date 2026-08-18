package de.pfoertner.assessment.sumbooklm.security.cookie;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import de.pfoertner.assessment.sumbooklm.security.config.SecurityProperties;
import org.springframework.stereotype.Service;

/**
 * Issues key handles and derives the encryption parameters that belong to them.
 *
 * <h2>Derivation</h2>
 * The key of a handle is derived with HKDF from the configured secret, using the handle as the salt.
 * Nothing about a key is stored: the same handle yields the same key for as long as the secret stays
 * unchanged, and rotating the secret invalidates every stored client cookie at once.
 *
 * <h2>Limits of the Protection</h2>
 * The key is handed out to any caller whose request carries the handle cookie, which the browser
 * attaches automatically. The scheme therefore protects a token pair that is copied out of the
 * cookie store, and it does not protect against code that runs inside the origin and is able to
 * repeat the request.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class CookieCryptographyService {

    /**
     * Key derivation function the client key is derived with.
     */
    private static final String DERIVATION_ALGORITHM = "HKDF-SHA256";

    /**
     * Cipher the derived key is meant for, in the naming the Web Crypto API of a browser uses.
     */
    private static final String CIPHER_ALGORITHM = "AES-GCM";

    /**
     * Length of the derived key in bytes.
     */
    private static final int KEY_LENGTH_BYTES = 32;

    /**
     * Length of an initialization vector in bytes, which is the size the cipher is specified for.
     */
    private static final int INITIALIZATION_VECTOR_LENGTH_BYTES = 12;

    /**
     * Length of the authentication tag in bits.
     */
    private static final int AUTHENTICATION_TAG_LENGTH_BITS = 128;

    /**
     * Length of an issued key handle in bytes.
     */
    private static final int HANDLE_LENGTH_BYTES = 32;

    /**
     * Context string that separates this derivation from any other use of the same secret.
     */
    private static final byte[] DERIVATION_CONTEXT =
            "sumbooklm-cookie-encryption".getBytes(StandardCharsets.UTF_8);

    /**
     * Secret every client key is derived from.
     */
    private final SecretKey derivationSecret;

    /**
     * Name of the cookie the client stores its encrypted token pair in.
     */
    private final String payloadCookieName;

    /**
     * Source of handles and initialization vectors.
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service.
     *
     * @param properties settings the derivation secret is read from
     */
    public CookieCryptographyService(final SecurityProperties properties) {
        this.derivationSecret = new SecretKeySpec(
                properties.cookie().secret().getBytes(StandardCharsets.UTF_8), DERIVATION_ALGORITHM);
        this.payloadCookieName = properties.cookie().payloadName();
    }

    /**
     * Creates a handle for a client that has just authenticated.
     *
     * @return a Base64 URL encoded handle, unique per issued session
     */
    public String issueKeyHandle() {
        final byte[] handle = new byte[HANDLE_LENGTH_BYTES];
        this.random.nextBytes(handle);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(handle);
    }

    /**
     * Derives the parameters belonging to a handle.
     *
     * @param keyHandle handle the calling client presented
     * @return the parameters, carrying the cookie name, a key bound to the handle and a fresh
     *         initialization vector
     */
    public CookieCryptographyParameters parametersFor(final String keyHandle) {
        final byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH_BYTES];
        this.random.nextBytes(initializationVector);

        return new CookieCryptographyParameters(
                this.payloadCookieName,
                CIPHER_ALGORITHM,
                KEY_LENGTH_BYTES * Byte.SIZE,
                INITIALIZATION_VECTOR_LENGTH_BYTES,
                AUTHENTICATION_TAG_LENGTH_BITS,
                Base64.getEncoder().encodeToString(deriveKey(keyHandle).getEncoded()),
                Base64.getEncoder().encodeToString(initializationVector));
    }

    /**
     * Derives the key of a handle.
     *
     * @param keyHandle handle to derive the key for
     * @return the derived key
     * @throws IllegalStateException if the runtime does not provide the derivation function
     */
    private SecretKey deriveKey(final String keyHandle) {
        try {
            final HKDFParameterSpec specification = HKDFParameterSpec.ofExtract()
                    .addIKM(this.derivationSecret)
                    .addSalt(keyHandle.getBytes(StandardCharsets.UTF_8))
                    .thenExpand(DERIVATION_CONTEXT, KEY_LENGTH_BYTES);
            return KDF.getInstance(DERIVATION_ALGORITHM).deriveKey("AES", specification);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(DERIVATION_ALGORITHM + " is required but not available", e);
        }
    }
}
