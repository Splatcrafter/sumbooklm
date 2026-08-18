package de.pfoertner.assessment.sumbooklm.api.config;

import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Decides which requests require an access token and how one is verified.
 *
 * <h2>Reachable Without a Token</h2>
 * Registration, login and the token exchange have to be reachable before a caller holds a token. The
 * endpoint that hands out the cookie encryption parameters joins them, because a client that has
 * just been restarted cannot present a token before it has decrypted its own store; what authorizes
 * that call is the key handle cookie instead. Everything else below the API prefix requires a valid
 * access token.
 *
 * <h2>Everything Outside the API</h2>
 * Paths that do not belong to the API serve the single page application and its assets, and stay
 * open. The application shell is the same document for every visitor and contains no data.
 *
 * <h2>Stateless by Construction</h2>
 * No session is created and no session is read. Authentication state travels in the access token
 * alone, which is also why the cross site request forgery protection is switched off: it defends a
 * cookie based session, and there is none.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    /**
     * Paths of the generated OpenAPI document and of the user interface rendering it.
     */
    private static final String[] OPEN_API_PATHS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public SecurityConfiguration() {
    }

    /**
     * Builds the filter chain of the application.
     *
     * @param http              builder the chain is assembled on
     * @param accessTokenDecoder verifier that accepts access tokens
     * @return the assembled filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(final HttpSecurity http,
                                                      final JwtDecoder accessTokenDecoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST,
                                ApiPaths.V1_REGISTER, ApiPaths.V1_LOGIN, ApiPaths.V1_TOKEN_REFRESH).permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.V1_SECURITY_COOKIE_IV).permitAll()
                        .requestMatchers(OPEN_API_PATHS).permitAll()
                        .requestMatchers(ApiPaths.BASE + "/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder)))
                .build();
    }
}
