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

package de.pfoertner.assessment.sumbooklm.ai.chat;

/**
 * Raised when the model a caller selected cannot be addressed with what they presented.
 *
 * <h2>Message Contents</h2>
 * The message names the part of the selection that is unusable and never the value of a key. It is
 * reported back to the caller, who is the only one able to correct their own settings, so it has to
 * be readable without being an echo of a secret.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class UnusableModelSelectionException extends RuntimeException {

    /**
     * Serialization identifier of this exception class.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of what is missing or not understood about the selection
     */
    public UnusableModelSelectionException(final String message) {
        super(message);
    }
}
