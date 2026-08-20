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

package de.pfoertner.assessment.sumbooklm.security.config;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises what a deployment that configures nothing runs under.
 *
 * <h2>Why the Defaults Are Stated</h2>
 * These settings decide how long a stolen access token stays useful, whether the API may be served
 * without transport security and what the cookies of a client are called. A deployment that sets
 * none of them still runs under all of them, so the values a missing configuration produces are part
 * of what the application promises rather than an implementation detail of the binder.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SecurityPropertiesTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SecurityPropertiesTest() {
    }

    /**
     * Verifies that a deployment which configures nothing gets short lived access tokens, refresh
     * tokens that live for a quarter of a year and no secret at all.
     */
    @Test
    void aDeploymentThatConfiguresNothingGetsTheDefaults() {
        final SecurityProperties properties = bind(Map.of());

        assertThat(properties.jwt().accessTokenValidity()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.jwt().refreshTokenValidity()).isEqualTo(Duration.ofDays(90));
        assertThat(properties.jwt().issuer()).isEqualTo("sumbooklm");
        assertThat(properties.jwt().secret()).isEmpty();
    }

    /**
     * Verifies that the cookies of a client are named by default and that they are not marked as
     * requiring transport security, because a deployment served over plain HTTP has to work as well.
     */
    @Test
    void theCookiesAreNamedByDefault() {
        final SecurityProperties properties = bind(Map.of());

        assertThat(properties.cookie().handleName()).isEqualTo("sumbooklm_key_handle");
        assertThat(properties.cookie().payloadName()).isEqualTo("sumbooklm_auth");
        assertThat(properties.cookie().secure()).isFalse();
        assertThat(properties.cookie().secret()).isEmpty();
    }

    /**
     * Verifies that a deployment does not demand transport security unless it says so, which is what
     * lets the application be run behind a proxy that terminates it.
     */
    @Test
    void transportSecurityIsNotDemandedUnlessConfigured() {
        assertThat(bind(Map.of()).requireSecureTransport()).isFalse();
        assertThat(bind(Map.of("sumbooklm.security.require-secure-transport", "true"))
                .requireSecureTransport()).isTrue();
    }

    /**
     * Verifies that a configured value replaces the default while the settings next to it keep
     * theirs, which is what makes a partial configuration usable.
     */
    @Test
    void aConfiguredValueReplacesOnlyItsOwnDefault() {
        final SecurityProperties properties = bind(Map.of(
                "sumbooklm.security.jwt.access-token-validity", "PT30S",
                "sumbooklm.security.cookie.secure", "true"));

        assertThat(properties.jwt().accessTokenValidity()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.jwt().refreshTokenValidity()).isEqualTo(Duration.ofDays(90));
        assertThat(properties.cookie().secure()).isTrue();
        assertThat(properties.cookie().handleName()).isEqualTo("sumbooklm_key_handle");
    }

    /**
     * Binds the settings from what a deployment configured.
     *
     * @param configuration settings the deployment names, which may be none
     * @return the settings the application runs under
     */
    private static SecurityProperties bind(final Map<String, Object> configuration) {
        return new Binder(new MapConfigurationPropertySource(configuration))
                .bindOrCreate("sumbooklm.security", SecurityProperties.class);
    }
}
