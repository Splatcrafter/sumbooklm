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

package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.io.Serial;
import java.util.UUID;

/**
 * Raised when an account holds no notebook with the requested identifier.
 *
 * <h2>Two Causes, One Failure</h2>
 * The failure is raised both when no notebook with the identifier exists and when one exists but
 * belongs to another account. Distinguishing the two would confirm the existence of a notebook to
 * someone who is not allowed to see it, so the caller receives the same answer either way.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class NotebookNotFoundException extends RuntimeException {

    /**
     * Serialization version of the class.
     */
    @Serial
    private static final long serialVersionUID = 2350504704244333694L;

    /**
     * Creates the failure.
     *
     * @param notebookId identifier that could not be resolved for the requesting account
     */
    public NotebookNotFoundException(final UUID notebookId) {
        super("No notebook with identifier " + notebookId + " belongs to the requesting account");
    }
}
