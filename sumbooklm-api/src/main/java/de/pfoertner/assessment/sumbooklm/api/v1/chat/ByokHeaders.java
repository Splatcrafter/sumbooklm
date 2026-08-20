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

package de.pfoertner.assessment.sumbooklm.api.v1.chat;

/**
 * Names of the headers a caller presents their own model access in.
 *
 * <h2>Why Headers</h2>
 * The values describe how the request is to be carried out rather than what is being asked, and one
 * of them is a credential. Putting a key into the body would place it in a request that is otherwise
 * a question, and into every log line that records a body.
 *
 * <h2>Not Stored</h2>
 * None of the values is persisted. They are read for one request, turned into a client, and forgotten
 * with the response.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ByokHeaders {

    /**
     * Name of the service the model is requested from.
     */
    public static final String PROVIDER = "X-AI-Provider";

    /**
     * Key the service is addressed with, absent for a service that needs none.
     */
    public static final String API_KEY = "X-AI-Api-Key";

    /**
     * Name the service knows the model under.
     */
    public static final String MODEL = "X-AI-Model";

    /**
     * Address the service is reached at, absent for the default address of the service.
     */
    public static final String BASE_URL = "X-AI-Base-Url";

    /**
     * Prevents instantiation of this constant holder.
     */
    private ByokHeaders() {
        throw new AssertionError("ByokHeaders is a constant holder and must not be instantiated");
    }
}
