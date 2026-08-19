package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.Objects;

/**
 * One retrieved passage as the model is shown it.
 *
 * <h2>Number and Name</h2>
 * The number is what a citation refers to, and it is the position of the passage in the list handed
 * to one request rather than anything stable. The name is shown next to it so that the model cites a
 * document a reader recognises instead of an identifier that means nothing outside the database.
 *
 * @param number      position of the passage in the list of one request, starting at one
 * @param displayName name of the source the passage was taken from
 * @param text        text of the passage
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ContextPassage(int number, String displayName, String text) {

    /**
     * Creates the passage.
     *
     * @param number      position of the passage in the list of one request
     * @param displayName name of the source the passage was taken from
     * @param text        text of the passage
     * @throws NullPointerException     if {@code displayName} or {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code number} is smaller than one
     */
    public ContextPassage {
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (number < 1) {
            throw new IllegalArgumentException("number must be at least one");
        }
    }
}
