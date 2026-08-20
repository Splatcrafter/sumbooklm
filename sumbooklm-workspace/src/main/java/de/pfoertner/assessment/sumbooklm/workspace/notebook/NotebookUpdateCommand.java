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

import org.jspecify.annotations.Nullable;

/**
 * Fields of a notebook a caller wants to change.
 *
 * <h2>Absent Means Unchanged</h2>
 * Both fields are optional and a {@code null} leaves the stored value alone. That is what allows one
 * command to serve renaming and pinning without the caller having to send back the value it does not
 * intend to touch, which it would otherwise have to read first and could overwrite with a stale one.
 *
 * @param title  name to store, or {@code null} to keep the current one
 * @param pinned pin state to store, or {@code null} to keep the current one
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record NotebookUpdateCommand(@Nullable String title, @Nullable Boolean pinned) {
}
