import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';

import { AuthProvider } from '@/auth/AuthProvider';
import { ThemeProvider } from '@/theme/ThemeProvider';

/** Renders with the same providers as the real app (fresh QueryClient, no retries). */
export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <AuthProvider>
          <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

/** Builds an unsigned JWT with the platform's claims (the client never verifies signatures). */
export function fakeToken(claims: {
  sub?: string;
  username?: string;
  role?: 'ADMIN' | 'CUSTOMER';
  exp?: number;
}) {
  const payload = {
    sub: claims.sub ?? '11111111-1111-1111-1111-111111111111',
    username: claims.username ?? 'alice',
    role: claims.role ?? 'CUSTOMER',
    exp: claims.exp ?? Math.floor(Date.now() / 1000) + 3600,
  };
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(payload)}.signature`;
}
