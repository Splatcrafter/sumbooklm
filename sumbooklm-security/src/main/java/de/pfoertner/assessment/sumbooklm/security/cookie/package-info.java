/**
 * Cryptographic parameters for the client side token store.
 *
 * <h2>Problem</h2>
 * A browser that keeps a token pair across page loads has to store it somewhere the page can read.
 * Anything a page can read, a script injected into that page can read as well. Storing the pair in
 * clear text therefore turns any script injection into a full session takeover.
 *
 * <h2>Approach</h2>
 * The client stores the pair encrypted and never holds the key. What the browser holds is an opaque
 * handle in a cookie that scripts cannot read. The key is derived from that handle and a server side
 * secret, and it is handed out only to a caller whose request carries the handle cookie. Neither the
 * encrypted cookie alone nor a copy of the key alone is enough to recover a token.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.security.cookie;
