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

package de.pfoertner.assessment.sumbooklm.domain.workspace;

/**
 * Reason a source document could not be indexed.
 *
 * <h2>Causes, Not Messages</h2>
 * The constants are a closed set rather than a text field. What a parser or an HTTP client says when
 * it fails names hosts, file paths and internals of the library, none of which may be handed to a
 * user; and a reason is only worth showing at all if it tells them what to do differently. Each
 * constant below stands for a different next step, which is what decides where the line between two
 * of them runs.
 *
 * <h2>Persistence</h2>
 * The constants are persisted by name rather than by ordinal, so that the order of the declarations
 * below carries no meaning for stored data.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum DocumentFailure {

    /**
     * Nothing failed. This is what a source carries in every stage other than
     * {@link DocumentStatus#ERROR}.
     */
    NONE,

    /**
     * The address is one this server may not retrieve on behalf of a caller, because it uses another
     * protocol than HTTP or because it names a host inside a private network.
     */
    BLOCKED,

    /**
     * The address could not be reached, or answered with something other than success.
     */
    UNREACHABLE,

    /**
     * The content was received but could not be turned into text, because the format is not one the
     * parser handles or because the document is damaged.
     */
    UNREADABLE,

    /**
     * The content was read and holds no text, which is what an image only document or an empty page
     * amounts to.
     */
    EMPTY,

    /**
     * The content is larger than the size the application accepts.
     */
    TOO_LARGE,

    /**
     * Something failed that none of the other constants describes.
     */
    UNEXPECTED
}
