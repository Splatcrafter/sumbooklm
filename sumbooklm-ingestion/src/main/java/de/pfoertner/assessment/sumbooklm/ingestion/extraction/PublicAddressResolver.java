package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.DnsResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves a host name and refuses every name that leads into a network of this server.
 *
 * <h2>The Single Point Every Connection Passes</h2>
 * The addresses a source is fetched over are the addresses this resolver returned, because the HTTP
 * client connects to exactly what it hands back. Checking a name and then letting the client resolve
 * it again would leave a window in which the answer changes between the two, which is the shape a
 * rebinding attack has. Here there is only one resolution, and it is the one that is judged.
 *
 * <h2>Redirects Are Covered by the Same Rule</h2>
 * A redirect to another host is another connection and therefore another call to this resolver. That
 * is what makes following redirects safe without the application having to drive them: no hop can
 * reach an address the first one was not allowed to reach.
 *
 * <h2>What Counts as Inside</h2>
 * Loopback, link local, site local, the wildcard address and multicast are refused. That covers the
 * ranges a private network is built from and the addresses that mean this machine. It does not cover
 * a public address that a firewall makes reachable only from here, which no resolver can see; see the
 * open questions.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class PublicAddressResolver implements DnsResolver {

    /**
     * Creates the resolver. The instance is created by the container and holds no state.
     */
    public PublicAddressResolver() {
    }

    /**
     * Resolves a name and returns its addresses, provided that none of them points inwards.
     *
     * @param host name to resolve
     * @return every address the name resolves to
     * @throws BlockedAddressException if any of the addresses points into a network of this server
     * @throws UnknownHostException    if the name cannot be resolved at all
     */
    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        final InetAddress[] resolved = InetAddress.getAllByName(host);
        for (final InetAddress address : resolved) {
            if (isInternal(address)) {
                throw new BlockedAddressException(host, address);
            }
        }
        return resolved;
    }

    /**
     * Resolves the canonical name of a host, under the same rule as every other resolution.
     *
     * @param host name to resolve
     * @return the canonical name of the first address the name resolves to
     * @throws BlockedAddressException if any of the addresses points into a network of this server
     * @throws UnknownHostException    if the name cannot be resolved at all
     */
    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
        final InetAddress[] resolved = resolve(host);
        final InetAddress first = resolved[0];
        final String canonical = first.getCanonicalHostName();
        return canonical.equals(first.getHostAddress()) ? host : canonical;
    }

    /**
     * Reports whether an address belongs to a network this server must not reach for a caller.
     *
     * @param address address to judge
     * @return {@code true} if the address is loopback, link local, site local, the wildcard address
     *         or a multicast address
     */
    private static boolean isInternal(final InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }
}
