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

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { apiClient } from '@/api/client';
import {
  AuthContext,
  AuthFailure,
  type AuthContextValue,
  type AuthStatus,
  type RegistrationInput,
} from '@/auth/authContext';
import { isExpired, toTokenPair, toUser, type Session } from '@/auth/session';
import { clearSession, readSession, writeSession } from '@/auth/sessionStore';

function failureFor(status: number): AuthFailure {
  switch (status) {
    case 401:
      return new AuthFailure('invalidCredentials');
    case 409:
      return new AuthFailure('usernameTaken');
    case 400:
      return new AuthFailure('invalidInput');
    default:
      return new AuthFailure('unexpected');
  }
}

/**
 * Holds the session of the current visitor and exposes the actions that change it.
 *
 * The session is restored on mount from the encrypted cookie the client wrote. Restoring can end in
 * three ways: no session exists, the stored refresh token is still usable and the pair is renewed,
 * or the stored session is rejected by the backend and is discarded.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [status, setStatus] = useState<AuthStatus>('restoring');

  // A component tree can trigger several protected calls at once. Without this reference each of
  // them would rotate the refresh token, and every rotation but the first would present a token
  // that the previous one already consumed, which the backend answers by closing the session.
  const pendingRefresh = useRef<Promise<Session | null> | null>(null);

  const discard = useCallback(async () => {
    pendingRefresh.current = null;
    setSession(null);
    setStatus('anonymous');
    await clearSession();
  }, []);

  const adopt = useCallback(async (next: Session) => {
    setSession(next);
    setStatus('authenticated');
    await writeSession(next);
  }, []);

  const refresh = useCallback(
    async (current: Session): Promise<Session | null> => {
      if (pendingRefresh.current) {
        return pendingRefresh.current;
      }
      const attempt = (async () => {
        const { data, response } = await apiClient.POST('/api/v1/token/refresh', {
          body: { refreshToken: current.tokens.refreshToken },
        });
        if (!response.ok || !data) {
          await discard();
          return null;
        }
        const next: Session = { user: current.user, tokens: toTokenPair(data) };
        await adopt(next);
        return next;
      })();
      pendingRefresh.current = attempt;
      try {
        return await attempt;
      } finally {
        pendingRefresh.current = null;
      }
    },
    [adopt, discard],
  );

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const stored = await readSession();
      if (cancelled) {
        return;
      }
      if (!stored || isExpired(stored.tokens.refreshTokenExpiresAt)) {
        await discard();
        return;
      }
      if (isExpired(stored.tokens.accessTokenExpiresAt)) {
        await refresh(stored);
        return;
      }
      await adopt(stored);
    })();
    return () => {
      cancelled = true;
    };
  }, [adopt, discard, refresh]);

  const login = useCallback(
    async (username: string, password: string) => {
      const { data, response } = await apiClient.POST('/api/v1/login', {
        body: { username, password },
      });
      if (!response.ok || !data) {
        throw failureFor(response.status);
      }
      await adopt({ user: toUser(data.user), tokens: toTokenPair(data.tokens) });
    },
    [adopt],
  );

  const register = useCallback(
    async (input: RegistrationInput) => {
      const { data, response } = await apiClient.POST('/api/v1/register', { body: input });
      if (!response.ok || !data) {
        throw failureFor(response.status);
      }
      await adopt({ user: toUser(data.user), tokens: toTokenPair(data.tokens) });
    },
    [adopt],
  );

  const authorize = useCallback(async (): Promise<string | null> => {
    if (!session) {
      return null;
    }
    if (!isExpired(session.tokens.accessTokenExpiresAt)) {
      return session.tokens.accessToken;
    }
    const renewed = await refresh(session);
    return renewed ? renewed.tokens.accessToken : null;
  }, [refresh, session]);

  const logout = useCallback(async () => {
    const accessToken = await authorize();
    if (accessToken) {
      await apiClient.POST('/api/v1/logout', {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
    }
    await discard();
  }, [authorize, discard]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user: session?.user ?? null,
      login,
      register,
      logout,
      authorize,
    }),
    [authorize, login, logout, register, session, status],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
