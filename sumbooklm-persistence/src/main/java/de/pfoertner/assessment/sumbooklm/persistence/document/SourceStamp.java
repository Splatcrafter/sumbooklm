package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.UUID;

/**
 * What one source contributes to the value the sources of a notebook are recognised by.
 *
 * <h2>Purpose</h2>
 * The projection is the result shape of
 * {@link SourceDocumentRepository#findStampsOfNotebook(UUID, UUID)}, which answers the question of
 * what a notebook currently holds. Three values per source are enough to see that something derived
 * from them no longer describes them, while reading the rows would pull every uploaded file and every
 * extracted text into the heap to compare a few columns.
 *
 * <h2>Why the Length of the Text</h2>
 * The hash of a source is the hash of an uploaded file or of an address, so it does not change when a
 * page is read again and says something else, and it exists before the source has been read at all.
 * The length of the extracted text covers both: it is zero until the source has been read, and it
 * changes with almost every change to a page. It is not a hash of the text and does not claim to be
 * one; what it buys is that a summary written before a source arrived is recognised as one, at the
 * price of a page whose new version happens to have exactly the same length.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface SourceStamp {

    /**
     * Returns the identifier of the source.
     *
     * @return identifier of the source
     */
    UUID getId();

    /**
     * Returns the hash the source is compared by, which is the one of its content or of its address.
     *
     * @return content hash of the source, in lower case hexadecimal
     */
    String getDocumentHash();

    /**
     * Returns the number of characters the source was read as.
     *
     * @return length of the extracted text, zero while the source has not been read
     */
    long getTextLength();
}
