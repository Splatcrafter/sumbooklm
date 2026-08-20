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

package de.pfoertner.assessment.sumbooklm.security.token;

import java.io.Serial;

/**
 * Signals that a presented refresh token is not accepted.
 *
 * <h2>Deliberate Lack of Detail</h2>
 * The exception carries no indication of which check failed. A caller that learns whether a token
 * was unknown, expired, revoked or malformed learns something about the state of the server it has
 * no legitimate use for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class InvalidRefreshTokenException extends RuntimeException {

    /**
     * Serialization version for compatibility with older versions of the class.
     */
    @Serial
    private static final long serialVersionUID = 1888318528776015753L;

    /**
     * Creates the exception.
     */
    public InvalidRefreshTokenException() {
        super("The presented refresh token is not valid");
    }
}
