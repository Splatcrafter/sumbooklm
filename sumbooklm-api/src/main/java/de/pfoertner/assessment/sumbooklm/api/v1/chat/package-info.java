/**
 * Transport of the conversation held inside a notebook.
 *
 * <h2>Two Endpoints, Two Shapes</h2>
 * Reading a transcript is an ordinary request that answers with a document. Asking a question answers
 * with a stream, because an answer is generated word by word and a reader should not wait for the last
 * one. The two are therefore separate operations rather than one that sometimes streams.
 *
 * <h2>The Model Travels in Headers</h2>
 * Which model answers, and with which key, is presented per request in headers rather than being
 * configured on the server. The application stores neither, which is what makes the key remain the
 * property of the user, and headers are where a credential belongs.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.api.v1.chat;
