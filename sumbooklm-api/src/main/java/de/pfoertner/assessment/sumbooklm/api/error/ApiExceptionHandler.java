package de.pfoertner.assessment.sumbooklm.api.error;

import de.pfoertner.assessment.sumbooklm.ai.chat.UnusableModelSelectionException;
import de.pfoertner.assessment.sumbooklm.ai.summary.SummaryNotWrittenException;
import de.pfoertner.assessment.sumbooklm.security.authentication.InvalidCredentialsException;
import de.pfoertner.assessment.sumbooklm.security.authentication.UsernameAlreadyTakenException;
import de.pfoertner.assessment.sumbooklm.security.token.InvalidRefreshTokenException;
import de.pfoertner.assessment.sumbooklm.workspace.chat.ChatSessionNotFoundException;
import de.pfoertner.assessment.sumbooklm.workspace.chat.QuestionsTooOftenException;
import de.pfoertner.assessment.sumbooklm.workspace.chat.TooManyQuestionsException;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookNotFoundException;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NothingToSummariseException;
import de.pfoertner.assessment.sumbooklm.workspace.source.DuplicateSourceException;
import de.pfoertner.assessment.sumbooklm.workspace.source.EmptyUploadException;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps the failures of the security module onto HTTP responses.
 *
 * <h2>Inherited Behaviour</h2>
 * Extending the framework handler keeps the responses for binding and validation failures, which
 * already produce problem details, and only adds the cases this application introduces. An upload
 * beyond the configured size is among the inherited cases and must not be added here, because a
 * second mapping for one exception type is rejected while the context is being built.
 *
 * <h2>Wording of Missing Resources</h2>
 * A notebook or a source that belongs to another account is reported as missing rather than as
 * forbidden. The distinction would tell a caller that a row with that identifier exists, which is
 * information the caller is not entitled to.
 *
 * <h2>Two Kinds of Too Many</h2>
 * An account can be refused because it is busy with its own answers and because it has asked often
 * enough for a while. Both are {@code 429}, and only the second carries {@code Retry-After}: the first
 * passes as soon as one of the answers arrives, which is sooner than any number this application could
 * name, and the second passes at a moment it knows exactly.
 *
 * <h2>A Provider That Did Not Answer</h2>
 * A summary that the selected provider refused or answered with nothing is reported as {@code 502}.
 * The request was correct and the account is entitled to it; what failed is a server this one asked
 * on the caller's behalf, and that is the status for it.
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

    /**
     * Reports a source the requesting account does not own.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 404}
     */
    @ExceptionHandler(SourceNotFoundException.class)
    public ProblemDetail handleSourceNotFound(final SourceNotFoundException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The requested source does not exist");
        problem.setTitle("Source not found");
        return problem;
    }

    /**
     * Reports a source whose content the notebook already holds.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 409}
     */
    @ExceptionHandler(DuplicateSourceException.class)
    public ProblemDetail handleDuplicateSource(final DuplicateSourceException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "This source is already part of the notebook");
        problem.setTitle("Source already added");
        return problem;
    }

    /**
     * Reports an upload that carries no bytes.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 400}
     */
    @ExceptionHandler(EmptyUploadException.class)
    public ProblemDetail handleEmptyUpload(final EmptyUploadException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The uploaded file carries no content");
        problem.setTitle("Empty upload");
        return problem;
    }

    /**
     * Reports a request that named a model which cannot be addressed with what it presented.
     *
     * <p>The detail of this one is the message of the failure rather than a fixed sentence. The
     * settings are the property of the caller, and only they can correct them, so being told which
     * part is missing is what makes the answer actionable.
     *
     * @param exception failure raised by the AI module
     * @return a problem detail with status {@code 400}
     */
    @ExceptionHandler(UnusableModelSelectionException.class)
    public ProblemDetail handleUnusableModelSelection(final UnusableModelSelectionException exception) {
        final ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Model not usable");
        return problem;
    }

    /**
     * Reports a conversation the requesting account does not hold.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 404}
     */
    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ProblemDetail handleChatSessionNotFound(final ChatSessionNotFoundException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The requested conversation does not exist");
        problem.setTitle("Conversation not found");
        return problem;
    }

    /**
     * Reports an account that already has as many answers in flight as it may have.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 429}
     */
    @ExceptionHandler(TooManyQuestionsException.class)
    public ProblemDetail handleTooManyQuestions(final TooManyQuestionsException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "This account already has as many answers being generated as it may have");
        problem.setTitle("Too many questions at once");
        return problem;
    }

    /**
     * Reports an account that has asked as many questions within the hour as it may ask.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 429} and the time until the account may ask again
     */
    @ExceptionHandler(QuestionsTooOftenException.class)
    public ResponseEntity<ProblemDetail> handleQuestionsTooOften(final QuestionsTooOftenException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "This account has asked as many questions as it may ask within an hour");
        problem.setTitle("Too many questions in an hour");
        final long seconds = Math.max(1, exception.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(problem);
    }

    /**
     * Reports a notebook whose sources cannot be summarised yet.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 409}
     */
    @ExceptionHandler(NothingToSummariseException.class)
    public ProblemDetail handleNothingToSummarise(final NothingToSummariseException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "No source of this notebook has been read yet");
        problem.setTitle("Nothing to summarise");
        return problem;
    }

    /**
     * Reports a summary the selected provider did not produce.
     *
     * @param exception failure raised by the AI module
     * @return a problem detail with status {@code 502}
     */
    @ExceptionHandler(SummaryNotWrittenException.class)
    public ProblemDetail handleSummaryNotWritten(final SummaryNotWrittenException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The selected model did not write a summary");
        problem.setTitle("Summary not written");
        return problem;
    }

    /**
     * Reports a notebook the requesting account does not own.
     *
     * @param exception failure raised by the workspace module
     * @return a problem detail with status {@code 404}
     */
    @ExceptionHandler(NotebookNotFoundException.class)
    public ProblemDetail handleNotebookNotFound(final NotebookNotFoundException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The requested notebook does not exist");
        problem.setTitle("Notebook not found");
        return problem;
    }
}
