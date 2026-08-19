package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Exercises the retrieval of a web page against a server that runs inside the test.
 *
 * <h2>Why the Rule Is Stubbed</h2>
 * A server a test can start is reachable only on the loopback address, which is exactly what the real
 * rule refuses. The rule is therefore replaced by one that names two hosts: the server the test runs,
 * and a host that stands for everything the real rule would refuse. What the real rule refuses is
 * stated by {@link PublicAddressResolverTest} instead.
 *
 * <h2>What That Buys</h2>
 * The interesting case is the redirect. The extractor lets the HTTP client follow redirects rather
 * than driving them, and the reason that is safe is that every hop is a connection and therefore
 * passes the resolver. A stubbed rule is what makes that observable: the first hop is allowed, the
 * second is not, and the retrieval has to end as refused.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class WebPageTextExtractorTest {

    /**
     * Host the test server is reached under, resolved to the loopback address by the stubbed rule.
     */
    private static final String SERVER_HOST = "page.test";

    /**
     * Host that stands for an address the rule refuses.
     */
    private static final String REFUSED_HOST = "internal.test";

    /**
     * Body of the page the successful cases retrieve.
     */
    private static final String PAGE = """
            <html><head><title>Thermodynamics</title></head><body>
            <nav>Home Contact</nav>
            <h1>The second law</h1>
            <p>Entropy never decreases in an isolated system.</p>
            <script>console.log('noise');</script>
            </body></html>
            """;

    /**
     * Server the pages are served from.
     */
    private HttpServer server;

    /**
     * Extractor under test, bound to the stubbed rule.
     */
    private WebPageTextExtractor extractor;

    /**
     * Creates the test class.
     */
    WebPageTextExtractorTest() {
    }

    /**
     * Starts the server and builds the extractor.
     *
     * @throws IOException if the server cannot be started
     */
    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/article", exchange -> respond(exchange, 200, "text/html", PAGE));
        this.server.createContext("/empty", exchange ->
                respond(exchange, 200, "text/html", "<html><body></body></html>"));
        this.server.createContext("/document", exchange -> respond(exchange, 200, "application/pdf", "%PDF-1.7"));
        this.server.createContext("/missing", exchange -> respond(exchange, 404, "text/html", "gone"));
        this.server.createContext("/huge", WebPageTextExtractorTest::respondLarge);
        this.server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://" + REFUSED_HOST + "/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        this.server.start();

        this.extractor = new WebPageTextExtractor(new PublicAddressResolver(new WebSourceProperties(List.of())) {
            @Override
            public InetAddress[] resolve(final String host) throws UnknownHostException {
                if (SERVER_HOST.equals(host)) {
                    return new InetAddress[] {InetAddress.getLoopbackAddress()};
                }
                if (REFUSED_HOST.equals(host)) {
                    throw new BlockedAddressException(host, InetAddress.getByName("10.0.0.1"));
                }
                return super.resolve(host);
            }
        });
    }

    /**
     * Stops the server and closes the client of the extractor.
     */
    @AfterEach
    void stopServer() {
        this.extractor.close();
        this.server.stop(0);
    }

    /**
     * Verifies that a page is retrieved, that its title is read, and that the structure around its
     * prose is left out of the text.
     */
    @Test
    void aPageIsRetrievedAndReducedToItsProse() {
        final ExtractedContent content = this.extractor.extract(url("/article"));

        assertThat(content.title()).isEqualTo("Thermodynamics");
        assertThat(content.text()).contains("The second law", "Entropy never decreases");
        assertThat(content.text()).doesNotContain("Home Contact", "console.log");
    }

    /**
     * Verifies that a redirect leading to an address the rule refuses ends the retrieval as refused,
     * which is what makes it safe to let the client follow redirects at all.
     */
    @Test
    void aRedirectIntoARefusedNetworkIsRefused() {
        assertThatFails(url("/redirect"), DocumentFailure.BLOCKED);
    }

    /**
     * Verifies that an address the rule refuses outright is refused before anything is retrieved.
     */
    @Test
    void aRefusedAddressIsNotRetrieved() {
        assertThatFails("http://" + REFUSED_HOST + "/secret", DocumentFailure.BLOCKED);
    }

    /**
     * Verifies that an address of another protocol is refused without a request being attempted.
     */
    @Test
    void anAddressOfAnotherProtocolIsRefused() {
        assertThatFails("file:///etc/passwd", DocumentFailure.BLOCKED);
    }

    /**
     * Verifies that a response which is not a page is refused rather than parsed as one.
     */
    @Test
    void aResponseThatIsNotAPageIsRefused() {
        assertThatFails(url("/document"), DocumentFailure.UNREADABLE);
    }

    /**
     * Verifies that a page without prose is reported as empty rather than stored as a source that
     * holds nothing to answer with.
     */
    @Test
    void aPageWithoutTextIsReportedAsEmpty() {
        assertThatFails(url("/empty"), DocumentFailure.EMPTY);
    }

    /**
     * Verifies that a response which is not a success is reported as unreachable.
     */
    @Test
    void aResponseThatIsNotASuccessIsReportedAsUnreachable() {
        assertThatFails(url("/missing"), DocumentFailure.UNREACHABLE);
    }

    /**
     * Verifies that a response beyond the accepted size is refused rather than truncated, so that a
     * source is never indexed as half of what it is.
     */
    @Test
    void aResponseBeyondTheAcceptedSizeIsRefused() {
        assertThatFails(url("/huge"), DocumentFailure.TOO_LARGE);
    }

    /**
     * Asserts that retrieving an address fails for a given reason.
     *
     * @param address address to retrieve
     * @param cause   reason the retrieval has to fail for
     */
    private void assertThatFails(final String address, final DocumentFailure cause) {
        assertThatExceptionOfType(TextExtractionException.class)
                .isThrownBy(() -> this.extractor.extract(address))
                .extracting(TextExtractionException::failure)
                .isEqualTo(cause);
    }

    /**
     * Builds the address one path of the test server is reached under.
     *
     * @param path path of the resource
     * @return the address, naming the host the stubbed rule resolves
     */
    private String url(final String path) {
        return "http://" + SERVER_HOST + ":" + this.server.getAddress().getPort() + path;
    }

    /**
     * Answers one request with a body.
     *
     * @param exchange    request to answer
     * @param status      status to answer with
     * @param contentType media type to declare
     * @param body        body to write
     * @throws IOException if the response cannot be written
     */
    private static void respond(final HttpExchange exchange,
                                final int status,
                                final String contentType,
                                final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
        exchange.close();
    }

    /**
     * Answers one request with a body beyond the accepted size.
     *
     * <p>The extractor stops reading as soon as the limit is passed, so the write fails part way
     * through. That is the expected end of this exchange rather than a failure of the test.
     *
     * @param exchange request to answer
     * @throws IOException if the response headers cannot be written
     */
    private static void respondLarge(final HttpExchange exchange) throws IOException {
        final byte[] chunk = new byte[64 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'a');
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream stream = exchange.getResponseBody()) {
            for (int written = 0; written < 9 * 1024 * 1024; written += chunk.length) {
                stream.write(chunk);
            }
        } catch (final IOException e) {
            // The extractor closed the connection once the limit was passed, which is what this case
            // asserts. Writing the rest of a body nobody reads is not part of it.
        }
        exchange.close();
    }
}
