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

package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.io.Serial;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Signals that a name resolved to an address this server must not connect to.
 *
 * <h2>Why an Unknown Host</h2>
 * The type extends {@link UnknownHostException} because that is what the resolution contract of the
 * HTTP client allows a resolver to raise, and raising it there is what stops the connection from
 * being made at all. A refusal that arrived later would be a refusal after the request.
 *
 * <h2>Two Reasons, One Type</h2>
 * A name is refused either because of where it leads or because the deployment does not list it. The
 * two reach the user as the same cause, since the difference is about this server rather than about
 * their source, and it is only in the message the operator reads.
 *
 * <h2>Recognised by Type</h2>
 * The client wraps a resolution failure before it reaches the caller, so what distinguishes a refused
 * address from a host that genuinely does not exist is this type somewhere in the chain of causes,
 * rather than the text of a message.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class BlockedAddressException extends UnknownHostException {

    /**
     * Serialization version of the exception.
     */
    @Serial
    private static final long serialVersionUID = 7365174910938204411L;

    /**
     * Creates the failure for a name that leads into a network of this server.
     *
     * @param host    name that was resolved
     * @param address address it resolved to
     */
    public BlockedAddressException(final String host, final InetAddress address) {
        super("The host " + host + " resolves to " + address.getHostAddress()
                + ", which is inside a network this server must not reach on behalf of a caller");
    }

    /**
     * Creates the failure for a name the deployment does not permit sources to be retrieved from.
     *
     * @param host name that was refused
     */
    public BlockedAddressException(final String host) {
        super("The host " + host + " is not among the hosts this deployment permits sources to be "
                + "retrieved from");
    }
}
