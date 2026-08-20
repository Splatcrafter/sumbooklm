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

package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.io.Serial;
import java.util.UUID;

/**
 * Raised when an account holds no source with the requested identifier.
 *
 * <h2>Two Causes, One Failure</h2>
 * As with a notebook, a source that does not exist and a source that belongs to somebody else
 * produce the same failure. Telling the two apart would confirm that a source with that identifier
 * exists to a caller who is not allowed to see it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class SourceNotFoundException extends RuntimeException {

    /**
     * Serialization version of the class.
     */
    @Serial
    private static final long serialVersionUID = 7719302266400584112L;

    /**
     * Creates the failure.
     *
     * @param sourceId identifier that could not be resolved for the requesting account
     */
    public SourceNotFoundException(final UUID sourceId) {
        super("No source with identifier " + sourceId + " belongs to the requesting account");
    }
}
