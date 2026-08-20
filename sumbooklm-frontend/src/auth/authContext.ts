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
