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
     * Creates the failure.
     *
     * @param host    name that was resolved
     * @param address address it resolved to
     */
    public BlockedAddressException(final String host, final InetAddress address) {
        super("The host " + host + " resolves to " + address.getHostAddress()
                + ", which is inside a network this server must not reach on behalf of a caller");
    }
}
