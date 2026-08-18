package de.pfoertner.assessment.sumbooklm.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an operation that additionally verifies the session of the caller against the database.
 *
 * <h2>Effect</h2>
 * Before the annotated method runs, the session named by the {@code sid} claim of the presented
 * access token is looked up. The call proceeds only if the refresh token of that session still
 * exists and is neither revoked nor expired; otherwise the call is denied.
 *
 * <h2>When to Use It</h2>
 * The annotation belongs on operations where a token that outlives its session would be harmful,
 * such as changing credentials, deleting data or ending a session. It is not a replacement for
 * authentication: the method still has to be reachable only to authenticated callers.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveOperation {
}
