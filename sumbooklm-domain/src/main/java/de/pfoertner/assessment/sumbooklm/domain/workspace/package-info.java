/**
 * Domain model of the workspaces a user organises their sources in.
 *
 * <h2>Scope</h2>
 * A notebook is the unit a user works in: it collects the sources that answers are grounded in and
 * the conversations held about them. The types here describe that unit and the lifecycle of a source
 * inside it, without saying anything about how either is stored or transported.
 *
 * <h2>Naming</h2>
 * The model calls the unit a notebook. The user interface presents it as a Sumbook, which is a
 * product name rather than a domain concept and therefore stays in the localisation files.
 *
 * <h2>Dependency Rule</h2>
 * As with the rest of the domain module, the types here are plain Java without persistence,
 * transport or security framework annotations.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.domain.workspace;
