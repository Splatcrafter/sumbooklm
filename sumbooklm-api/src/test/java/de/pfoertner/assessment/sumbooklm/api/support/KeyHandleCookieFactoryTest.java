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

package de.pfoertner.assessment.sumbooklm.api.support;

import java.time.Duration;

import de.pfoertner.assessment.sumbooklm.security.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the cookie a client encrypts its stored session with.
 *
 * <h2>Why the Attributes Are Stated</h2>
 * The cookie carries the salt the browser derives its encryption key from, so what protects it is
 * not what it holds but how it is set: unreadable to scripts, not sent along on requests started
 * elsewhere, and gone when a deployment says a session ended. Those are attributes rather than code,
 * and nothing about them fails to compile when one of them is dropped.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class KeyHandleCookieFactoryTest {

    /**
     * Settings a deployment served over transport security runs under.
     */
    private static final SecurityProperties SECURE = properties(true);

    /**
     * Settings a deployment served without transport security runs under.
     */
    private static final SecurityProperties PLAIN = properties(false);

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    KeyHandleCookieFactoryTest() {
    }

    /**
     * Verifies that the cookie is unreachable from a script, is not sent along on requests started
     * elsewhere, and lives as long as the session it belongs to.
     */
    @Test
    void theCookieIsSetForItsPurpose() {
        final ResponseCookie cookie = new KeyHandleCookieFactory(SECURE).create("the-handle");

        assertThat(cookie.getName()).isEqualTo("sumbooklm_key_handle");
        assertThat(cookie.getValue()).isEqualTo("the-handle");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(90));
    }

    /**
     * Verifies that a deployment served without transport security does not mark the cookie as
     * requiring it, because a browser would then never send it back.
     */
    @Test
    void aPlainDeploymentDoesNotDemandTransportSecurity() {
        assertThat(new KeyHandleCookieFactory(PLAIN).create("the-handle").isSecure()).isFalse();
    }

    /**
     * Verifies that ending a session sets the same cookie to nothing and to no lifetime, so that a
     * browser removes it rather than keeping an empty one.
     */
    @Test
    void endingASessionRemovesTheCookie() {
        final ResponseCookie expired = new KeyHandleCookieFactory(SECURE).expire();

        assertThat(expired.getName()).isEqualTo("sumbooklm_key_handle");
        assertThat(expired.getValue()).isEmpty();
        assertThat(expired.getMaxAge()).isZero();
        assertThat(expired.isHttpOnly()).isTrue();
        assertThat(expired.getPath()).isEqualTo("/");
    }

    /**
     * Verifies that the handle of a request is read back out of the cookie the client sent.
     */
    @Test
    void theHandleOfARequestIsReadBack() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("sumbooklm_key_handle", "the-handle"));

        assertThat(new KeyHandleCookieFactory(SECURE).read(request)).isEqualTo("the-handle");
    }

    /**
     * Verifies that a request without the cookie is answered with nothing, so that a caller can tell
     * a client that has no session from one that has.
     */
    @Test
    void aRequestWithoutTheCookieIsAnsweredWithNothing() {
        assertThat(new KeyHandleCookieFactory(SECURE).read(new MockHttpServletRequest())).isNull();
    }

    /**
     * Verifies that a cookie holding nothing counts as absent, because a browser that was told to
     * remove it may send it back empty first.
     */
    @Test
    void anEmptyCookieCountsAsAbsent() {
        final MockHttpServletRequest empty = new MockHttpServletRequest();
        empty.setCookies(new Cookie("sumbooklm_key_handle", ""));
        final MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie("sumbooklm_key_handle", "   "));

        assertThat(new KeyHandleCookieFactory(SECURE).read(empty)).isNull();
        assertThat(new KeyHandleCookieFactory(SECURE).read(blank)).isNull();
    }

    /**
     * Verifies that a cookie of another name is not read as the handle, so that an unrelated cookie
     * cannot be mistaken for one.
     */
    @Test
    void aCookieOfAnotherNameIsNotTheHandle() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("something_else", "the-handle"));

        assertThat(new KeyHandleCookieFactory(SECURE).read(request)).isNull();
    }

    /**
     * Builds the settings a deployment of a case runs under.
     *
     * @param secure whether the deployment is served over transport security
     * @return the settings
     */
    private static SecurityProperties properties(final boolean secure) {
        return new SecurityProperties(
                new SecurityProperties.Jwt("secret", "sumbooklm",
                        Duration.ofMinutes(5), Duration.ofDays(90)),
                new SecurityProperties.Cookie("secret", "sumbooklm_key_handle", "sumbooklm_auth", secure),
                secure);
    }
}
