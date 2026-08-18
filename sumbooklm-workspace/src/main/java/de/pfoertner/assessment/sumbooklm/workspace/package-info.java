/**
 * Management of the workspaces a user organises their sources in.
 *
 * <h2>Scope</h2>
 * The module owns the lifecycle of a notebook and of everything that hangs below it. It takes
 * commands and returns domain objects, and it knows nothing about HTTP, which keeps the transport
 * layer above it free of rules and this module testable without one.
 *
 * <h2>Ownership Is a Query, Not a Check</h2>
 * Every operation receives the account it is performed for and passes it into the query. A caller
 * that names a notebook of another account gets the same answer as a caller that names a notebook
 * that does not exist, because the row is never loaded in the first place.
 *
 * <h2>Dependency Rule</h2>
 * The module depends on the domain model and on the persistence layer, and on nothing else of the
 * application. It is a sibling of the security module and follows the same shape.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.workspace;
