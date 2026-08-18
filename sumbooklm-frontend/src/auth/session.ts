import type { components } from '@/api/schema';

/**
 * The account a session belongs to.
 */
export interface AuthenticatedUser {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  registeredAt: string;
  lastLoginAt: string;
}

/**
 * The token pair a session is authenticated with.
 */
export interface TokenPair {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

/**
 * Everything the client keeps about an authenticated user between page loads.
 */
export interface Session {
  user: AuthenticatedUser;
  tokens: TokenPair;
}

/**
 * Raised when a response of the backend does not carry the fields the client relies on.
 *
 * The generated types mark every property as optional, because the specification does not declare
 * required fields. Narrowing happens here so that the rest of the application works with values
 * that are known to be present.
 */
export class MalformedResponseError extends Error {
  constructor(field: string) {
    super(`The response is missing the field "${field}"`);
    this.name = 'MalformedResponseError';
  }
}

function required(value: string | undefined, field: string): string {
  if (value === undefined || value === '') {
    throw new MalformedResponseError(field);
  }
  return value;
}

/**
 * Narrows the token pair of a backend response into its client side form.
 */
export function toTokenPair(tokens: components['schemas']['TokenPairResponse'] | undefined): TokenPair {
  if (!tokens) {
    throw new MalformedResponseError('tokens');
  }
  return {
    tokenType: required(tokens.tokenType, 'tokens.tokenType'),
    accessToken: required(tokens.accessToken, 'tokens.accessToken'),
    accessTokenExpiresAt: required(tokens.accessTokenExpiresAt, 'tokens.accessTokenExpiresAt'),
    refreshToken: required(tokens.refreshToken, 'tokens.refreshToken'),
    refreshTokenExpiresAt: required(tokens.refreshTokenExpiresAt, 'tokens.refreshTokenExpiresAt'),
  };
}

/**
 * Narrows the account of a backend response into its client side form.
 */
export function toUser(user: components['schemas']['AuthenticatedUser'] | undefined): AuthenticatedUser {
  if (!user) {
    throw new MalformedResponseError('user');
  }
  return {
    id: required(user.id, 'user.id'),
    username: required(user.username, 'user.username'),
    firstName: required(user.firstName, 'user.firstName'),
    lastName: required(user.lastName, 'user.lastName'),
    registeredAt: required(user.registeredAt, 'user.registeredAt'),
    lastLoginAt: required(user.lastLoginAt, 'user.lastLoginAt'),
  };
}

/**
 * Reports whether a token has already expired, with a margin that keeps a request from being sent
 * with a token that expires while it is in flight.
 */
export function isExpired(expiresAt: string, marginSeconds = 15): boolean {
  return Date.parse(expiresAt) - marginSeconds * 1000 <= Date.now();
}
