import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { configureApiClient } from '@/api/client';
import { api } from '@/api/endpoints';
import type { LoginRequest } from '@/api/types';

import { decodeToken, type SessionUser } from './jwt';

const STORAGE_KEY = 'btp.session';

interface AuthState {
  user: SessionUser | null;
  token: string | null;
  login: (credentials: LoginRequest) => Promise<SessionUser>;
  logout: (reason?: 'expired') => void;
}

const AuthContext = createContext<AuthState | null>(null);

function restore(): { token: string; user: SessionUser } | null {
  try {
    const token = localStorage.getItem(STORAGE_KEY);
    const user = token ? decodeToken(token) : null;
    if (token && user && user.expiresAt > Date.now()) return { token, user };
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // storage unavailable (private mode) - stay logged out
  }
  return null;
}

/**
 * The JWT lives in React state (memory) for every request. It is mirrored to localStorage only so a
 * page reload restores the session; logout and expiry remove it from both places.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState(restore);
  const tokenRef = useRef<string | null>(session?.token ?? null);
  const [expiredNotice, setExpiredNotice] = useState(false);

  const logout = useCallback(
    (reason?: 'expired') => {
      tokenRef.current = null;
      try {
        localStorage.removeItem(STORAGE_KEY);
      } catch {
        /* ignore */
      }
      setSession(null);
      queryClient.clear();
      setExpiredNotice(reason === 'expired');
    },
    [queryClient],
  );

  // the API client reads the token from memory and reports 401s back here
  useEffect(() => {
    configureApiClient(
      () => tokenRef.current,
      () => logout('expired'),
    );
  }, [logout]);

  // automatic logout exactly when the token expires
  useEffect(() => {
    if (!session) return;
    const ms = session.user.expiresAt - Date.now();
    const timer = window.setTimeout(() => logout('expired'), Math.max(ms, 0));
    return () => window.clearTimeout(timer);
  }, [session, logout]);

  const login = useCallback(async (credentials: LoginRequest) => {
    const { accessToken } = await api.auth.login(credentials);
    const user = decodeToken(accessToken);
    if (!user) throw new Error('Received an invalid token');
    tokenRef.current = accessToken;
    try {
      localStorage.setItem(STORAGE_KEY, accessToken);
    } catch {
      /* session will just not survive a reload */
    }
    setExpiredNotice(false);
    setSession({ token: accessToken, user });
    return user;
  }, []);

  const value = useMemo<AuthState>(
    () => ({ user: session?.user ?? null, token: session?.token ?? null, login, logout }),
    [session, login, logout],
  );

  return (
    <AuthContext.Provider value={value}>
      <SessionExpiredContext.Provider value={expiredNotice}>{children}</SessionExpiredContext.Provider>
    </AuthContext.Provider>
  );
}

const SessionExpiredContext = createContext(false);

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

export function useSessionExpired(): boolean {
  return useContext(SessionExpiredContext);
}
