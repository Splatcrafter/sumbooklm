/**
 * Translation of failures into HTTP responses.
 *
 * <h2>Format</h2>
 * Failures are reported as problem details in the sense of RFC 9457, which is the representation
 * Spring produces for framework level errors as well. Clients therefore see one error format
 * regardless of whether a request failed during binding, during validation or inside a service.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.api.error;
