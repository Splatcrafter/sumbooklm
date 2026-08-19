package de.pfoertner.assessment.sumbooklm.ai.embedding;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the pass that removes the segments of sources that no longer exist.
 *
 * <h2>A Model That Computes Nothing</h2>
 * The cases are about which segments the store holds, not about how near they are to a question, so
 * the model below returns the same vector for every segment. That makes one search return everything
 * that passes the filter, which is exactly what counting segments needs, and it keeps a neural network
 * out of a test that has nothing to say about one.
 *
 * <h2>Why the Interleaving Is Forced</h2>
 * The pass removes what it does not recognise, so what it must not do is remove a source that was
 * stored while it was working. Asserting that by starting a writer and hoping it lands in the middle
 * would state nothing on most runs, so the writer is started from inside the pass and the case first
 * asserts that it has not got through.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookIndexTest {

    /**
     * Notebook every segment of these cases belongs to.
     */
    private static final UUID NOTEBOOK_ID = UUID.randomUUID();

    /**
     * Vector the model returns for every segment, and the one every search is performed with.
     */
    private static final float[] VECTOR = {1.0f, 0.0f, 0.0f, 0.0f};

    /**
     * Longest a case waits for the writer it started to be let through.
     */
    private static final long BLOCKED_MILLIS = 200;

    /**
     * Longest a case waits for a thread it started to end once nothing holds it up.
     */
    private static final long JOIN_MILLIS = 5_000;

    /**
     * Store the cases count the segments of.
     */
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    /**
     * Index under test.
     */
    private final NotebookIndex notebookIndex =
            new NotebookIndex(new ConstantEmbeddingModel(), this.embeddingStore);

    /**
     * Creates the test class.
     */
    NotebookIndexTest() {
    }

    /**
     * Verifies that a pass removes the segments of a source it was not told about and keeps the
     * segments of the ones it was.
     */
    @Test
    void segmentsOfSourcesThatAreGoneAreRemoved() {
        final UUID kept = UUID.randomUUID();
        final UUID removed = UUID.randomUUID();
        this.notebookIndex.index(NOTEBOOK_ID, kept, segments());
        this.notebookIndex.index(NOTEBOOK_ID, removed, segments());
        assertThat(segmentsOf(kept)).isEqualTo(2);
        assertThat(segmentsOf(removed)).isEqualTo(2);

        this.notebookIndex.collectOrphanedSegments(() -> List.of(kept));

        assertThat(segmentsOf(kept)).isEqualTo(2);
        assertThat(segmentsOf(removed)).isZero();
    }

    /**
     * Verifies that a pass finding no source at all empties the store, which is the case a filter over
     * an empty list of identifiers cannot express.
     */
    @Test
    void everySegmentIsRemovedWhenNoSourceExists() {
        final UUID sourceId = UUID.randomUUID();
        this.notebookIndex.index(NOTEBOOK_ID, sourceId, segments());

        this.notebookIndex.collectOrphanedSegments(List::of);

        assertThat(segmentsOf(sourceId)).isZero();
    }

    /**
     * Verifies that a source stored while a pass is running is not removed by it, because the writing
     * is held off until the pass is over.
     *
     * @throws InterruptedException if the case is interrupted while it waits for the writer
     */
    @Test
    void aSourceStoredWhileAPassRunsSurvivesIt() throws InterruptedException {
        final UUID kept = UUID.randomUUID();
        final UUID added = UUID.randomUUID();
        this.notebookIndex.index(NOTEBOOK_ID, kept, segments());

        final CountDownLatch written = new CountDownLatch(1);
        final Thread writer = new Thread(() -> {
            this.notebookIndex.index(NOTEBOOK_ID, added, segments());
            written.countDown();
        }, "notebook-index-test-writer");

        this.notebookIndex.collectOrphanedSegments(() -> {
            writer.start();
            assertThat(awaits(written, BLOCKED_MILLIS))
                    .describedAs("a run that writes has to wait for the pass to be over")
                    .isFalse();
            return List.of(kept);
        });

        writer.join(JOIN_MILLIS);
        assertThat(awaits(written, JOIN_MILLIS)).isTrue();
        assertThat(segmentsOf(added))
                .describedAs("a source stored after the pass has read its list is not one it may remove")
                .isEqualTo(2);
        assertThat(segmentsOf(kept)).isEqualTo(2);
    }

    /**
     * Counts the segments the store holds for one source.
     *
     * @param sourceId identifier of the source to count the segments of
     * @return number of segments stored under that source
     */
    private int segmentsOf(final UUID sourceId) {
        return this.embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(new Embedding(VECTOR))
                        .maxResults(Integer.MAX_VALUE)
                        .minScore(0.0)
                        .filter(MetadataFilterBuilder.metadataKey(SegmentMetadata.SOURCE_DOCUMENT_ID)
                                .isEqualTo(sourceId))
                        .build())
                .matches()
                .size();
    }

    /**
     * Waits for a latch and reports whether it was counted down in time.
     *
     * @param latch  latch to wait for
     * @param millis time to wait
     * @return {@code true} if the latch reached zero within that time
     */
    private static boolean awaits(final CountDownLatch latch, final long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Builds the segments of one source, as many as a source needs for a removal to be visible as a
     * count rather than as a presence.
     *
     * @return two segments without metadata, which the index adds
     */
    private static List<TextSegment> segments() {
        return List.of(TextSegment.from("The first paragraph."), TextSegment.from("The second paragraph."));
    }

    /**
     * A model that answers every segment with the same vector.
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    private static final class ConstantEmbeddingModel implements EmbeddingModel {

        /**
         * Creates the model.
         */
        private ConstantEmbeddingModel() {
        }

        /**
         * Returns one vector per segment, all of them the same.
         *
         * @param segments segments to embed
         * @return as many identical vectors as there were segments, without a token count
         */
        @Override
        public Response<List<Embedding>> embedAll(final List<TextSegment> segments) {
            return new Response<>(segments.stream().map(segment -> new Embedding(VECTOR)).toList());
        }
    }
}
