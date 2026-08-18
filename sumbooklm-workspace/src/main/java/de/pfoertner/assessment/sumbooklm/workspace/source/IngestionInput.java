package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.util.Objects;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import org.jspecify.annotations.Nullable;

/**
 * Everything the indexing pipeline needs about one source, read once at the start of a run.
 *
 * <h2>Why a Copy</h2>
 * Indexing runs outside a transaction, because it takes seconds and holding a connection open for
 * that long would serve nobody. The values it works on are therefore read in one short transaction
 * and carried out of it, rather than a detached row being kept and read from later.
 *
 * @param notebookId  identifier of the notebook the source belongs to
 * @param kind        way the source entered the notebook
 * @param origin      name of the uploaded file or address of the page
 * @param displayName name the source is currently listed under
 * @param content     bytes of the uploaded file, or {@code null} for a source that names a page
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record IngestionInput(UUID notebookId,
                             SourceKind kind,
                             String origin,
                             String displayName,
                             byte @Nullable [] content) {

    /**
     * Creates the input.
     *
     * @param notebookId  identifier of the notebook the source belongs to
     * @param kind        way the source entered the notebook
     * @param origin      name of the uploaded file or address of the page
     * @param displayName name the source is currently listed under
     * @param content     bytes of the uploaded file, or {@code null} for a source that names a page
     * @throws NullPointerException if any argument other than {@code content} is {@code null}
     */
    public IngestionInput {
        Objects.requireNonNull(notebookId, "notebookId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
    }
}
