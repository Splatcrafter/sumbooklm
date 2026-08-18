/**
 * Authentication and token lifecycle of the application.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Register accounts and verify credentials.</li>
 *   <li>Issue, rotate and revoke the access and refresh token pair.</li>
 *   <li>Derive the parameters a client encrypts its stored token pair with.</li>
 *   <li>Provide the marker that makes an operation verify its session against the database.</li>
 * </ul>
 *
 * <h2>Boundary</h2>
 * The module knows nothing about HTTP. It receives the data a request carries, such as the network
 * address of the caller, as ordinary arguments, and it returns objects rather than responses. Which
 * routes are reachable without authentication, and how a token reaches a response, is decided in the
 * transport layer.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.security;
