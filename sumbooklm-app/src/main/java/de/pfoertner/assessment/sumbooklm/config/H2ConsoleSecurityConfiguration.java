package de.pfoertner.assessment.sumbooklm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Makes the H2 console reachable while the development profile is active.
 *
 * <h2>Why a Separate Chain</h2>
 * The console is a servlet from another project with requirements the application chain
 * deliberately does not meet: it renders itself inside a frame and posts forms without a cross site
 * request forgery token. Relaxing both in the application chain would relax them for every request.
 * A chain of its own, matched to the console path and registered only under the development profile,
 * keeps those exceptions where they belong.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class H2ConsoleSecurityConfiguration {

    /**
     * Path prefix the console is served under, matching {@code spring.h2.console.path}.
     */
    private static final String CONSOLE_PATHS = "/h2-console/**";

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public H2ConsoleSecurityConfiguration() {
    }

    /**
     * Builds the filter chain that serves the H2 console.
     *
     * @param http builder the chain is assembled on
     * @return the assembled filter chain, matched to the console path only
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleSecurityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .securityMatcher(CONSOLE_PATHS)
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
