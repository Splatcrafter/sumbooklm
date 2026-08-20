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

package de.pfoertner.assessment.sumbooklm.ai.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Publishes the embedding model and the vector store.
 *
 * <h2>Deferred Model Loading</h2>
 * The model bean is lazy, and so is the point it is injected at. Constructing it reads a neural
 * network of roughly ninety megabytes out of the classpath and builds an inference session from it,
 * which would be paid at every start of the application and of every test context even though a run
 * that indexes nothing never uses it. The first source that is indexed pays for it instead.
 *
 * <h2>In Memory Store</h2>
 * The store keeps its vectors in the heap and loses them when the application stops. That is the
 * store the brief asks for, and it is consistent as long as it is the only place the vectors live:
 * they are derived from sources that are stored durably, so they can be produced again.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public EmbeddingConfiguration() {
    }

    /**
     * Provides the model that turns text into vectors.
     *
     * @return the in process all-MiniLM-L6-v2 model, producing vectors of 384 dimensions
     */
    @Bean
    @Lazy
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * Provides the store the segments of every notebook are kept in.
     *
     * @return an in memory store shared by all notebooks, kept apart by segment metadata
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
