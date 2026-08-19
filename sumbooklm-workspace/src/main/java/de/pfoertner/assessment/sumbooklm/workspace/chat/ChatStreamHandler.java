package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.List;

import de.pfoertner.assessment.sumbooklm.ai.chat.AnswerStreamHandler;

/**
 * Receiver of one answer, including the sources it was allowed to draw on.
 *
 * <h2>Order of the Callbacks</h2>
 * The sources are reported once, before the first part of the answer. A client may therefore assume
 * that every citation it renders refers to a source it has already been told about, and does not have
 * to hold text back until the end.
 *
 * <h2>Relation to the Engine</h2>
 * The interface adds the notebook side of a turn to what the engine already reports. The same object
 * is handed down to the engine, so an answer arrives through one receiver rather than being stitched
 * together from two.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface ChatStreamHandler extends AnswerStreamHandler {

    /**
     * Reports the sources the answer may cite.
     *
     * @param sources sources the retrieved passages came from, in the order they are numbered
     */
    void onSources(List<RetrievedSource> sources);
}
