package de.pfoertner.assessment.sumbooklm.api.error;

import de.pfoertner.assessment.sumbooklm.security.authentication.InvalidCredentialsException;
import de.pfoertner.assessment.sumbooklm.security.authentication.UsernameAlreadyTakenException;
import de.pfoertner.assessment.sumbooklm.security.token.InvalidRefreshTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps the failures of the security module onto HTTP responses.
 *
 * <h2>Inherited Behaviour</h2>
 * Extending the framework handler keeps the responses for binding and validation failures, which
 * already produce problem details, and only adds the cases this application introduces.
 *
 * <h2>Wording of Authentication Failures</h2>
 * Both rejected credentials and rejected refresh tokens answer with the same generic detail. A more
 * precise message would tell a caller which half of an attempt was wrong, which is exactly what an
 * attacker enumerating accounts is after.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Creates the handler. The instance is created by the container and holds no state.
     */
    public ApiExceptionHandler() {
    }

    /**
     * Reports a registration that collides with an existing username.
     *
     * @param exception failure raised by the security module
     * @return a problem detail with status {@code 409}
     */
    @ExceptionHandler(UsernameAlreadyTakenException.class)
    public ProblemDetail handleUsernameAlreadyTaken(final UsernameAlreadyTakenException exception) {
        final ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Username already taken");
        return problem;
    }

    /**
     * Reports a login whose credentials were not accepted.
     *
     * @param exception failure raised by the security module
     * @return a problem detail with status {@code 401}
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(final InvalidCredentialsException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "The provided credentials are not valid");
        problem.setTitle("Authentication failed");
        return problem;
    }

    /**
     * Reports a refresh token that was not accepted.
     *
     * @param exception failure raised by the security module
     * @return a problem detail with status {@code 401}
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(final InvalidRefreshTokenException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "The presented refresh token is not valid");
        problem.setTitle("Authentication failed");
        return problem;
    }
}
