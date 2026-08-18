package de.pfoertner.assessment.sumbooklm.domain.workspace;

/**
 * Way a source document entered a notebook.
 *
 * <h2>Why the Kind Is Kept</h2>
 * The two kinds are acquired differently, are parsed by different components and are presented
 * differently. Keeping the distinction on the source itself is what allows all three to decide
 * without inspecting the origin string and guessing what it is.
 *
 * <h2>Persistence</h2>
 * The constants are persisted by name rather than by ordinal, so that the order of the declarations
 * below carries no meaning for stored data.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum SourceKind {

    /**
     * A file the user uploaded, whose bytes the application stores.
     */
    FILE,

    /**
     * A web page the user named by its address, whose content the application retrieves.
     */
    WEB
}
