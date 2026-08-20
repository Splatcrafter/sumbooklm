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

import { toModelSettings, type ModelSettings } from '@/byok/modelSettings';
import { clearEncryptedCookie, readEncryptedCookie, writeEncryptedCookie } from '@/security/encryptedCookies';

/**
 * Suffix of the cookie the model settings are stored in.
 */
const SETTINGS_COOKIE_SUFFIX = '_model';

/**
 * How long the stored settings survive, matching the lifetime of the key they are encrypted under.
 *
 * The key belongs to the session, so settings that outlived it would be unreadable rather than
 * merely old. Signing out therefore also ends the life of the stored key, which is the intended
 * behaviour for a credential the user handed in.
 */
const SETTINGS_LIFETIME_SECONDS = 90 * 24 * 60 * 60;

/**
 * Encrypts the settings and stores them next to the session.
 */
export async function writeModelSettings(settings: ModelSettings): Promise<void> {
  await writeEncryptedCookie(SETTINGS_COOKIE_SUFFIX, settings, SETTINGS_LIFETIME_SECONDS);
}

/**
 * Decrypts the stored settings, or returns null when there are none this session can open.
 */
export async function readModelSettings(): Promise<ModelSettings | null> {
  return toModelSettings(await readEncryptedCookie<unknown>(SETTINGS_COOKIE_SUFFIX));
}

/**
 * Removes the stored settings.
 */
export async function clearModelSettings(): Promise<void> {
  await clearEncryptedCookie(SETTINGS_COOKIE_SUFFIX);
}
