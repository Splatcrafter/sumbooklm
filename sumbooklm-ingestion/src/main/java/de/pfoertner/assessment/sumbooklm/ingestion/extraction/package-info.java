/**
 * Turning an uploaded file or a web address into plain text.
 *
 * <h2>Two Extractors, One Result</h2>
 * A file and a web page reach the application in different shapes and are read by different
 * libraries, but everything downstream of this package works on text alone. Both extractors
 * therefore return the same result type, and nothing outside this package has to know which of the
 * two produced it.
 *
 * <h2>Paragraph Boundaries Are Content</h2>
 * The text produced here keeps blank lines between blocks. The splitter that runs later cuts on
 * paragraph boundaries, so an extractor that flattened a page into one line would leave it nothing
 * to cut on and would hand the model chunks that end mid-thought.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.ingestion.extraction;
