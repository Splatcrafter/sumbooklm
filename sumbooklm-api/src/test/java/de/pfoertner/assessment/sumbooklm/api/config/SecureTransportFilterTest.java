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

package de.pfoertner.assessment.sumbooklm.api.config;

import java.io.IOException;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises what a deployment that demands transport security answers an insecure request with.
 *
 * <h2>Why It Refuses Rather Than Redirects</h2>
 * The request that arrives without transport security has already been sent, credentials and all,
 * so redirecting it would only cause the client to send the same thing again over another channel.
 * The refusal is therefore final, and it has to name what is wrong in a form a client can read,
 * because the client that ran into it is a program rather than a browser.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SecureTransportFilterTest {

    /**
     * Filter under test.
     */
    private final SecureTransportFilter filter = new SecureTransportFilter();

    /**
     * Creates the test class.
     */
    SecureTransportFilterTest() {
    }

    /**
     * Verifies that an insecure request to the API is refused, that nothing behind the filter sees
     * it, and that the refusal is readable as a problem rather than as a page.
     *
     * @throws ServletException if the filter chain of the case fails
     * @throws IOException      if the answer of the case cannot be written
     */
    @Test
    void anInsecureRequestToTheApiIsRefused() throws ServletException, IOException {
        final MockHttpServletRequest request = request("/api/v1/notebooks", false);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        this.filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UPGRADE_REQUIRED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getContentAsString()).contains("Insecure transport").contains("426");
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * Verifies that a request which did arrive over transport security passes through untouched.
     *
     * @throws ServletException if the filter chain of the case fails
     * @throws IOException      if the answer of the case cannot be written
     */
    @Test
    void aSecureRequestPassesThrough() throws ServletException, IOException {
        final MockHttpServletRequest request = request("/api/v1/notebooks", true);
        final MockFilterChain chain = new MockFilterChain();

        this.filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    /**
     * Verifies that everything outside the API is served either way, because the interface itself
     * has to be reachable in order to tell the user what is wrong.
     *
     * @throws ServletException if the filter chain of the case fails
     * @throws IOException      if the answer of the case cannot be written
     */
    @Test
    void whatIsNotTheApiIsServedEitherWay() throws ServletException, IOException {
        final MockHttpServletRequest page = request("/index.html", false);
        final MockFilterChain chain = new MockFilterChain();

        this.filter.doFilter(page, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(page);
    }

    /**
     * Verifies that a path which merely begins with the same letters as the API is not treated as
     * part of it, so that the rule covers what it names and nothing else.
     *
     * @throws ServletException if the filter chain of the case fails
     * @throws IOException      if the answer of the case cannot be written
     */
    @Test
    void aPathThatMerelyLooksLikeTheApiIsNotOne() throws ServletException, IOException {
        final MockHttpServletRequest lookalike = request("/apidocs/index.html", false);
        final MockFilterChain chain = new MockFilterChain();

        this.filter.doFilter(lookalike, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(lookalike);
    }

    /**
     * Builds one request of a case.
     *
     * @param path   path the request names
     * @param secure whether the request arrived over transport security
     * @return the request
     */
    private static MockHttpServletRequest request(final String path, final boolean secure) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.setSecure(secure);
        return request;
    }
}
