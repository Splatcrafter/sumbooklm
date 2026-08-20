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

/**
 * Cryptographic parameters for the client side token store.
 *
 * <h2>Problem</h2>
 * A browser that keeps a token pair across page loads has to store it somewhere the page can read.
 * Anything a page can read, a script injected into that page can read as well. Storing the pair in
 * clear text therefore turns any script injection into a full session takeover.
 *
 * <h2>Approach</h2>
 * The client stores the pair encrypted and never holds the key. What the browser holds is an opaque
 * handle in a cookie that scripts cannot read. The key is derived from that handle and a server side
 * secret, and it is handed out only to a caller whose request carries the handle cookie. Neither the
 * encrypted cookie alone nor a copy of the key alone is enough to recover a token.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.security.cookie;
