package de.pfoertner.assessment.sumbooklm.ai.embedding;

/**
 * Metadata keys every stored segment carries.
 *
 * <h2>Purpose</h2>
 * The keys are what separates the notebooks inside a shared vector store. A write that used another
 * spelling than a read would produce segments no filter matches, and, worse, a filter that spelled a
 * key wrong would match nothing rather than fail, so both sides name the same constant.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class SegmentMetadata {

    /**
     * Identifier of the notebook a segment belongs to.
     */
    public static final String NOTEBOOK_ID = "notebookId";

    /**
     * Identifier of the source document a segment was extracted from.
     */
    public static final String SOURCE_DOCUMENT_ID = "sourceDocumentId";

    /**
     * Prevents instantiation of this constant holder.
     */
    private SegmentMetadata() {
        throw new AssertionError("SegmentMetadata is a constant holder and must not be instantiated");
    }
}
