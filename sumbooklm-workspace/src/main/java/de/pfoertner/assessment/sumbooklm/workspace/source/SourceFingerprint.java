package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Produces the value two sources are compared by.
 *
 * <h2>Files Are Compared by Content</h2>
 * An uploaded file is identified by the hash of its bytes, so the same document uploaded twice under
 * two names is recognised as one document.
 *
 * <h2>Pages Are Compared by Address</h2>
 * A web page is identified by its address rather than by what the address currently returns. The
 * content is not known while the request that adds the source is being answered, and retrieving it
 * first would make the caller wait for a foreign server before learning whether the source was even
 * accepted. Two addresses that serve the same page therefore count as two sources, which is the
 * price of answering immediately.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class SourceFingerprint {

    /**
     * Digest the fingerprints are produced with.
     */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * Prevents instantiation of this utility.
     */
    private SourceFingerprint() {
        throw new AssertionError("SourceFingerprint is a utility and must not be instantiated");
    }

    /**
     * Returns the fingerprint of an uploaded file.
     *
     * @param content bytes of the file as they were uploaded
     * @return the hash of the bytes, in lower case hexadecimal
     */
    public static String ofContent(final byte[] content) {
        return hexDigest(content);
    }

    /**
     * Returns the fingerprint of a web address.
     *
     * @param address address of the page
     * @return the hash of the normalised address, in lower case hexadecimal
     */
    public static String ofAddress(final String address) {
        return hexDigest(normalise(address).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reduces the spellings of one address to a single form.
     *
     * <p>Scheme and host are case insensitive, and a fragment names a position inside a page rather
     * than a page. Ignoring all three keeps two spellings of the same address from entering the same
     * notebook twice. An address that cannot be parsed is compared as it was given, because guessing
     * at its shape would be worse than treating it as its own source.
     *
     * @param address address as the caller submitted it
     * @return the address in the form it is compared in
     */
    private static String normalise(final String address) {
        final String trimmed = address.strip();
        try {
            final URI uri = new URI(trimmed);
            final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            final String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            final String path = uri.getPath() == null ? "" : uri.getPath();
            final String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
            final String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return scheme + "://" + host + port + path + query;
        } catch (final URISyntaxException e) {
            return trimmed;
        }
    }

    /**
     * Hashes bytes.
     *
     * @param value bytes to hash
     * @return the digest of the bytes, in lower case hexadecimal
     */
    private static String hexDigest(final byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(value));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is required but not available", e);
        }
    }
}
