/**
 * HTTP transport layer of the application.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Expose the application use cases as REST endpoints below {@link de.pfoertner.assessment.sumbooklm.api.ApiPaths#BASE}.</li>
 *   <li>Define request and response models that are independent of the domain model.</li>
 *   <li>Publish the OpenAPI specification that the frontend derives its typed client from.</li>
 * </ul>
 *
 * <h2>Contract Ownership</h2>
 * The backend is the single source of truth for the API contract. The specification is generated
 * from the controllers and models of this module and consumed by the frontend build.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.api;
