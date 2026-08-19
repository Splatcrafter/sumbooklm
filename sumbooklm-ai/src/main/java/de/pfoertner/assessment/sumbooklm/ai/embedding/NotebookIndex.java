package de.pfoertner.assessment.sumbooklm.ai.embedding;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
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
 * <h2>Reading Is Separated the Same Way</h2>
 * A retriever is never handed out without a notebook either. It is built with a metadata filter on
 * that notebook, so a question asked in one notebook cannot reach a segment of another even though
 * both live in the same store.
 *
 * <h2>Writing Replaces</h2>
 * Indexing a source removes whatever was stored for it before. A source is indexed more than once,
 * on request and whenever the store has to be rebuilt, and appending instead would leave every
 * paragraph in the index as many times as the source was read.
 *
 * <h2>Segments Whose Source Is Gone</h2>
 * The segments of a removed source are removed after the transaction that removed it has committed,
 * so a removal that fails leaves them behind with nothing left to remove them. They are invisible to
 * an answer, because a passage whose source the notebook no longer lists is dropped, but they still
 * occupy memory and still cost a comparison on every search. Collecting them is a pass that keeps
 * only the sources that exist, and it is the one operation here that has to know what all of them are.
 *
 * <h2>Why the Collection Pass Takes a Lock</h2>
 * That pass deletes by what it does not recognise, so a source that was stored between it reading the
 * list and it deleting would be deleted although it exists. Writing therefore holds a shared lock and
 * the pass an exclusive one, which it takes before it reads the list: any segment already in the store
 * was written by a run that finished before the lock was granted, and that run had a committed row
 * behind it, so the list the pass then reads contains it. The cost is that indexing waits for one
 * query, and what it buys is that the pass cannot delete a source it simply had not heard of yet.
 *
 * <h2>The Token Count Is Not a Size</h2>
 * Indexing returns what the embedding model reports as its token usage, and that model reports the
 * same number for every input: four hundred characters and ten thousand characters both come back as
 * one hundred and twenty six. The value is therefore kept because it is what the model said, and it is
 * shown to nobody, because a number that does not vary with what it counts is not a measurement.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class NotebookIndex {

    /**
     * Number of segments one question is answered from. The value bounds how much of a notebook
     * reaches the model, and with segments of about a thousand characters it is a context of a few
     * thousand tokens, which every model the application can be pointed at accepts.
     */
    private static final int MAX_RESULTS = 8;

    /**
     * Similarity a segment has to reach in order to be shown to the model at all, on the scale of the
     * cosine similarity mapped onto zero to one.
     *
     * <p>The value is low because measurement showed that a floor cannot do the job it looks like it
     * does. Against the segments of a German document, this model scores an unrelated question about
     * baking bread at 0.64 and the request to summarise the document itself at 0.62: the two
     * distributions overlap completely, so no threshold separates them. A floor set where it looked
     * safe therefore discarded every passage of exactly the questions a reader asks first, and a
     * question with no passages is the one case where a model invents an answer with confidence.
     *
     * <p>What the floor still does is keep a search from returning segments that point the other way.
     * What decides whether a question is answered from the sources is that the passages are given to
     * the model at all, and that a question without any is not asked.
     */
    private static final double MIN_SCORE = 0.5;

    /**
     * Lock that separates writing segments from collecting the ones whose source is gone. Writing
     * takes it in shared mode, since two runs write different sources and never the same one.
     */
    private final ReadWriteLock collectionLock = new ReentrantReadWriteLock();

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
     * Embeds the segments of one source and stores them under that source and its notebook,
     * replacing whatever was stored for that source before.
     *
     * @param notebookId       identifier of the notebook the segments belong to
     * @param sourceDocumentId identifier of the source the segments were extracted from
     * @param segments         segments to embed, in the order they appear in the source
     * @return number of tokens the model counted for the embedded text, zero for no segments
     */
    public int index(final UUID notebookId, final UUID sourceDocumentId, final List<TextSegment> segments) {
        this.collectionLock.readLock().lock();
        try {
            removeSource(sourceDocumentId);
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
        } finally {
            this.collectionLock.readLock().unlock();
        }
    }

    /**
     * Builds a retriever that reads the segments of one notebook and of no other.
     *
     * @param notebookId identifier of the notebook the retriever may read from
     * @return a retriever answering a query from the segments of that notebook
     */
    public ContentRetriever retrieverFor(final UUID notebookId) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(this.embeddingStore)
                .embeddingModel(this.embeddingModel)
                .maxResults(MAX_RESULTS)
                .minScore(MIN_SCORE)
                .filter(MetadataFilterBuilder.metadataKey(SegmentMetadata.NOTEBOOK_ID).isEqualTo(notebookId))
                .build();
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

    /**
     * Removes every segment that does not belong to one of the sources that exist.
     *
     * <p>The sources are asked for rather than passed in, because the answer has to be obtained while
     * writing is held off; see the note on the lock above. A caller that reads the list itself and
     * hands over the result would be reading it too early, and the pass would have no way to tell.
     *
     * @param existingSources source identifiers that exist, read while writing is held off
     */
    public void collectOrphanedSegments(final Supplier<Collection<UUID>> existingSources) {
        this.collectionLock.writeLock().lock();
        try {
            final Collection<UUID> existing = existingSources.get();
            if (existing.isEmpty()) {
                this.embeddingStore.removeAll();
                return;
            }
            this.embeddingStore.removeAll(MetadataFilterBuilder
                    .metadataKey(SegmentMetadata.SOURCE_DOCUMENT_ID).isNotIn(existing));
        } finally {
            this.collectionLock.writeLock().unlock();
        }
    }
}
