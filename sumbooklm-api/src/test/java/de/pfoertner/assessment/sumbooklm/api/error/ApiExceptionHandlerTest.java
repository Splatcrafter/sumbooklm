/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.pfoertner.assessment.sumbooklm.api.error;

import java.time.Duration;
import java.util.UUID;

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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises what each failure of the application is answered with.
 *
 * <h2>Why the Wording Is Part of the Test</h2>
 * Two things are decided here that nothing else can check. The first is the status, because a client
 * acts on it: a conflict is retried differently from a refusal. The second is what the answer says,
 * because several of these failures carry the identifier of a notebook, a source or an account in
 * their message, and handing that to a caller who guessed at it would confirm the guess. Where a
 * message is replaced by a fixed sentence, that replacement is the point.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ApiExceptionHandlerTest {

    /**
     * Handler under test.
     */
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    /**
     * Creates the test class.
     */
    ApiExceptionHandlerTest() {
    }

    /**
     * Verifies that a username somebody already holds is answered as a conflict, and that the answer
     * repeats the name, which the caller sent itself.
     */
    @Test
    void aTakenUsernameIsAConflict() {
        final ProblemDetail problem =
                this.handler.handleUsernameAlreadyTaken(new UsernameAlreadyTakenException("erik"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Username already taken");
        assertThat(problem.getDetail()).contains("erik");
    }

    /**
     * Verifies that credentials which do not match are answered as a refusal that says nothing about
     * which half of them was wrong.
     */
    @Test
    void wrongCredentialsSayNothingFurther() {
        final ProblemDetail problem =
                this.handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getTitle()).isEqualTo("Authentication failed");
        assertThat(problem.getDetail()).isEqualTo("The provided credentials are not valid");
    }

    /**
     * Verifies that a refresh token that is not valid is answered the same way as wrong credentials,
     * because saying more would tell a caller which of its guesses was closer.
     */
    @Test
    void anInvalidRefreshTokenSaysNothingFurther() {
        final ProblemDetail problem =
                this.handler.handleInvalidRefreshToken(new InvalidRefreshTokenException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getTitle()).isEqualTo("Authentication failed");
    }

    /**
     * Verifies that a notebook that does not exist and one that belongs to somebody else are both
     * answered as absent, and that the identifier is not repeated back.
     */
    @Test
    void aNotebookOutOfReachIsSimplyAbsent() {
        final UUID notebookId = UUID.randomUUID();

        final ProblemDetail problem =
                this.handler.handleNotebookNotFound(new NotebookNotFoundException(notebookId));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Notebook not found");
        assertThat(problem.getDetail()).doesNotContain(notebookId.toString());
    }

    /**
     * Verifies that a source out of reach is answered as absent without repeating its identifier.
     */
    @Test
    void aSourceOutOfReachIsSimplyAbsent() {
        final UUID sourceId = UUID.randomUUID();

        final ProblemDetail problem =
                this.handler.handleSourceNotFound(new SourceNotFoundException(sourceId));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).doesNotContain(sourceId.toString());
    }

    /**
     * Verifies that a conversation out of reach is answered as absent without repeating its
     * identifier.
     */
    @Test
    void aConversationOutOfReachIsSimplyAbsent() {
        final UUID sessionId = UUID.randomUUID();

        final ProblemDetail problem =
                this.handler.handleChatSessionNotFound(new ChatSessionNotFoundException(sessionId));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Conversation not found");
        assertThat(problem.getDetail()).doesNotContain(sessionId.toString());
    }

    /**
     * Verifies that a source the notebook already holds is answered as a conflict, which is what
     * tells a client to stop rather than to retry.
     */
    @Test
    void aSourceThatIsAlreadyThereIsAConflict() {
        final ProblemDetail problem =
                this.handler.handleDuplicateSource(new DuplicateSourceException(UUID.randomUUID()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Source already added");
    }

    /**
     * Verifies that an upload without content is answered as a bad request, because the caller sent
     * something it can fix.
     */
    @Test
    void anEmptyUploadIsABadRequest() {
        final ProblemDetail problem =
                this.handler.handleEmptyUpload(new EmptyUploadException("empty.txt"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Empty upload");
    }

    /**
     * Verifies that a model that cannot be used is answered as a bad request that repeats what was
     * wrong, because the selection came from the caller and only the caller can fix it.
     */
    @Test
    void anUnusableModelIsABadRequestThatSaysWhy() {
        final ProblemDetail problem = this.handler.handleUnusableModelSelection(
                new UnusableModelSelectionException("Provider OPENAI requires an API key"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Model not usable");
        assertThat(problem.getDetail()).isEqualTo("Provider OPENAI requires an API key");
    }

    /**
     * Verifies that an account with as many answers as it may have is answered as being over a
     * limit, so that a client waits rather than retrying at once.
     */
    @Test
    void tooManyAnswersAtOnceIsALimit() {
        final ProblemDetail problem =
                this.handler.handleTooManyQuestions(new TooManyQuestionsException(UUID.randomUUID()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(problem.getTitle()).isEqualTo("Too many questions at once");
    }

    /**
     * Verifies that an account which asked too often is told how long to wait, in a header a client
     * reads without parsing the answer.
     */
    @Test
    void askingTooOftenIsAnsweredWithHowLongToWait() {
        final ResponseEntity<ProblemDetail> answer = this.handler.handleQuestionsTooOften(
                new QuestionsTooOftenException(UUID.randomUUID(), Duration.ofSeconds(90)));

        assertThat(answer.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(answer.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("90");
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().getTitle()).isEqualTo("Too many questions in an hour");
    }

    /**
     * Verifies that a wait of nothing is still answered as a wait of one second, because a client
     * told to wait for zero would retry at once and be refused again.
     */
    @Test
    void aWaitOfNothingIsStillAWait() {
        final ResponseEntity<ProblemDetail> answer = this.handler.handleQuestionsTooOften(
                new QuestionsTooOftenException(UUID.randomUUID(), Duration.ZERO));

        assertThat(answer.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    /**
     * Verifies that a wait shorter than a second is rounded up rather than down, for the same
     * reason.
     */
    @Test
    void aWaitShorterThanASecondIsRoundedUp() {
        final ResponseEntity<ProblemDetail> answer = this.handler.handleQuestionsTooOften(
                new QuestionsTooOftenException(UUID.randomUUID(), Duration.ofMillis(400)));

        assertThat(answer.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    /**
     * Verifies that a notebook with nothing to summarise is answered as a conflict, because the
     * request is sound and the state of the notebook is not.
     */
    @Test
    void nothingToSummariseIsAConflict() {
        final UUID notebookId = UUID.randomUUID();

        final ProblemDetail problem =
                this.handler.handleNothingToSummarise(new NothingToSummariseException(notebookId));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Nothing to summarise");
        assertThat(problem.getDetail()).doesNotContain(notebookId.toString());
    }

    /**
     * Verifies that a model which wrote no summary is answered as a failure of something behind this
     * server, because the request was sound and the provider is not this application.
     */
    @Test
    void aModelThatWroteNothingIsAFailureBehindTheServer() {
        final ProblemDetail problem = this.handler.handleSummaryNotWritten(
                new SummaryNotWrittenException("The model did not write a summary",
                        new IllegalStateException("connection refused to https://api.openai.com")));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(problem.getTitle()).isEqualTo("Summary not written");
        assertThat(problem.getDetail()).doesNotContain("api.openai.com");
    }
}
