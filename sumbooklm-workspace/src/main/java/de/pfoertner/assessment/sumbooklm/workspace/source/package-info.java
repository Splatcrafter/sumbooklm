/**
 * Adding, listing and removing the sources of a notebook, and indexing them for retrieval.
 *
 * <h2>Two Phases</h2>
 * Adding a source and indexing it are separated. The request stores the source and answers; parsing,
 * splitting and embedding happen afterwards on their own thread. A user who uploads a long document
 * therefore waits for a write rather than for a neural network, and the stage the source has reached
 * is what the interface shows in the meantime.
 *
 * <h2>Ownership</h2>
 * As everywhere below a notebook, the account is a parameter of every query rather than a check
 * performed on a loaded row, and a source is only reachable through the notebook it belongs to.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.workspace.source;
