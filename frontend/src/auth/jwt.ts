import type { Role } from '@/api/types';

export interface SessionUser {
  id: string;
  username: string;
  role: Role;
  /** epoch millis */
  expiresAt: number;
}

/** Decodes (does NOT verify - the server does that) the payload of the platform's JWT. */
export function decodeToken(token: string): SessionUser | null {
  try {
    const payload = token.split('.')[1];
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    if (!json.sub || !json.role || !json.exp) return null;
    return { id: json.sub, username: json.username, role: json.role, expiresAt: json.exp * 1000 };
  } catch {
    return null;
  }
}
