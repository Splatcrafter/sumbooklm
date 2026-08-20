/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
