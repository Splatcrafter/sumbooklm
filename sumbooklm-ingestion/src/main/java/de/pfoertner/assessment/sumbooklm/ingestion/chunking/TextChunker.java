package de.pfoertner.assessment.sumbooklm.ingestion.chunking;

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

/**
 * Cuts extracted text into the segments that are embedded.
 *
 * <h2>Paragraphs First</h2>
 * The text is cut on paragraph boundaries and as many paragraphs as fit are kept together. A
 * paragraph that is longer than one segment is cut on sentence boundaries instead, which the
 * splitter does on its own. Cutting on a fixed number of characters would have been simpler and
 * would have ended segments in the middle of sentences.
 *
 * <h2>Sizes</h2>
 * The limits are given in characters. The embedding model degrades beyond roughly two hundred and
 * fifty tokens, and at the four characters per token that ordinary prose averages that is about the
 * segment size below. The overlap repeats the end of a segment at the start of the next one, so a
 * statement that straddles a boundary is still complete in one of the two.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class TextChunker {

    /**
     * Largest segment the splitter produces, in characters.
     */
    private static final int MAX_SEGMENT_CHARS = 1_000;

    /**
     * Number of characters a segment repeats from the end of the segment before it.
     */
    private static final int MAX_OVERLAP_CHARS = 200;

    /**
     * Splitter the segments are produced with. It holds no per-document state.
     */
    private final DocumentSplitter splitter =
            new DocumentByParagraphSplitter(MAX_SEGMENT_CHARS, MAX_OVERLAP_CHARS);

    /**
     * Creates the chunker. The instance is created by the container and holds no request state.
     */
    public TextChunker() {
    }

    /**
     * Cuts text into segments.
     *
     * @param text extracted text with its paragraph boundaries preserved
     * @return the segments, in the order they appear in the text, empty for text without content
     */
    public List<TextSegment> chunk(final String text) {
        if (text.isBlank()) {
            return List.of();
        }
        return this.splitter.split(Document.from(text));
    }
}
