/**
 * Domain model of user accounts.
 *
 * <h2>Scope</h2>
 * The types in this package describe who a user is and what the application knows about the
 * lifecycle of their account. They carry neither credentials nor tokens: a password hash is a
 * persistence concern and a token is a transport concern, and neither belongs to the identity of a
 * user.
 *
 * <h2>Dependency Rule</h2>
 * As with the rest of the domain module, the types here are plain Java without persistence,
 * transport or security framework annotations.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.domain.user;
