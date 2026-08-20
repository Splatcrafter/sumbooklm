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

package de.pfoertner.assessment.sumbooklm.ai.summary;

/**
 * Raised when the model a caller selected did not produce a summary.
 *
 * <h2>One Failure for Two Endings</h2>
 * A provider that refuses and a provider that answers with nothing are the same event to the reader:
 * the text they came for does not exist. Both therefore end here, and what the provider said is kept
 * as the cause rather than as the message, because the message is shown to somebody who did not make
 * the request the provider rejected.
 *
 * <h2>Nothing Is Stored</h2>
 * A failed attempt leaves whatever summary the notebook already had. The one that could not be
 * written is not written down as an empty one, so a provider that is briefly unreachable does not
 * cost a reader the text they had.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class SummaryNotWrittenException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for a provider that answered with nothing.
     *
     * @param message description of what the attempt ended with
     */
    public SummaryNotWrittenException(final String message) {
        super(message);
    }

    /**
     * Creates the exception for an attempt that failed.
     *
     * @param message description of what the attempt ended with
     * @param cause   failure the provider or the client reported
     */
    public SummaryNotWrittenException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
