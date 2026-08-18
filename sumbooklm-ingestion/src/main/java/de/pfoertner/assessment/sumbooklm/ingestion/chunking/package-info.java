/**
 * Cutting extracted text into the segments that are embedded and retrieved.
 *
 * <h2>Why the Cut Matters</h2>
 * A segment is what retrieval returns and what the model later reads as one piece of evidence. Cut
 * too small it loses the context that makes it mean anything; cut too large it dilutes the vector
 * that represents it and drags unrelated sentences into every answer it wins. The sizes are chosen
 * against the window of the embedding model rather than against the length of the source.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.ingestion.chunking;
