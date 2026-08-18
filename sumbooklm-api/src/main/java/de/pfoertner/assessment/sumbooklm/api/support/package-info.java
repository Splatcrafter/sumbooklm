/**
 * Helpers that turn servlet level facts into arguments the lower layers can consume.
 *
 * <h2>Reason</h2>
 * The security module receives everything it needs as ordinary values and never touches the servlet
 * API. The classes here are the boundary that produces those values.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.api.support;
