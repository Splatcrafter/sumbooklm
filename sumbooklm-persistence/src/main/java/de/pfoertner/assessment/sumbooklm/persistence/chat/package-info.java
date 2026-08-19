/**
 * Storage of the conversations held inside a notebook.
 *
 * <h2>Split of a Session</h2>
 * A session is stored as a row carrying its owner, its notebook and the two timestamps a list of
 * conversations is ordered by, plus a CBOR payload holding what the user sees of it. The transcript
 * is part of that payload rather than of a table of its own, because a message is only ever read
 * together with the conversation it belongs to and never changes once it has been appended.
 *
 * <h2>One Conversation per Notebook</h2>
 * The application starts one session per notebook and appends to it. The table is not restricted to
 * that: it carries an identifier and its own timestamps, so several conversations about one set of
 * sources are a change to the service rather than to the schema.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.persistence.chat;
