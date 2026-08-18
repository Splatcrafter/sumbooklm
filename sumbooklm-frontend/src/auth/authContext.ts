import { createContext } from 'react';

import type { AuthenticatedUser } from '@/auth/session';

/**
 * Reason an authentication attempt was rejected, used by views to pick a message.
 */
export type AuthFailureReason =
  | 'invalidCredentials'
  | 'usernameTaken'
  | 'invalidInput'
  | 'unexpected';

/**
 * Raised by the authentication actions when an attempt does not succeed.
 */
export class AuthFailure extends Error {
  readonly reason: AuthFailureReason;

  constructor(reason: AuthFailureReason) {
    super(`Authentication failed: ${reason}`);
    this.name = 'AuthFailure';
    this.reason = reason;
  }
}

/**
 * Data required to create an account.
 */
export interface RegistrationInput {
  username: string;
  firstName: string;
  lastName: string;
  password: string;
}

/**
 * State of the current visitor. The initial state is unknown because restoring a stored session
 * requires a request to the backend.
 */
export type AuthStatus = 'restoring' | 'authenticated' | 'anonymous';

/**
 * Everything views need in order to authenticate and to call protected endpoints.
 */
export interface AuthContextValue {
  status: AuthStatus;
  user: AuthenticatedUser | null;
  login: (username: string, password: string) => Promise<void>;
  register: (input: RegistrationInput) => Promise<void>;
  logout: () => Promise<void>;
  /**
   * Returns an access token that is valid at the moment of the call, refreshing the token pair when
   * the current one has expired, or null when the visitor is not authenticated.
   */
  authorize: () => Promise<string | null>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
