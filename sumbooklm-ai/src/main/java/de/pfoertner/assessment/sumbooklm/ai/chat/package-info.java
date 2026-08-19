/**
 * Access to a chat model and the assembly of the grounded prompt it is asked with.
 *
 * <h2>No Model of Its Own</h2>
 * The application holds no API key and publishes no chat model bean. A model is built for the
 * duration of one request from what the caller presented, which is what makes the key the property of
 * the user rather than of the deployment. The types here therefore describe a selection and turn it
 * into a client, instead of describing a configured provider.
 *
 * <h2>Grounding Lives Here</h2>
 * The instructions that bind an answer to the retrieved passages are built in this package rather
 * than in the layer that retrieves them. Retrieval decides what the model may see; this package
 * decides what it is allowed to do with it, and the two are separate decisions.
 *
 * <h2>Unaware of Notebooks</h2>
 * Nothing here knows what a notebook is. Passages arrive already selected and already named, so the
 * same engine answers from any set of texts.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.ai.chat;
