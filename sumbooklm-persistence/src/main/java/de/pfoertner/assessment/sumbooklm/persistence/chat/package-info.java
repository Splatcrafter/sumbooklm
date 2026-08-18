/**
 * Storage of the conversations held inside a notebook.
 *
 * <h2>Split of a Session</h2>
 * A session is stored as a row carrying its owner, its notebook and the two timestamps a list of
 * conversations is ordered by, plus a CBOR payload holding what the user sees of it. The messages
 * themselves are not part of this row and join the model together with the chat pipeline.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence.chat;
