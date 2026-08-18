/**
 * Helpers that turn the generated response types into the types the application works with.
 *
 * The generated types mark every property as optional, because the OpenAPI document declares no
 * required fields. Narrowing happens once per response shape so that the rest of the application
 * works with values that are known to be present, and a backend that omits a field fails loudly at
 * the boundary instead of producing an undefined somewhere far from its cause.
 */

/**
 * Raised when a response of the backend does not carry a field the client relies on.
 */
export class MalformedResponseError extends Error {
  constructor(field: string) {
    super(`The response is missing the field "${field}"`);
    this.name = 'MalformedResponseError';
  }
}

/**
 * Returns a string field of a response, or fails when it is absent or empty.
 */
export function requireString(value: string | undefined, field: string): string {
  if (value === undefined || value === '') {
    throw new MalformedResponseError(field);
  }
  return value;
}

/**
 * Returns a string field of a response that is allowed to be empty, or fails when it is absent.
 */
export function requireText(value: string | undefined, field: string): string {
  if (value === undefined) {
    throw new MalformedResponseError(field);
  }
  return value;
}

/**
 * Returns a numeric field of a response, or fails when it is absent.
 */
export function requireNumber(value: number | undefined, field: string): number {
  if (value === undefined) {
    throw new MalformedResponseError(field);
  }
  return value;
}

/**
 * Returns a boolean field of a response, or fails when it is absent.
 */
export function requireBoolean(value: boolean | undefined, field: string): boolean {
  if (value === undefined) {
    throw new MalformedResponseError(field);
  }
  return value;
}
