/**
 * Writing one text about a set of sources, rather than answering a question about them.
 *
 * <h2>Why It Is Not the Chat</h2>
 * A question is answered from the passages a retriever selected for it, and the answer cites them. A
 * summary has no question to select by: it is about everything the notebook holds, so the material is
 * the sources themselves and the shortening is a share of the budget per source instead of a
 * similarity. That difference is the whole reason this package exists next to the chat rather than
 * inside it.
 *
 * <h2>One Request, No Stream</h2>
 * A summary is a few sentences and is read after it exists rather than while it is written, so it is
 * requested as a single call that returns the text. Nothing here reports progress, and nothing here
 * can be stopped: what would be gained is the end of a request that is already short.
 *
 * <h2>Unaware of Notebooks</h2>
 * Nothing here knows what a notebook is. The excerpts arrive already selected and already named, so
 * the same engine summarises any set of texts.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.ai.summary;
