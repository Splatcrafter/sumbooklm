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
