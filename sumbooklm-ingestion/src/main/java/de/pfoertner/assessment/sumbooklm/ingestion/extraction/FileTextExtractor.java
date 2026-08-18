package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.stereotype.Component;

/**
 * Reads the text of an uploaded file.
 *
 * <h2>One Parser for Every Format</h2>
 * Apache Tika detects the format from the bytes rather than from the file name, so PDF, Markdown,
 * plain text and everything else it supports enter through the same call. Trusting the extension
 * instead would let a file named {@code .txt} that is in fact a PDF arrive as unreadable characters.
 *
 * <h2>Empty Results Are Failures</h2>
 * A file whose text is empty is rejected rather than indexed. A scanned page without an embedded
 * text layer produces exactly that, and storing it as a finished source would offer the user
 * something to ask questions about that holds nothing to answer with.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class FileTextExtractor {

    /**
     * Parser that detects the format and extracts the body text. It holds no per-file state and is
     * documented as reusable across files, so one instance serves every upload.
     */
    private final ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();

    /**
     * Creates the extractor. The instance is created by the container and holds no request state.
     */
    public FileTextExtractor() {
    }

    /**
     * Reads the text of an uploaded file.
     *
     * @param content  bytes of the file as they were uploaded
     * @param fileName name the file was uploaded under, used to describe a failure
     * @return the extracted text, with no title because a file carries its name outside its content
     * @throws TextExtractionException if the bytes cannot be parsed or hold no text
     */
    public ExtractedContent extract(final byte[] content, final String fileName) {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            final Document document = this.parser.parse(stream);
            final String text = document.text().strip();
            if (text.isEmpty()) {
                throw new TextExtractionException("The file " + fileName + " holds no readable text");
            }
            return new ExtractedContent("", text);
        } catch (final BlankDocumentException e) {
            throw new TextExtractionException("The file " + fileName + " holds no readable text", e);
        } catch (final TextExtractionException e) {
            throw e;
        } catch (final Exception e) {
            throw new TextExtractionException("The file " + fileName + " cannot be parsed", e);
        }
    }
}
