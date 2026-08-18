import { apiClient } from '@/api/client';
import type { Session } from '@/auth/session';

/**
 * Cryptographic parameters the backend derived for this client.
 */
interface CryptographyParameters {
  cookieName: string;
  algorithm: string;
  authenticationTagLength: number;
  initializationVectorLength: number;
  key: string;
  initializationVector: string;
}

function toBytes(base64: string): Uint8Array<ArrayBuffer> {
  const binary = atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const match = document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : null;
}

function writeCookie(name: string, value: string, maxAgeSeconds: number): void {
  const attributes = [
    `${name}=${encodeURIComponent(value)}`,
    'path=/',
    `max-age=${Math.max(0, Math.floor(maxAgeSeconds))}`,
    'samesite=strict',
  ];
  if (window.location.protocol === 'https:') {
    attributes.push('secure');
  }
  document.cookie = attributes.join('; ');
}

function deleteCookie(name: string): void {
  document.cookie = `${name}=; path=/; max-age=0; samesite=strict`;
}

/**
 * Reads the parameters the backend derived for the key handle this browser holds.
 *
 * Returns null when the browser carries no key handle, which is the normal state of a visitor who
 * has never authenticated.
 */
async function cryptographyParameters(): Promise<CryptographyParameters | null> {
  const { data, response } = await apiClient.GET('/api/v1/security/cookie-iv/');
  if (!response.ok || !data) {
    return null;
  }
  const {
    cookieName,
    algorithm,
    authenticationTagLength,
    initializationVectorLength,
    key,
    initializationVector,
  } = data;
  if (
    !cookieName ||
    !algorithm ||
    !key ||
    !initializationVector ||
    authenticationTagLength === undefined ||
    initializationVectorLength === undefined
  ) {
    return null;
  }
  return {
    cookieName,
    algorithm,
    authenticationTagLength,
    initializationVectorLength,
    key,
    initializationVector,
  };
}

async function importKey(parameters: CryptographyParameters): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', toBytes(parameters.key), { name: parameters.algorithm }, false, [
    'encrypt',
    'decrypt',
  ]);
}

/**
 * Encrypts the session and stores it in the cookie the backend named.
 *
 * The vector used for this encryption is stored in front of the ciphertext, because the backend
 * hands out a fresh vector on every call and decryption has to use the one the data was written
 * with.
 */
export async function writeSession(session: Session): Promise<void> {
  const parameters = await cryptographyParameters();
  if (!parameters) {
    return;
  }
  const initializationVector = toBytes(parameters.initializationVector);
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      {
        name: parameters.algorithm,
        iv: initializationVector,
        tagLength: parameters.authenticationTagLength,
      },
      await importKey(parameters),
      new TextEncoder().encode(JSON.stringify(session)),
    ),
  );

  const payload = new Uint8Array(new ArrayBuffer(initializationVector.length + ciphertext.length));
  payload.set(initializationVector);
  payload.set(ciphertext, initializationVector.length);

  const lifetimeSeconds = (Date.parse(session.tokens.refreshTokenExpiresAt) - Date.now()) / 1000;
  writeCookie(parameters.cookieName, toBase64(payload), lifetimeSeconds);
}

/**
 * Decrypts the stored session, or returns null when there is none the current key can open.
 */
export async function readSession(): Promise<Session | null> {
  const parameters = await cryptographyParameters();
  if (!parameters) {
    return null;
  }
  const stored = readCookie(parameters.cookieName);
  if (!stored) {
    return null;
  }

  const payload = toBytes(stored);
  const initializationVector = payload.slice(0, parameters.initializationVectorLength);
  const ciphertext = payload.slice(parameters.initializationVectorLength);
  try {
    const plaintext = await crypto.subtle.decrypt(
      {
        name: parameters.algorithm,
        iv: initializationVector,
        tagLength: parameters.authenticationTagLength,
      },
      await importKey(parameters),
      ciphertext,
    );
    return JSON.parse(new TextDecoder().decode(plaintext)) as Session;
  } catch {
    // The cookie cannot be opened with the current key, which happens after the server side
    // derivation secret was rotated. It is unusable rather than merely absent, so it is removed.
    deleteCookie(parameters.cookieName);
    return null;
  }
}

/**
 * Removes the stored session.
 */
export async function clearSession(): Promise<void> {
  const parameters = await cryptographyParameters();
  deleteCookie(parameters?.cookieName ?? 'sumbooklm_auth');
}
