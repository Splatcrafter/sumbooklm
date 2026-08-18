/**
 * Source acquisition and text extraction.
 *
 * <h2>Pipeline</h2>
 * <ul>
 *   <li>Web sources are fetched and reduced to their readable article content with jsoup.</li>
 *   <li>The resulting byte stream, as well as uploaded documents such as PDF, Markdown, HTML or
 *       plain text, is handed to the Apache Tika document parser provided by LangChain4j.</li>
 *   <li>The extracted text is normalised and split into chunks that the AI module can embed.</li>
 * </ul>
 *
 * <h2>Dependency Rule</h2>
 * This module depends on the domain module only. It produces domain level results and never
 * accesses persistence or transport types.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.ingestion;
