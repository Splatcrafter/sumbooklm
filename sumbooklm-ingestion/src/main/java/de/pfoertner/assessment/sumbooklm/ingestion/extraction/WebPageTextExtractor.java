package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the readable text of a web page.
 *
 * <h2>Why Not the Whole Document</h2>
 * A page carries navigation, scripts and footers that repeat on every page of a site. Embedding them
 * would fill the index with text that matches many questions and answers none, so the noise is
 * removed before the text is taken, and the text is then taken from the block elements that carry
 * prose.
 *
 * <h2>Blank Lines Are Kept</h2>
 * Each block becomes its own paragraph, separated by a blank line, instead of the whole page
 * becoming a single line. The splitter that runs later cuts on those boundaries, so flattening the
 * page here would cost the chunks their shape.
 *
 * <h2>Addresses That Point Inwards</h2>
 * The address is retrieved by the server, so a caller could otherwise use it to reach services that
 * are only reachable from the server itself. Every connection this extractor makes resolves its host
 * through {@link PublicAddressResolver} and connects to exactly the addresses that resolver returned,
 * which is what closes the window between checking a name and using it. Redirects need no separate
 * treatment for the same reason: each hop is a connection and therefore passes the same resolver.
 *
 * <h2>Fetching and Parsing Are Separate Libraries</h2>
 * The page is retrieved with the HTTP client and handed to jsoup as bytes. jsoup can retrieve a page
 * itself, but it resolves the host inside the call, which leaves no place to put the rule above.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class WebPageTextExtractor {

    /**
     * Elements that carry prose. Everything else on the page is structure around them.
     */
    private static final String CONTENT_SELECTOR = "h1, h2, h3, h4, h5, h6, p, li, blockquote, pre, dd";

    /**
     * Elements that repeat across the pages of a site and carry no content of their own.
     */
    private static final String NOISE_SELECTOR = "script, style, noscript, nav, header, footer, aside, form, svg";

    /**
     * Time each step of retrieving one page may take before it is given up on.
     */
    private static final Timeout TIMEOUT = Timeout.ofSeconds(15);

    /**
     * Largest response the extractor accepts, so that one address cannot exhaust the heap.
     */
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    /**
     * Size of the buffer the response body is read in.
     */
    private static final int READ_BUFFER_BYTES = 8 * 1024;

    /**
     * Number of redirects one retrieval may follow before it is given up on.
     */
    private static final int MAX_REDIRECTS = 5;

    /**
     * Number of connections the pool holds, which bounds what a burst of sources may occupy.
     */
    private static final int MAX_CONNECTIONS = 8;

    /**
     * Media types the extractor reads. A response of another type is a document rather than a page,
     * and is refused here rather than handed to a parser that would produce characters from bytes.
     */
    private static final Set<String> READABLE_TYPES =
            Set.of("text/html", "application/xhtml+xml", "text/plain");

    /**
     * Identifier the application presents itself with. A page that decides what to serve by client
     * receives a name it can recognise rather than the default of the library.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; SumbookLM/0.1; +https://github.com/sumbooklm)";

    /**
     * Log the shutdown of the client reports to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(WebPageTextExtractor.class);

    /**
     * Client every page is retrieved with, bound to the resolver that judges the addresses.
     */
    private final CloseableHttpClient httpClient;

    /**
     * Creates the extractor.
     *
     * @param addressResolver resolver every connection of this extractor passes through
     */
    public WebPageTextExtractor(final PublicAddressResolver addressResolver) {
        this.httpClient = HttpClients.custom()
                .setUserAgent(USER_AGENT)
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(addressResolver)
                        .setMaxConnTotal(MAX_CONNECTIONS)
                        .setMaxConnPerRoute(MAX_CONNECTIONS)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(TIMEOUT)
                                .setSocketTimeout(TIMEOUT)
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(TIMEOUT)
                        .setResponseTimeout(TIMEOUT)
                        .setRedirectsEnabled(true)
                        .setCircularRedirectsAllowed(false)
                        .setMaxRedirects(MAX_REDIRECTS)
                        .build())
                .build();
    }

    /**
     * Closes the client and the connections it holds when the application shuts down.
     */
    @PreDestroy
    public void close() {
        try {
            this.httpClient.close();
        } catch (final IOException e) {
            LOG.debug("The HTTP client of the web page extractor could not be closed cleanly", e);
        }
    }

    /**
     * Retrieves a web page and reads its readable text.
     *
     * @param address address of the page to retrieve
     * @return the extracted text, with the title of the page when it carries one
     * @throws TextExtractionException if the address is not acceptable, cannot be retrieved, or the
     *                                 page holds no readable text
     */
    public ExtractedContent extract(final String address) {
        final Document document = fetch(requireHttpAddress(address), address);
        final String text = readableText(document);
        if (text.isEmpty()) {
            throw new TextExtractionException(
                    DocumentFailure.EMPTY, "The page at " + address + " holds no readable text");
        }
        return new ExtractedContent(document.title().strip(), text);
    }

    /**
     * Retrieves one page and parses it.
     *
     * @param uri     address to retrieve
     * @param address address as the caller submitted it, used to describe a failure
     * @return the parsed page
     * @throws TextExtractionException if the page cannot be retrieved or is not one that can be read
     */
    private Document fetch(final URI uri, final String address) {
        try {
            return this.httpClient.execute(new HttpGet(uri), response -> parse(response, address));
        } catch (final TextExtractionException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            throw new TextExtractionException(
                    causeOf(e), "The page at " + address + " cannot be retrieved", e);
        }
    }

    /**
     * Turns one response into a parsed page.
     *
     * @param response response the server answered with
     * @param address  address as the caller submitted it, used to describe a failure
     * @return the parsed page
     * @throws IOException             if the body cannot be read
     * @throws TextExtractionException if the response is not a readable page
     */
    private static Document parse(final ClassicHttpResponse response, final String address) throws IOException {
        final int status = response.getCode();
        if (status < 200 || status >= 300) {
            throw new TextExtractionException(DocumentFailure.UNREACHABLE,
                    "The page at " + address + " answered with status " + status);
        }
        final HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new TextExtractionException(
                    DocumentFailure.EMPTY, "The page at " + address + " answered with no content");
        }

        final ContentType contentType = contentTypeOf(entity);
        if (contentType != null
                && !READABLE_TYPES.contains(contentType.getMimeType().toLowerCase(Locale.ROOT))) {
            throw new TextExtractionException(DocumentFailure.UNREADABLE,
                    "The page at " + address + " is served as " + contentType.getMimeType());
        }

        final byte[] body = readBounded(entity.getContent(), address);
        final Charset charset = contentType == null ? null : contentType.getCharset();
        return Jsoup.parse(new ByteArrayInputStream(body),
                charset == null ? null : charset.name(), address);
    }

    /**
     * Reads the declared media type of a response.
     *
     * <p>A response without a usable type is accepted rather than refused, and is parsed as a page.
     * A server that declares nothing is more often serving markup than serving something else, and
     * the parser detects the encoding from the document itself either way.
     *
     * @param entity body of the response
     * @return the declared media type, or {@code null} if none was declared or it is malformed
     */
    private static ContentType contentTypeOf(final HttpEntity entity) {
        try {
            return ContentType.parse(entity.getContentType());
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Reads a response body up to the accepted size.
     *
     * @param stream  body of the response
     * @param address address as the caller submitted it, used to describe a failure
     * @return the bytes of the body
     * @throws IOException             if the body cannot be read
     * @throws TextExtractionException if the body is larger than the accepted size
     */
    private static byte[] readBounded(final InputStream stream, final String address) throws IOException {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final byte[] chunk = new byte[READ_BUFFER_BYTES];
        int read = stream.read(chunk);
        while (read >= 0) {
            if (body.size() + read > MAX_BODY_BYTES) {
                throw new TextExtractionException(DocumentFailure.TOO_LARGE,
                        "The page at " + address + " is larger than " + MAX_BODY_BYTES + " bytes");
            }
            body.write(chunk, 0, read);
            read = stream.read(chunk);
        }
        return body.toByteArray();
    }

    /**
     * Decides what a failed retrieval is recorded as.
     *
     * @param failure failure the retrieval ended with
     * @return {@link DocumentFailure#BLOCKED} if a refused address is anywhere in the chain of
     *         causes, {@link DocumentFailure#UNREACHABLE} otherwise
     */
    private static DocumentFailure causeOf(final Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof BlockedAddressException) {
                return DocumentFailure.BLOCKED;
            }
            if (current == current.getCause()) {
                break;
            }
        }
        return DocumentFailure.UNREACHABLE;
    }

    /**
     * Joins the prose blocks of a page into paragraphs.
     *
     * @param document parsed page to read
     * @return the readable text of the page, empty when it carries none
     */
    private String readableText(final Document document) {
        document.select(NOISE_SELECTOR).remove();
        final Element body = document.body();
        if (body == null) {
            return "";
        }

        final List<String> paragraphs = new ArrayList<>();
        String previous = "";
        for (final Element element : body.select(CONTENT_SELECTOR)) {
            final String paragraph = element.text().strip();
            // A list item nested in another one is matched twice by the selector, once through its
            // own element and once through its parent. Dropping a repeat of the previous paragraph
            // removes that without having to walk the tree by hand.
            if (!paragraph.isEmpty() && !paragraph.equals(previous)) {
                paragraphs.add(paragraph);
                previous = paragraph;
            }
        }
        if (paragraphs.isEmpty()) {
            return body.wholeText().strip();
        }
        return String.join("\n\n", paragraphs);
    }

    /**
     * Rejects an address that is not one this server retrieves at all.
     *
     * <p>Only the shape of the address is judged here. Where it leads is judged by the resolver, at
     * the moment the connection is made, because that is the only judgement a name cannot slip out
     * from under.
     *
     * @param address address to check
     * @return the address as a URI
     * @throws TextExtractionException if the address is malformed, uses another scheme than HTTP or
     *                                 HTTPS, or names no host
     */
    private static URI requireHttpAddress(final String address) {
        final URI uri;
        try {
            uri = new URI(address);
        } catch (final URISyntaxException e) {
            throw new TextExtractionException(DocumentFailure.UNREACHABLE,
                    "The address " + address + " is not a valid address", e);
        }

        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new TextExtractionException(
                    DocumentFailure.BLOCKED, "The address " + address + " is not an HTTP address");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new TextExtractionException(
                    DocumentFailure.BLOCKED, "The address " + address + " names no host");
        }
        return uri;
    }
}
