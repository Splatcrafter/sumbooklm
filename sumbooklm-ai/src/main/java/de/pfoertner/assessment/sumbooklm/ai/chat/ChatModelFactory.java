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

import java.time.Duration;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;

/**
 * Builds the streaming client one request is answered through.
 *
 * <h2>One Client per Request</h2>
 * A client is created for a single answer and discarded afterwards, because the key it is built with
 * belongs to the caller rather than to the application. Caching clients would mean keeping keys in
 * memory across requests and deciding when a changed setting takes effect, which is a cache to invent
 * once the same user asks often enough for the setup cost to matter.
 *
 * <h2>The Client Is Chosen, Not Discovered</h2>
 * Both models are built with an HTTP client of this application's choosing rather than with the one
 * the framework finds on the class path. That is what makes a stop reach the provider: stopping closes
 * the body of the response, and only a client whose close abandons the exchange turns that into an
 * abandoned request. The client the framework would find here wraps a library whose close first reads
 * the rest of the message, which is the same as not stopping at all, and which is on this class path
 * because reading web sources needs it.
 *
 * <h2>Two Branches, Three Providers</h2>
 * Both cloud providers speak the OpenAI protocol and differ only in the address, so they share a
 * branch. Ollama has a protocol of its own and needs none of the key handling, which is what makes it
 * the second branch rather than a third address.
 *
 * <h2>Two Shapes of Client</h2>
 * An answer is read while it is written and arrives as a stream. A summary is read once it exists and
 * arrives as one response. The two are different clients of the same provider, built here from the
 * same selection, so that a caller chooses the shape it needs rather than a second way of reaching a
 * model.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class ChatModelFactory {

    /**
     * Time a provider is given to answer before the attempt is abandoned. A generated answer arrives
     * as a stream, so the value bounds the wait for the connection and for the parts of one response,
     * not the length of the answer.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    /**
     * Sampling temperature every answer is requested with. An answer that may only repeat what its
     * sources say has nothing to gain from variation, so the value is as low as the providers accept
     * without being deterministic in name only.
     */
    private static final double TEMPERATURE = 0.2;

    /**
     * Creates the factory. The instance is created by the container and holds no state.
     */
    public ChatModelFactory() {
    }

    /**
     * Builds the HTTP client one answer is streamed over.
     *
     * @return a client whose response body can be abandoned rather than drained
     */
    private static HttpClientBuilder abortableClient() {
        return new JdkHttpClientBuilder()
                .connectTimeout(TIMEOUT)
                .readTimeout(TIMEOUT);
    }

    /**
     * Builds a client for the model a caller selected.
     *
     * @param selection model to address, already validated
     * @return a client that streams the answer of that model
     */
    public StreamingChatModel create(final ModelSelection selection) {
        return switch (selection.provider()) {
            case OPENAI, GROQ -> OpenAiStreamingChatModel.builder()
                    .httpClientBuilder(abortableClient())
                    .baseUrl(selection.baseUrl())
                    .apiKey(selection.apiKey())
                    .modelName(selection.modelName())
                    .temperature(TEMPERATURE)
                    .timeout(TIMEOUT)
                    .build();
            case OLLAMA -> OllamaStreamingChatModel.builder()
                    .httpClientBuilder(abortableClient())
                    .baseUrl(selection.baseUrl())
                    .modelName(selection.modelName())
                    .temperature(TEMPERATURE)
                    .timeout(TIMEOUT)
                    .build();
        };
    }

    /**
     * Builds a client that returns the whole response of the model a caller selected.
     *
     * @param selection model to address, already validated
     * @return a client that answers once, with everything the model produced
     */
    public ChatModel createComplete(final ModelSelection selection) {
        return switch (selection.provider()) {
            case OPENAI, GROQ -> OpenAiChatModel.builder()
                    .httpClientBuilder(abortableClient())
                    .baseUrl(selection.baseUrl())
                    .apiKey(selection.apiKey())
                    .modelName(selection.modelName())
                    .temperature(TEMPERATURE)
                    .timeout(TIMEOUT)
                    .build();
            case OLLAMA -> OllamaChatModel.builder()
                    .httpClientBuilder(abortableClient())
                    .baseUrl(selection.baseUrl())
                    .modelName(selection.modelName())
                    .temperature(TEMPERATURE)
                    .timeout(TIMEOUT)
                    .build();
        };
    }
}
