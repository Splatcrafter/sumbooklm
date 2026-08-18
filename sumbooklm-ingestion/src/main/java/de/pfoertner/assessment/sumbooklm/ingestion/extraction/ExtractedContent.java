package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.util.Objects;

/**
 * Plain text read out of a source, together with the name the source called itself.
 *
 * <h2>Title</h2>
 * The title is what the content itself claims to be called, such as the title element of a web page.
 * It is empty whenever the format carries no such name, and a caller that has a better name already,
 * as an upload does in its file name, is free to ignore it.
 *
 * @param title title the content carries, empty when it carries none
 * @param text  extracted text with its paragraph boundaries preserved
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ExtractedContent(String title, String text) {

    /**
     * Creates the result.
     *
     * @param title title the content carries, empty when it carries none
     * @param text  extracted text with its paragraph boundaries preserved
     * @throws NullPointerException if any argument is {@code null}
     */
    public ExtractedContent {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
