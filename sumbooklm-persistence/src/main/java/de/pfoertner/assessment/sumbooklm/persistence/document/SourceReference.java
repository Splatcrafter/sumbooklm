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

package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.util.UUID;

/**
 * Identity of one source and of the account it belongs to.
 *
 * <h2>Purpose</h2>
 * The projection is the result shape of {@link SourceDocumentRepository#findAllReferences()}, which
 * is read when the retrieval index has to be rebuilt for every source there is. Reading the rows
 * themselves would pull every uploaded file and every extracted text into the heap at once, while the
 * two identifiers are all that is needed to work through them one at a time.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface SourceReference {

    /**
     * Returns the identifier of the source.
     *
     * @return identifier of the source
     */
    UUID getId();

    /**
     * Returns the identifier of the account the source belongs to.
     *
     * @return identifier of the owning account
     */
    UUID getUserId();
}
