/**
 * Framework independent domain model of the application.
 *
 * <h2>Scope</h2>
 * Types in this package and its subpackages describe notebooks, sources, chunks and chat
 * interactions purely in terms of the problem domain. They carry no persistence, transport or
 * inference framework annotations, which keeps the model reusable across the persistence,
 * ingestion, AI and API modules.
 *
 * <h2>Dependency Rule</h2>
 * This module is a leaf of the module graph. It must not depend on any other module of the
 * application and must not introduce framework dependencies.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.domain;
