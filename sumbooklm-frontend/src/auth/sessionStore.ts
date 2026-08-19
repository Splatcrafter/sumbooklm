import type { Session } from '@/auth/session';
import { clearEncryptedCookie, readEncryptedCookie, writeEncryptedCookie } from '@/security/encryptedCookies';

/**
 * Suffix of the cookie the session is stored in.
 *
 * The session lives in the cookie the backend named itself, so its suffix is empty. Everything else
 * this client stores hangs off that name with a suffix of its own.
 */
const SESSION_COOKIE_SUFFIX = '';

/**
 * Encrypts the session and stores it in the cookie the backend named.
 *
 * The cookie is given the lifetime of the refresh token it carries. A stored session that outlived
 * its own token would be restored on the next visit only to be rejected by the backend.
 */
export async function writeSession(session: Session): Promise<void> {
  const lifetimeSeconds = (Date.parse(session.tokens.refreshTokenExpiresAt) - Date.now()) / 1000;
  await writeEncryptedCookie(SESSION_COOKIE_SUFFIX, session, lifetimeSeconds);
}

/**
 * Decrypts the stored session, or returns null when there is none the current key can open.
 */
export async function readSession(): Promise<Session | null> {
  return readEncryptedCookie<Session>(SESSION_COOKIE_SUFFIX);
}

/**
 * Removes the stored session.
 */
export async function clearSession(): Promise<void> {
  await clearEncryptedCookie(SESSION_COOKIE_SUFFIX);
}
