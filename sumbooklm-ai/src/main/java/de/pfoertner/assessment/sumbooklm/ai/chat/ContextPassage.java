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

import java.util.Objects;

/**
 * One retrieved passage as the model is shown it.
 *
 * <h2>Number and Name</h2>
 * The number is what a citation refers to, and it is the position of the passage in the list handed
 * to one request rather than anything stable. The name is shown next to it so that the model cites a
 * document a reader recognises instead of an identifier that means nothing outside the database.
 *
 * @param number      position of the passage in the list of one request, starting at one
 * @param displayName name of the source the passage was taken from
 * @param text        text of the passage
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ContextPassage(int number, String displayName, String text) {

    /**
     * Creates the passage.
     *
     * @param number      position of the passage in the list of one request
     * @param displayName name of the source the passage was taken from
     * @param text        text of the passage
     * @throws NullPointerException     if {@code displayName} or {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code number} is smaller than one
     */
    public ContextPassage {
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (number < 1) {
            throw new IllegalArgumentException("number must be at least one");
        }
    }
}
