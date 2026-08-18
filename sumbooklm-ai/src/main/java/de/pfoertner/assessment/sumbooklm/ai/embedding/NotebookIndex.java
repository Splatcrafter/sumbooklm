package de.pfoertner.assessment.sumbooklm.ai.embedding;

import java.util.List;
import java.util.UUID;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The retrieval index of all notebooks, written and cleared one source at a time.
 *
 * <h2>Separation Is Enforced Here</h2>
 * There is no way to store a segment without naming the notebook and the source it belongs to,
 * because both are parameters of the only method that writes. Tagging the segments at the call site
 * instead would have made an untagged segment possible, and an untagged segment is one that every
 * notebook of every account can retrieve.
 *
 * <h2>Token Count</h2>
 * The count returned by indexing is what the model itself reported for the text it embedded, not an
 * estimate made next to it. It therefore describes the text that actually entered the index,
 * including the overlap the segments share.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class NotebookIndex {

    /**
     * Model that turns a segment into a vector.
     */
    private final EmbeddingModel embeddingModel;

    /**
     * Store the vectors and their segments are kept in.
     */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Creates the index.
     *
     * @param embeddingModel model that turns a segment into a vector, resolved on first use so that
     *                       the network is not read while the application starts
     * @param embeddingStore store the vectors and their segments are kept in
     */
    public NotebookIndex(@Lazy final EmbeddingModel embeddingModel,
                         final EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * Embeds the segments of one source and stores them under that source and its notebook.
     *
     * @param notebookId       identifier of the notebook the segments belong to
     * @param sourceDocumentId identifier of the source the segments were extracted from
     * @param segments         segments to embed, in the order they appear in the source
     * @return number of tokens the model counted for the embedded text, zero for no segments
     */
    public int index(final UUID notebookId, final UUID sourceDocumentId, final List<TextSegment> segments) {
        if (segments.isEmpty()) {
            return 0;
        }
        for (final TextSegment segment : segments) {
            segment.metadata().put(SegmentMetadata.NOTEBOOK_ID, notebookId);
            segment.metadata().put(SegmentMetadata.SOURCE_DOCUMENT_ID, sourceDocumentId);
        }

        final Response<List<Embedding>> embeddings = this.embeddingModel.embedAll(segments);
        this.embeddingStore.addAll(embeddings.content(), segments);

        final TokenUsage usage = embeddings.tokenUsage();
        if (usage == null || usage.inputTokenCount() == null) {
            return 0;
        }
        return usage.inputTokenCount();
    }

    /**
     * Removes every segment of one source.
     *
     * @param sourceDocumentId identifier of the source whose segments are removed
     */
    public void removeSource(final UUID sourceDocumentId) {
        this.embeddingStore.removeAll(
                MetadataFilterBuilder.metadataKey(SegmentMetadata.SOURCE_DOCUMENT_ID).isEqualTo(sourceDocumentId));
    }

    /**
     * Removes every segment of every source of one notebook.
     *
     * @param notebookId identifier of the notebook whose segments are removed
     */
    public void removeNotebook(final UUID notebookId) {
        this.embeddingStore.removeAll(
                MetadataFilterBuilder.metadataKey(SegmentMetadata.NOTEBOOK_ID).isEqualTo(notebookId));
    }
}
