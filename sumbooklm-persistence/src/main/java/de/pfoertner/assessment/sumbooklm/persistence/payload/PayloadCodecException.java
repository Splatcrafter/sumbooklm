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

package de.pfoertner.assessment.sumbooklm.persistence.payload;

import java.io.Serial;

/**
 * Signals that a payload could not be encoded, migrated or decoded.
 *
 * <h2>Meaning</h2>
 * The exception indicates that stored bytes and the expected payload schema do not fit together, or
 * that the migration pipeline rejected the data. It is not recoverable at the call site: the caller
 * cannot repair the payload, only report it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class PayloadCodecException extends RuntimeException {

    /**
     * Serialization version of the exception.
     */
    @Serial
    private static final long serialVersionUID = -6400888191033533811L;

    /**
     * Creates the exception.
     *
     * @param message description of what failed
     */
    public PayloadCodecException(final String message) {
        super(message);
    }

    /**
     * Creates the exception with the failure that caused it.
     *
     * @param message description of what failed
     * @param cause   underlying failure
     */
    public PayloadCodecException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
