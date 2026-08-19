package de.pfoertner.assessment.sumbooklm.domain.workspace;

/**
 * Author of one message of a conversation.
 *
 * <h2>Two Authors Only</h2>
 * A conversation carries what the user asked and what the notebook answered. The instructions the
 * model is given are not part of it: they are rebuilt from the sources on every turn, so storing them
 * would freeze a prompt that is meant to change with the pipeline.
 *
 * <h2>Persistence</h2>
 * The constants are persisted by name rather than by ordinal, so that the order of the declarations
 * below carries no meaning for stored data.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum ChatRole {

    /**
     * The message was written by the account holding the conversation.
     */
    USER,

    /**
     * The message was generated from the sources of the notebook.
     */
    ASSISTANT
}
