package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.UUID;

/**
 * Identity of one source and of the account it belongs to.
 *
 * <h2>Purpose</h2>
 * The projection is the result shape of {@link SourceDocumentRepository#findAllReferences()}, which
 * is read when the retrieval index has to be rebuilt for every source there is. Reading the rows
 * themselves would pull every uploaded file and every extracted text into the heap at once, while the
 * two identifiers are all that is needed to work through them one at a time.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface SourceReference {

    /**
     * Returns the identifier of the source.
     *
     * @return identifier of the source
     */
    UUID getId();

    /**
     * Returns the identifier of the account the source belongs to.
     *
     * @return identifier of the owning account
     */
    UUID getUserId();
}
