package de.pfoertner.assessment.sumbooklm.security.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.pfoertner.assessment.sumbooklm.security.token.TokenClaims;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Builds the beans that authentication is performed with.
 *
 * <h2>Two Verifiers</h2>
 * Access and refresh tokens are signed with the same key but must not be interchangeable. The
 * configuration therefore publishes two verifiers that differ only in the token kind they accept:
 * the primary one accepts access tokens and is what the resource server authenticates requests with,
 * the secondary one accepts refresh tokens and is used where a refresh token is exchanged.
 *
 * <h2>Password Encoding</h2>
 * The encoder is the delegating encoder of Spring Security, which writes the algorithm as a prefix
 * into every hash and picks the verifier from that prefix when reading. Its current default is
 * bcrypt. Because the algorithm travels with the hash, changing it later is a configuration change
 * rather than a data migration.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityBeansConfiguration {

    /**
     * Shortest secret accepted, in characters. HMAC with SHA-256 is specified for keys of at least
     * the digest length, and a shorter secret would silently weaken every issued signature.
     */
    private static final int MINIMUM_SECRET_LENGTH = 32;

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public SecurityBeansConfiguration() {
    }

    /**
     * Provides the encoder that produces and verifies password hashes.
     *
     * @return the delegating password encoder of Spring Security
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Provides the signer of both token kinds.
     *
     * @param properties settings the signing secret is read from
     * @return an encoder signing with HMAC and SHA-256
     */
    @Bean
    public JwtEncoder jwtEncoder(final SecurityProperties properties) {
        return NimbusJwtEncoder.withSecretKey(signingKey(properties))
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Provides the verifier that accepts access tokens.
     *
     * @param properties settings the signing secret and the issuer are read from
     * @return a decoder that rejects anything but a valid, unexpired access token of this issuer
     */
    @Bean
    @Primary
    public JwtDecoder accessTokenDecoder(final SecurityProperties properties) {
        return decoderFor(properties, TokenClaims.ACCESS_TOKEN_TYPE);
    }

    /**
     * Provides the verifier that accepts refresh tokens.
     *
     * @param properties settings the signing secret and the issuer are read from
     * @return a decoder that rejects anything but a valid, unexpired refresh token of this issuer
     */
    @Bean
    public JwtDecoder refreshTokenDecoder(final SecurityProperties properties) {
        return decoderFor(properties, TokenClaims.REFRESH_TOKEN_TYPE);
    }

    /**
     * Builds a verifier restricted to one kind of token.
     *
     * @param properties settings the signing secret and the issuer are read from
     * @param tokenType  value the {@link TokenClaims#TOKEN_TYPE} claim has to carry
     * @return a decoder for the given kind of token
     */
    private static JwtDecoder decoderFor(final SecurityProperties properties, final String tokenType) {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        final OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithValidators(List.of(
                new JwtIssuerValidator(properties.jwt().issuer()),
                new JwtClaimValidator<String>(TokenClaims.TOKEN_TYPE, tokenType::equals)));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Turns the configured signing secret into a key.
     *
     * @param properties settings the signing secret is read from
     * @return the signing key
     * @throws IllegalStateException if the secret is missing or too short
     */
    private static SecretKey signingKey(final SecurityProperties properties) {
        final String secret = properties.jwt().secret();
        if (secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Property 'sumbooklm.security.jwt.secret' must be set to at least "
                            + MINIMUM_SECRET_LENGTH + " characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), MacAlgorithm.HS256.getName());
    }
}
