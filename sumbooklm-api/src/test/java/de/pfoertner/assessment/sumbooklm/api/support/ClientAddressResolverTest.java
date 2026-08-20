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

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises where a request is recorded as coming from.
 *
 * <h2>Why It Never Answers With Nothing</h2>
 * The address is written into the audit metadata of an account and into the row of a session, both
 * of which require a value. A container that reports no address at all therefore has to be answered
 * with a word rather than with absence, or a login from behind an unusual setup would fail on a
 * field nobody logs in for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ClientAddressResolverTest {

    /**
     * Resolver under test.
     */
    private final ClientAddressResolver resolver = new ClientAddressResolver();

    /**
     * Creates the test class.
     */
    ClientAddressResolverTest() {
    }

    /**
     * Verifies that the address the container reports is what gets recorded.
     */
    @Test
    void theAddressOfTheContainerIsRecorded() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        assertThat(this.resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    /**
     * Verifies that a request without an address is recorded as unknown rather than as nothing.
     */
    @Test
    void aRequestWithoutAnAddressIsRecordedAsUnknown() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertThat(this.resolver.resolve(request)).isEqualTo("unknown");
    }

    /**
     * Verifies that an address of whitespace is treated as none, because a container reporting one
     * is saying the same thing in another way.
     */
    @Test
    void anAddressOfWhitespaceIsTreatedAsNone() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("   ");

        assertThat(this.resolver.resolve(request)).isEqualTo("unknown");
    }

    /**
     * Verifies that an address of the sixth version of the protocol is recorded as it is, because it
     * is as much an address as any other and the column is wide enough for it.
     */
    @Test
    void anAddressOfTheSixthVersionIsRecordedAsItIs() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

        assertThat(this.resolver.resolve(request))
                .isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    }
}
