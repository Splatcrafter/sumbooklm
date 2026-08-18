package de.pfoertner.assessment.sumbooklm.domain.workspace;

/**
 * Stage a source document has reached on its way into the retrieval index.
 *
 * <h2>Progression</h2>
 * A document enters as {@link #UPLOADED}, becomes {@link #INDEXING} while it is parsed, split and
 * embedded, and ends in {@link #READY} or {@link #ERROR}. Only a document in {@link #READY} may be
 * retrieved from, which is what keeps a partially indexed document out of an answer.
 *
 * <h2>Persistence</h2>
 * The constants are persisted by name rather than by ordinal, so that the order of the declarations
 * below carries no meaning for stored data.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum DocumentStatus {

    /**
     * The document is stored but nothing has been extracted from it yet.
     */
    UPLOADED,

    /**
     * The document is being parsed, split into chunks and embedded.
     */
    INDEXING,

    /**
     * The document is indexed and can be retrieved from.
     */
    READY,

    /**
     * Processing the document failed and it cannot be retrieved from.
     */
    ERROR
}
