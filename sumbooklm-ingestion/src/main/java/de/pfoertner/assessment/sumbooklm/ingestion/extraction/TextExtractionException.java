package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.io.Serial;
import java.util.Objects;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;

/**
 * Signals that a source could not be turned into text.
 *
 * <h2>The Cause Travels With It</h2>
 * The failure carries the cause the source is recorded under, chosen by the extractor that raised it.
 * Only the extractor knows whether an address was refused, unreachable or merely empty, and deriving
 * that later from a message or from the type of an underlying exception would be guesswork about a
 * fact that was known here.
 *
 * <h2>Message Against Cause</h2>
 * The message names hosts and file names, so it belongs in the log and nowhere else. The cause is the
 * part that reaches the user, which is why it is a constant rather than a sentence.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class TextExtractionException extends RuntimeException {

    /**
     * Serialization version of the exception.
     */
    @Serial
    private static final long serialVersionUID = 4820391647250398112L;

    /**
     * Cause the source is recorded under.
     */
    private final DocumentFailure failure;

    /**
     * Creates the failure.
     *
     * @param failure cause the source is recorded under
     * @param message description of what could not be read
     * @throws NullPointerException if {@code failure} is {@code null}
     */
    public TextExtractionException(final DocumentFailure failure, final String message) {
        super(message);
        this.failure = Objects.requireNonNull(failure, "failure must not be null");
    }

    /**
     * Creates the failure with the failure that caused it.
     *
     * @param failure cause the source is recorded under
     * @param message description of what could not be read
     * @param cause   underlying failure
     * @throws NullPointerException if {@code failure} is {@code null}
     */
    public TextExtractionException(final DocumentFailure failure, final String message, final Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure must not be null");
    }

    /**
     * Returns the cause the source is recorded under.
     *
     * @return the cause, never {@link DocumentFailure#NONE}
     */
    public DocumentFailure failure() {
        return this.failure;
    }
}
