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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Determines the network address a request originated from.
 *
 * <h2>Proxies</h2>
 * The resolver reads the address of the connection and never inspects forwarding headers itself. An
 * attacker can set those headers freely, so honouring them is only correct behind a proxy that
 * overwrites them. That decision belongs to the deployment and is expressed by the
 * {@code server.forward-headers-strategy} property, which makes the framework apply the headers to
 * the request before it reaches any application code.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class ClientAddressResolver {

    /**
     * Value recorded when the container reports no address for the connection.
     */
    private static final String UNKNOWN_ADDRESS = "unknown";

    /**
     * Creates the resolver. The instance is created by the container and holds no state.
     */
    public ClientAddressResolver() {
    }

    /**
     * Returns the network address of a request.
     *
     * @param request request to read the address from
     * @return the address of the connection, or {@code unknown} if the container reports none
     */
    public String resolve(final HttpServletRequest request) {
        final String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN_ADDRESS : address;
    }
}
