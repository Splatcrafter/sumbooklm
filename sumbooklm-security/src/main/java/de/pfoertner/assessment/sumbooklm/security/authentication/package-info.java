/**
 * Registration of accounts and verification of credentials.
 *
 * <h2>Input and Output</h2>
 * The package takes commands that carry everything a request contributed, including the network
 * address of the caller, and returns the authenticated account together with a freshly issued token
 * pair. Turning that into an HTTP response, and deciding which cookies accompany it, belongs to the
 * transport layer.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.security.authentication;
