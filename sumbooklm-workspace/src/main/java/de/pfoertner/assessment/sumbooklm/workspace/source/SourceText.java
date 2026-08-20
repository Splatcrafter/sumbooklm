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

/**
 * One readable source of a notebook, with the text it was read as.
 *
 * <h2>Only What Was Read</h2>
 * A source appears here once it has been read and indexed. A source that is still on its way, or one
 * that could not be read, has no text and is therefore not something anything can be derived from.
 *
 * <h2>Why the Whole Text</h2>
 * The text is handed over as it is stored rather than shortened, because how much of it fits depends
 * on what it is being used for and on how many other sources are being used with it. Deciding that
 * here would make one caller's budget the budget of every caller.
 *
 * @param id          identifier of the source, never {@code null}
 * @param displayName name the source is listed under, never {@code null}
 * @param text        text the source was read as, never blank
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record SourceText(UUID id, String displayName, String text) {

    /**
     * Creates the text of one source.
     *
     * @param id          identifier of the source
     * @param displayName name the source is listed under
     * @param text        text the source was read as
     * @throws NullPointerException if any argument is {@code null}
     */
    public SourceText {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
