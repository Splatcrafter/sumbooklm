/**
 * The conversation held about the sources of one notebook.
 *
 * <h2>What This Package Decides</h2>
 * Answering a question is three things that belong to different modules: finding the passages that
 * may be used, generating text from them, and keeping what was said. This package is where the three
 * meet, and it is the only place that knows that all of them concern the same notebook.
 *
 * <h2>Two Halves of a Turn</h2>
 * A turn is opened in a transaction that resolves the notebook, stores the question and hands back
 * the conversation so far. Only afterwards is the model asked, on a thread of its own. The question
 * is therefore recorded even when generating the answer fails, which is what lets the user see what
 * they asked instead of a conversation that swallowed it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.workspace.chat;
