import { apiClient } from '@/api/client';

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

/**
 * Name used when the backend cannot be asked which cookie it named.
 *
 * It is only ever used to delete one. Deleting a cookie that does not exist is harmless, while
 * leaving an unreadable one behind is not.
 */
const FALLBACK_COOKIE_NAME = 'sumbooklm_auth';

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
 * Encrypts a value and stores it in one of the cookies belonging to this client.
 *
 * The backend names a single cookie, and everything this client stores is a variant of that name.
 * Deriving the names from the one the backend chose keeps them all under the same key and lets them
 * all become unreadable together when the session ends.
 *
 * The vector used for this encryption is stored in front of the ciphertext, because the backend hands
 * out a fresh vector on every call and decryption has to use the one the data was written with.
 */
export async function writeEncryptedCookie(
  suffix: string,
  value: unknown,
  lifetimeSeconds: number,
): Promise<void> {
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
      new TextEncoder().encode(JSON.stringify(value)),
    ),
  );

  const payload = new Uint8Array(new ArrayBuffer(initializationVector.length + ciphertext.length));
  payload.set(initializationVector);
  payload.set(ciphertext, initializationVector.length);

  writeCookie(`${parameters.cookieName}${suffix}`, toBase64(payload), lifetimeSeconds);
}

/**
 * Decrypts one of the stored cookies, or returns null when there is none the current key can open.
 */
export async function readEncryptedCookie<T>(suffix: string): Promise<T | null> {
  const parameters = await cryptographyParameters();
  if (!parameters) {
    return null;
  }
  const name = `${parameters.cookieName}${suffix}`;
  const stored = readCookie(name);
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
    return JSON.parse(new TextDecoder().decode(plaintext)) as T;
  } catch {
    // The cookie cannot be opened with the current key, which happens after the server side
    // derivation secret was rotated. It is unusable rather than merely absent, so it is removed.
    deleteCookie(name);
    return null;
  }
}

/**
 * Removes one of the stored cookies.
 */
export async function clearEncryptedCookie(suffix: string): Promise<void> {
  const parameters = await cryptographyParameters();
  deleteCookie(`${parameters?.cookieName ?? FALLBACK_COOKIE_NAME}${suffix}`);
}
