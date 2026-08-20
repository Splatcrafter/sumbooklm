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

/**
 * Service a chat model is requested from.
 *
 * <h2>Two Shapes, Three Constants</h2>
 * The cloud providers speak the same protocol and differ only in the endpoint they answer on, so both
 * are served by the same client with another base address. A locally running server is the second
 * shape: it speaks its own protocol and needs no key, because reaching it already means being on the
 * machine it runs on.
 *
 * <h2>Adding a Provider</h2>
 * A further OpenAI compatible service is one constant with its own default address. A service with
 * its own protocol additionally needs a branch in {@link ChatModelFactory}, which is the only place
 * that knows how a constant becomes a client.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum ChatProvider {

    /**
     * The OpenAI API, addressed with a key of the calling user.
     */
    OPENAI("https://api.openai.com/v1", true),

    /**
     * The OpenAI compatible API of Groq, addressed with a key of the calling user.
     */
    GROQ("https://api.groq.com/openai/v1", true),

    /**
     * A locally running Ollama server, addressed without a key.
     */
    OLLAMA("http://localhost:11434", false);

    /**
     * Address used when the caller names none.
     */
    private final String defaultBaseUrl;

    /**
     * Whether a request to this provider has to carry an API key.
     */
    private final boolean apiKeyRequired;

    /**
     * Creates a constant.
     *
     * @param defaultBaseUrl address used when the caller names none
     * @param apiKeyRequired whether a request to this provider has to carry an API key
     */
    ChatProvider(final String defaultBaseUrl, final boolean apiKeyRequired) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.apiKeyRequired = apiKeyRequired;
    }

    /**
     * Returns the address used when the caller names none.
     *
     * @return default base address of the provider, without a trailing slash
     */
    public String defaultBaseUrl() {
        return this.defaultBaseUrl;
    }

    /**
     * Reports whether a request to this provider has to carry an API key.
     *
     * @return {@code true} if a key is required, {@code false} if the provider is reached without one
     */
    public boolean isApiKeyRequired() {
        return this.apiKeyRequired;
    }
}
