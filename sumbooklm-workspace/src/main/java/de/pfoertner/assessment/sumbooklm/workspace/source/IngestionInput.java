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

import java.util.Objects;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import org.jspecify.annotations.Nullable;

/**
 * Everything the indexing pipeline needs about one source, read once at the start of a run.
 *
 * <h2>Why a Copy</h2>
 * Indexing runs outside a transaction, because it takes seconds and holding a connection open for
 * that long would serve nobody. The values it works on are therefore read in one short transaction
 * and carried out of it, rather than a detached row being kept and read from later.
 *
 * <h2>Text That Is Already Known</h2>
 * A source that has been read successfully before carries the text that reading produced. A run that
 * finds it there does not read the source again, which is what makes rebuilding the index of an
 * uploaded file free of the parser and that of a web page free of the network.
 *
 * @param notebookId    identifier of the notebook the source belongs to
 * @param kind          way the source entered the notebook
 * @param origin        name of the uploaded file or address of the page
 * @param displayName   name the source is currently listed under
 * @param content       bytes of the uploaded file, or {@code null} for a source that names a page
 * @param extractedText text a previous run read out of the source, or {@code null} if there was none
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record IngestionInput(UUID notebookId,
                             SourceKind kind,
                             String origin,
                             String displayName,
                             byte @Nullable [] content,
                             @Nullable String extractedText) {

    /**
     * Creates the input.
     *
     * @param notebookId    identifier of the notebook the source belongs to
     * @param kind          way the source entered the notebook
     * @param origin        name of the uploaded file or address of the page
     * @param displayName   name the source is currently listed under
     * @param content       bytes of the uploaded file, or {@code null} for a source that names a page
     * @param extractedText text a previous run read out of the source, or {@code null} if there was none
     * @throws NullPointerException if any argument other than {@code content} and
     *                              {@code extractedText} is {@code null}
     */
    public IngestionInput {
        Objects.requireNonNull(notebookId, "notebookId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
    }
}
