package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
 * are only reachable from the server itself. Both the submitted address and the address the request
 * finally landed on are checked against the private ranges, and the content of a page behind such an
 * address is discarded rather than returned.
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
     * Time the retrieval of one page may take before it is given up on.
     */
    private static final int TIMEOUT_MILLIS = 15_000;

    /**
     * Largest response the extractor accepts, so that one address cannot exhaust the heap.
     */
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    /**
     * Identifier the application presents itself with. A page that decides what to serve by client
     * receives a name it can recognise rather than the default of the library.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; SumbookLM/0.1; +https://github.com/sumbooklm)";

    /**
     * Creates the extractor. The instance is created by the container and holds no request state.
     */
    public WebPageTextExtractor() {
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
        requireReachableAddress(address);
        final Document document;
        try {
            final Connection.Response response = Jsoup.connect(address)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MILLIS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .ignoreContentType(false)
                    .execute();
            requireReachableAddress(response.url().toString());
            document = response.parse();
        } catch (final TextExtractionException e) {
            throw e;
        } catch (final Exception e) {
            throw new TextExtractionException("The page at " + address + " cannot be retrieved", e);
        }

        final String text = readableText(document);
        if (text.isEmpty()) {
            throw new TextExtractionException("The page at " + address + " holds no readable text");
        }
        return new ExtractedContent(document.title().strip(), text);
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
     * Rejects an address the server must not retrieve on behalf of a caller.
     *
     * @param address address to check
     * @throws TextExtractionException if the address is malformed, uses another scheme than HTTP or
     *                                 HTTPS, or names a host inside a private range
     */
    private void requireReachableAddress(final String address) {
        final URI uri;
        try {
            uri = new URI(address);
        } catch (final URISyntaxException e) {
            throw new TextExtractionException("The address " + address + " is not a valid address", e);
        }

        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new TextExtractionException("The address " + address + " is not an HTTP address");
        }
        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new TextExtractionException("The address " + address + " names no host");
        }

        final InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (final UnknownHostException e) {
            throw new TextExtractionException("The host of " + address + " cannot be resolved", e);
        }
        for (final InetAddress candidate : resolved) {
            if (candidate.isAnyLocalAddress()
                    || candidate.isLoopbackAddress()
                    || candidate.isLinkLocalAddress()
                    || candidate.isSiteLocalAddress()
                    || candidate.isMulticastAddress()) {
                throw new TextExtractionException("The address " + address + " points into a private network");
            }
        }
    }
}
