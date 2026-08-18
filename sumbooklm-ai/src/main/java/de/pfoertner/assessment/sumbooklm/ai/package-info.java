/**
 * Chat model access, embedding computation and vector storage.
 *
 * <h2>Model Access</h2>
 * Chat completions are served either by an OpenAI compatible endpoint addressed with a caller
 * supplied API key, or by a locally running inference server such as Ollama. Both paths are exposed
 * through the same abstraction so that callers are independent of the selected provider.
 *
 * <h2>Embeddings</h2>
 * Embeddings are computed in process with the quantised all-MiniLM-L6-v2 model, which requires no
 * remote endpoint and no API key. Vectors are held in the in memory embedding store of LangChain4j.
 *
 * <h2>Dependency Rule</h2>
 * This module depends on the domain module only.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.ai;
