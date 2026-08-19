package de.pfoertner.assessment.sumbooklm.ai.summary;

import java.util.Objects;

/**
 * One source as the model is shown it while a summary is written.
 *
 * <h2>Named, Not Numbered</h2>
 * A passage of an answer carries a number, because the answer cites it. A summary cites nothing: it
 * is one text about all of them, and a number the reader never sees would only be something for the
 * model to put into the prose. The name is kept because it tells the model what the text in front of
 * it is, which is what keeps a list of files from being summarised as one document.
 *
 * @param displayName name the source is listed under
 * @param text        text of the source, possibly only its beginning
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record SourceExcerpt(String displayName, String text) {

    /**
     * Creates the excerpt.
     *
     * @param displayName name the source is listed under
     * @param text        text of the source, possibly only its beginning
     * @throws NullPointerException if any argument is {@code null}
     */
    public SourceExcerpt {
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
