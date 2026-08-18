/**
 * First version of the HTTP API.
 *
 * <h2>Scope of a Version</h2>
 * Everything below this package describes one wire contract. Request and response models are defined
 * here rather than reused from other layers, so that a change to a domain or persistence type cannot
 * silently change what clients receive.
 *
 * <h2>Compatibility</h2>
 * Within a version, changes are additive: new optional fields and new endpoints. A change that would
 * break a client is published as the next version instead.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.api.v1;
