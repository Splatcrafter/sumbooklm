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

package de.pfoertner.assessment.sumbooklm.api.v1;

import java.util.Set;

import de.pfoertner.assessment.sumbooklm.api.v1.auth.LoginRequest;
import de.pfoertner.assessment.sumbooklm.api.v1.auth.RefreshRequest;
import de.pfoertner.assessment.sumbooklm.api.v1.auth.RegistrationRequest;
import de.pfoertner.assessment.sumbooklm.api.v1.chat.ChatQuestionRequest;
import de.pfoertner.assessment.sumbooklm.api.v1.notebook.NotebookCreationRequest;
import de.pfoertner.assessment.sumbooklm.api.v1.notebook.NotebookSummaryRequest;
import de.pfoertner.assessment.sumbooklm.api.v1.notebook.NotebookUpdateRequest;
import de.pfoertner.assessment.sumbooklm.api.v1.source.WebSourceRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises what the API refuses before a request reaches anything.
 *
 * <h2>Why It Is Tested Without a Server</h2>
 * These rules are annotations rather than code, so nothing about them fails to compile when one is
 * dropped or written to match more than it should. Reaching them through the running application
 * would mean one request per rule, all of which are answered with the same status, and the answer
 * would say nothing about which rule was reached. Reading the rules directly states each of them
 * where it can be seen, including the boundaries: a password of exactly the shortest allowed length,
 * a name of exactly the longest, and the characters a login name may not carry.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class RequestValidationTest {

    /**
     * Source of the reader the rules are checked with.
     */
    private static ValidatorFactory factory;

    /**
     * Reader the rules are checked with.
     */
    private static Validator validator;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    RequestValidationTest() {
    }

    /**
     * Builds the reader the rules are checked with.
     */
    @BeforeAll
    static void buildValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Closes the reader the rules were checked with.
     */
    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    /**
     * Verifies that a registration carrying everything the rules ask for is accepted.
     */
    @Test
    void aCompleteRegistrationIsAccepted() {
        assertThat(violations(new RegistrationRequest(
                "erik", "Erik", "Pfoertner", "a-long-enough-password"))).isEmpty();
    }

    /**
     * Verifies that a login name may carry letters, digits and the three punctuation characters the
     * rule allows, because those are what a name is ordinarily built from.
     *
     * @param username name the case is run for
     */
    @ParameterizedTest
    @ValueSource(strings = {"erik", "Erik_2026", "erik.pfoertner", "erik-p", "abc", "a_b-c.d"})
    void anOrdinaryLoginNameIsAccepted(final String username) {
        assertThat(violations(new RegistrationRequest(
                username, "Erik", "Pfoertner", "a-long-enough-password")))
                .isEmpty();
    }

    /**
     * Verifies that a login name carrying anything else is refused, because the name appears in
     * answers and in log statements and is compared for uniqueness.
     *
     * @param username name the case is run for
     */
    @ParameterizedTest
    @ValueSource(strings = {"erik pfoertner", "erik@example.org", "erik/../admin", "erik<script>",
            "erik\n", "erik;drop", "erik%20", "Pförtner"})
    void aLoginNameOfAnythingElseIsRefused(final String username) {
        assertThat(violations(new RegistrationRequest(
                username, "Erik", "Pfoertner", "a-long-enough-password")))
                .isNotEmpty();
    }

    /**
     * Verifies that a login name shorter than the rule allows is refused and one of exactly the
     * shortest allowed length is accepted, which is the boundary between them.
     */
    @Test
    void theShortestLoginNameIsTheBoundary() {
        assertThat(violations(new RegistrationRequest(
                "ab", "Erik", "Pfoertner", "a-long-enough-password"))).isNotEmpty();
        assertThat(violations(new RegistrationRequest(
                "abc", "Erik", "Pfoertner", "a-long-enough-password"))).isEmpty();
    }

    /**
     * Verifies that a login name longer than the column that holds it is refused, and that one of
     * exactly that length is accepted.
     */
    @Test
    void theLongestLoginNameIsTheBoundary() {
        assertThat(violations(new RegistrationRequest(
                "a".repeat(64), "Erik", "Pfoertner", "a-long-enough-password"))).isEmpty();
        assertThat(violations(new RegistrationRequest(
                "a".repeat(65), "Erik", "Pfoertner", "a-long-enough-password"))).isNotEmpty();
    }

    /**
     * Verifies that a password shorter than the rule allows is refused and one of exactly the
     * shortest allowed length is accepted, because that boundary is the whole rule.
     */
    @Test
    void theShortestPasswordIsTheBoundary() {
        assertThat(violations(new RegistrationRequest(
                "erik", "Erik", "Pfoertner", "a".repeat(11)))).isNotEmpty();
        assertThat(violations(new RegistrationRequest(
                "erik", "Erik", "Pfoertner", "a".repeat(12)))).isEmpty();
    }

    /**
     * Verifies that a password beyond the longest one accepted is refused, so that a caller cannot
     * make the server hash an unbounded amount of text.
     */
    @Test
    void aPasswordBeyondTheLongestOneIsRefused() {
        assertThat(violations(new RegistrationRequest(
                "erik", "Erik", "Pfoertner", "a".repeat(256)))).isEmpty();
        assertThat(violations(new RegistrationRequest(
                "erik", "Erik", "Pfoertner", "a".repeat(257)))).isNotEmpty();
    }

    /**
     * Verifies that a registration without a name is refused, whether the name is missing or made of
     * whitespace.
     *
     * @param name name the case is run for
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aRegistrationWithoutANameIsRefused(final String name) {
        assertThat(violations(new RegistrationRequest(
                "erik", name, "Pfoertner", "a-long-enough-password"))).isNotEmpty();
        assertThat(violations(new RegistrationRequest(
                "erik", "Erik", name, "a-long-enough-password"))).isNotEmpty();
    }

    /**
     * Verifies that a login without a name or without a password is refused before anything is
     * looked up.
     *
     * @param value value the case is run for
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void aLoginWithoutCredentialsIsRefused(final String value) {
        assertThat(violations(new LoginRequest(value, "secret"))).isNotEmpty();
        assertThat(violations(new LoginRequest("erik", value))).isNotEmpty();
    }

    /**
     * Verifies that a login accepts a name that a registration would not, because the rules on a new
     * name must not lock out accounts created under earlier ones.
     */
    @Test
    void aLoginAcceptsWhatARegistrationWouldNot() {
        assertThat(violations(new LoginRequest("erik pfoertner", "secret"))).isEmpty();
    }

    /**
     * Verifies that a refresh without a token is refused.
     *
     * @param token token the case is run for
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aRefreshWithoutATokenIsRefused(final String token) {
        assertThat(violations(new RefreshRequest(token))).isNotEmpty();
    }

    /**
     * Verifies that a notebook cannot be created without a name and not with one longer than the
     * payload is meant to carry.
     */
    @Test
    void aNotebookNeedsAName() {
        assertThat(violations(new NotebookCreationRequest("Thermodynamics"))).isEmpty();
        assertThat(violations(new NotebookCreationRequest("   "))).isNotEmpty();
        assertThat(violations(new NotebookCreationRequest(null))).isNotEmpty();
        assertThat(violations(new NotebookCreationRequest("a".repeat(200)))).isEmpty();
        assertThat(violations(new NotebookCreationRequest("a".repeat(201)))).isNotEmpty();
    }

    /**
     * Verifies that a change may leave both fields out, because an omitted field keeps its stored
     * value and a request that changes nothing is not an invalid one.
     */
    @Test
    void aChangeMayNameNothing() {
        assertThat(violations(new NotebookUpdateRequest(null, null))).isEmpty();
    }

    /**
     * Verifies that a change may not rename a notebook to whitespace, because the name is what the
     * overview lists it under and an empty entry cannot be told from any other.
     */
    @Test
    void aChangeMayNotRenameToNothing() {
        assertThat(violations(new NotebookUpdateRequest("   ", null))).isNotEmpty();
        assertThat(violations(new NotebookUpdateRequest("", null))).isNotEmpty();
        assertThat(violations(new NotebookUpdateRequest("Thermodynamics", true))).isEmpty();
    }

    /**
     * Verifies that a change carries its fields into the command the workspace reads, including the
     * absence of one.
     */
    @Test
    void aChangeIsCarriedIntoTheCommand() {
        assertThat(new NotebookUpdateRequest("Thermodynamics", true).toCommand().title())
                .isEqualTo("Thermodynamics");
        assertThat(new NotebookUpdateRequest("Thermodynamics", true).toCommand().pinned()).isTrue();
        assertThat(new NotebookUpdateRequest(null, null).toCommand().title()).isNull();
        assertThat(new NotebookUpdateRequest(null, null).toCommand().pinned()).isNull();
    }

    /**
     * Verifies that a question is refused when it is empty and when it is longer than a question
     * ever needs to be, so that a caller cannot send a document as a question.
     */
    @Test
    void aQuestionHasToBeAQuestion() {
        assertThat(violations(new ChatQuestionRequest("What is entropy?"))).isEmpty();
        assertThat(violations(new ChatQuestionRequest("   "))).isNotEmpty();
        assertThat(violations(new ChatQuestionRequest(null))).isNotEmpty();
        assertThat(violations(new ChatQuestionRequest("a".repeat(4_000)))).isEmpty();
        assertThat(violations(new ChatQuestionRequest("a".repeat(4_001)))).isNotEmpty();
    }

    /**
     * Verifies that an address is accepted only where it is one this server would retrieve, so that
     * an address of another protocol is refused before anything resolves it.
     *
     * @param url address the case is run for
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://example.org/article", "http://example.org", "https://example.org:8443/a?b=c"})
    void anOrdinaryAddressIsAccepted(final String url) {
        assertThat(violations(new WebSourceRequest(url))).isEmpty();
    }

    /**
     * Verifies that an address of another protocol, or none at all, is refused where the request is
     * read rather than where it would be retrieved.
     *
     * @param url address the case is run for
     */
    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.org", "javascript:alert(1)",
            "example.org", "https://", "  https://example.org", "https://example.org/a b"})
    void anAddressOfAnotherKindIsRefused(final String url) {
        assertThat(violations(new WebSourceRequest(url))).isNotEmpty();
    }

    /**
     * Verifies that an address longer than the column that holds it is refused.
     */
    @Test
    void anAddressBeyondTheLongestOneIsRefused() {
        assertThat(violations(new WebSourceRequest(
                "https://example.org/" + "a".repeat(2_000)))).isNotEmpty();
    }

    /**
     * Verifies that a summary may be asked for in a language, in none, or in a language written with
     * a region, and that anything which is not a language tag is refused.
     */
    @Test
    void aSummaryIsAskedForInALanguageOrInNone() {
        assertThat(violations(new NotebookSummaryRequest("de"))).isEmpty();
        assertThat(violations(new NotebookSummaryRequest("de-DE"))).isEmpty();
        assertThat(violations(new NotebookSummaryRequest("zh-Hans-CN"))).isEmpty();
        assertThat(violations(new NotebookSummaryRequest(""))).isEmpty();
        assertThat(violations(new NotebookSummaryRequest(null))).isEmpty();
        assertThat(violations(new NotebookSummaryRequest("d"))).isNotEmpty();
        assertThat(violations(new NotebookSummaryRequest("de_DE"))).isNotEmpty();
        assertThat(violations(new NotebookSummaryRequest("Deutsch, bitte"))).isNotEmpty();
    }

    /**
     * Verifies that a summary request which names no language is read as naming none rather than as
     * naming nothing, because the engine is handed a string.
     */
    @Test
    void aSummaryRequestWithoutALanguageReadsAsEmpty() {
        assertThat(new NotebookSummaryRequest(null).languageOrEmpty()).isEmpty();
        assertThat(new NotebookSummaryRequest("de").languageOrEmpty()).isEqualTo("de");
    }

    /**
     * Reads the rules one request breaks.
     *
     * @param request request to read the rules of
     * @param <T>     kind of the request
     * @return the rules the request breaks, empty where it breaks none
     */
    private static <T> Set<ConstraintViolation<T>> violations(final T request) {
        return validator.validate(request);
    }
}
