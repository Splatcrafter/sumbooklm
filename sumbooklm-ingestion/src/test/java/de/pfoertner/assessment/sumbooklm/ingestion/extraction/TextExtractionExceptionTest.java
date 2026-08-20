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

package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.net.InetAddress;
import java.net.UnknownHostException;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises how a failed extraction states its reason.
 *
 * <h2>Why the Reason Is Required</h2>
 * The pipeline stores what the exception names and shows it to the user. A failure carrying no
 * reason would therefore have to be answered with a guess at the point it is caught, which is
 * exactly where the least is known about it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class TextExtractionExceptionTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    TextExtractionExceptionTest() {
    }

    /**
     * Verifies that a failure carries the reason and the message it was built with.
     */
    @Test
    void theReasonAndTheMessageAreCarried() {
        final TextExtractionException failure =
                new TextExtractionException(DocumentFailure.TOO_LARGE, "The page is too large");

        assertThat(failure.failure()).isEqualTo(DocumentFailure.TOO_LARGE);
        assertThat(failure).hasMessage("The page is too large").hasNoCause();
    }

    /**
     * Verifies that the failure of a library is kept as the cause, so that a log statement can say
     * what actually happened while the user is shown the reason.
     */
    @Test
    void theFailureOfALibraryIsKeptAsTheCause() {
        final Exception cause = new IllegalStateException("tika broke");

        final TextExtractionException failure =
                new TextExtractionException(DocumentFailure.UNREADABLE, "Cannot parse", cause);

        assertThat(failure).hasCause(cause);
        assertThat(failure.failure()).isEqualTo(DocumentFailure.UNREADABLE);
    }

    /**
     * Verifies that a failure without a reason is refused at the point it is built, rather than
     * reaching the pipeline as one that cannot be stored.
     */
    @Test
    void aFailureWithoutAReasonIsRefused() {
        assertThatThrownBy(() -> new TextExtractionException(null, "Something broke"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("failure");
        assertThatThrownBy(() ->
                new TextExtractionException(null, "Something broke", new IllegalStateException()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("failure");
    }

    /**
     * Verifies that every reason can be carried, so that the set of causes the domain declares and
     * the set an extractor may report cannot drift apart.
     *
     * @param cause reason the case is run for
     */
    @ParameterizedTest
    @EnumSource(DocumentFailure.class)
    void everyReasonCanBeCarried(final DocumentFailure cause) {
        assertThat(new TextExtractionException(cause, "message").failure()).isEqualTo(cause);
    }

    /**
     * Verifies that a refused address states which host was refused and, where one was resolved,
     * which address it stood for, because that is what a deployment reads in its log.
     *
     * @throws UnknownHostException if the fixed address of the case cannot be built
     */
    @Test
    void aRefusedAddressNamesWhatWasRefused() throws UnknownHostException {
        final InetAddress internal = InetAddress.getByName("10.0.0.1");

        assertThat(new BlockedAddressException("intranet.test", internal))
                .hasMessageContaining("intranet.test")
                .hasMessageContaining("10.0.0.1");
        assertThat(new BlockedAddressException("intranet.test"))
                .hasMessageContaining("intranet.test")
                .isInstanceOf(UnknownHostException.class);
    }
}
