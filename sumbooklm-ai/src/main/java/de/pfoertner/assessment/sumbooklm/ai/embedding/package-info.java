/**
 * Computation of embeddings and the vector store the segments of a notebook live in.
 *
 * <h2>One Store, Many Notebooks</h2>
 * All notebooks of all accounts share a single store. What keeps them apart is metadata: every
 * segment carries the notebook and the source it came from, and every read is filtered on it. The
 * separation is therefore a property of the queries rather than of the storage, which is why the
 * only way into the store is a call that names both identifiers.
 *
 * <h2>Local Model</h2>
 * Embeddings are computed in process, so indexing needs neither a remote endpoint nor an API key and
 * the content of a source never leaves the machine.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.ai.embedding;
