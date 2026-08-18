package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.io.Serial;

/**
 * Signals that a source could not be turned into text.
 *
 * <h2>What It Covers</h2>
 * An unreadable file, a format no parser understands, an address that cannot be retrieved and a
 * document that turns out to hold no text at all all end here. The distinction matters to a log but
 * not to the caller, which can only record that this source did not become searchable.
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
     * Creates the failure.
     *
     * @param message description of what could not be read
     */
    public TextExtractionException(final String message) {
        super(message);
    }

    /**
     * Creates the failure with the failure that caused it.
     *
     * @param message description of what could not be read
     * @param cause   underlying failure
     */
    public TextExtractionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
