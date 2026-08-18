import { MalformedResponseError, requireString } from '@/api/narrowing';
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
 * Narrows the token pair of a backend response into its client side form.
 */
export function toTokenPair(tokens: components['schemas']['TokenPairResponse'] | undefined): TokenPair {
  if (!tokens) {
    throw new MalformedResponseError('tokens');
  }
  return {
    tokenType: requireString(tokens.tokenType, 'tokens.tokenType'),
    accessToken: requireString(tokens.accessToken, 'tokens.accessToken'),
    accessTokenExpiresAt: requireString(tokens.accessTokenExpiresAt, 'tokens.accessTokenExpiresAt'),
    refreshToken: requireString(tokens.refreshToken, 'tokens.refreshToken'),
    refreshTokenExpiresAt: requireString(tokens.refreshTokenExpiresAt, 'tokens.refreshTokenExpiresAt'),
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
    id: requireString(user.id, 'user.id'),
    username: requireString(user.username, 'user.username'),
    firstName: requireString(user.firstName, 'user.firstName'),
    lastName: requireString(user.lastName, 'user.lastName'),
    registeredAt: requireString(user.registeredAt, 'user.registeredAt'),
    lastLoginAt: requireString(user.lastLoginAt, 'user.lastLoginAt'),
  };
}

/**
 * Reports whether a token has already expired, with a margin that keeps a request from being sent
 * with a token that expires while it is in flight.
 */
export function isExpired(expiresAt: string, marginSeconds = 15): boolean {
  return Date.parse(expiresAt) - marginSeconds * 1000 <= Date.now();
}
