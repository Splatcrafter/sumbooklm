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

package de.pfoertner.assessment.sumbooklm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
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
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
