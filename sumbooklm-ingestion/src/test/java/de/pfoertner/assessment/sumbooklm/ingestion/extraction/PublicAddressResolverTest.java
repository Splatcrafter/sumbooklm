package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Exercises the rule that decides which addresses this server may retrieve for a caller.
 *
 * <h2>Why It Is Tested Alone</h2>
 * The resolver is the single point every outbound connection of the ingestion pipeline passes, so
 * what it refuses is what the pipeline cannot reach. Testing it directly is the only way to state
 * that rule as a list of addresses rather than as the behaviour of a request.
 *
 * <h2>Names That Cannot Resolve</h2>
 * The cases about the permitted hosts use names under {@code .invalid}, which no name server answers
 * for. A name that reaches the resolution step therefore ends as an unknown host, and that is what
 * states the difference the cases are about: refused before resolving, or resolved and then judged.
 *
 * <h2>Literals, Not Names</h2>
 * The addresses below are written as literals, which the platform resolves without asking a name
 * server. The cases therefore describe the rule rather than the state of the network the build runs
 * on, and they need no network at all.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class PublicAddressResolverTest {

    /**
     * Rule under test, as a deployment that permits every public host.
     */
    private final PublicAddressResolver resolver = resolverPermitting();

    /**
     * Creates the test class.
     */
    PublicAddressResolverTest() {
    }

    /**
     * Verifies that the addresses which mean this machine are refused.
     */
    @Test
    void addressesOfThisMachineAreRefused() {
        assertThatBlocked("127.0.0.1");
        assertThatBlocked("localhost");
        assertThatBlocked("0.0.0.0");
        assertThatBlocked("::1");
    }

    /**
     * Verifies that the ranges a private network is built from are refused.
     */
    @Test
    void addressesInsidePrivateRangesAreRefused() {
        assertThatBlocked("10.0.0.1");
        assertThatBlocked("172.16.0.1");
        assertThatBlocked("192.168.1.1");
    }

    /**
     * Verifies that the link local range is refused, which is the range the metadata service of a
     * cloud instance answers on and therefore the address such a guard exists for.
     */
    @Test
    void theLinkLocalRangeIsRefused() {
        assertThatBlocked("169.254.169.254");
        assertThatBlocked("169.254.0.1");
    }

    /**
     * Verifies that multicast addresses are refused.
     */
    @Test
    void multicastAddressesAreRefused() {
        assertThatBlocked("224.0.0.1");
    }

    /**
     * Verifies that a public address is returned as it is, so that the rule refuses rather than
     * rewrites.
     *
     * @throws UnknownHostException if the literal cannot be parsed, which would be a platform failure
     */
    @Test
    void publicAddressesAreReturned() throws UnknownHostException {
        final InetAddress[] resolved = this.resolver.resolve("8.8.8.8");

        assertThat(resolved).hasSize(1);
        assertThat(resolved[0].getHostAddress()).isEqualTo("8.8.8.8");
    }

    /**
     * Verifies that a name which cannot be resolved is reported as unknown rather than as refused,
     * because the two lead to different reasons on the source.
     */
    @Test
    void aNameThatCannotBeResolvedIsNotReportedAsRefused() {
        assertThatExceptionOfType(UnknownHostException.class)
                .isThrownBy(() -> this.resolver.resolve("sumbooklm-no-such-host.invalid"))
                .isNotInstanceOf(BlockedAddressException.class);
    }

    /**
     * Verifies that a deployment which names its hosts refuses every other host, including one whose
     * address nothing is wrong with.
     */
    @Test
    void hostsTheDeploymentDoesNotNameAreRefused() {
        final PublicAddressResolver restricted = resolverPermitting("wiki.example.com");

        assertThatExceptionOfType(BlockedAddressException.class)
                .isThrownBy(() -> restricted.resolve("8.8.8.8"));
    }

    /**
     * Verifies that a named host passes the list, which is visible as the resolution being attempted
     * at all, and that the comparison ignores case.
     */
    @Test
    void aNamedHostReachesTheResolutionStep() {
        final PublicAddressResolver restricted = resolverPermitting("Wiki.Example.INVALID");

        assertThatExceptionOfType(UnknownHostException.class)
                .isThrownBy(() -> restricted.resolve("wiki.example.invalid"))
                .isNotInstanceOf(BlockedAddressException.class);
    }

    /**
     * Verifies that naming a host does not lift the rule about addresses, which is what keeps the list
     * from turning into a way to reach the network of this server.
     */
    @Test
    void aNamedHostIsStillJudgedByItsAddress() {
        final PublicAddressResolver restricted = resolverPermitting("localhost");

        assertThatExceptionOfType(BlockedAddressException.class)
                .isThrownBy(() -> restricted.resolve("localhost"));
    }

    /**
     * Asserts that a host is refused rather than resolved.
     *
     * @param host host to resolve
     */
    private void assertThatBlocked(final String host) {
        assertThatExceptionOfType(BlockedAddressException.class)
                .describedAs("the host %s has to be refused", host)
                .isThrownBy(() -> this.resolver.resolve(host));
    }

    /**
     * Creates a resolver as a deployment that permits the given hosts.
     *
     * @param hosts hosts sources may be retrieved from, none for every public host
     * @return the resolver under that setting
     */
    private static PublicAddressResolver resolverPermitting(final String... hosts) {
        return new PublicAddressResolver(new WebSourceProperties(List.of(hosts)));
    }
}
