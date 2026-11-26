import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';

import { RequireAuth, RequireRole } from '@/auth/guards';
import { fakeToken, renderWithProviders } from '@/test/utils';

function renderAdminRoute() {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<p>Login page</p>} />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<p>Dashboard</p>} />
        <Route element={<RequireRole role="ADMIN" />}>
          <Route path="/admin" element={<p>Admin area</p>} />
        </Route>
      </Route>
    </Routes>,
    { route: '/admin' },
  );
}

describe('route guards', () => {
  it('sends anonymous users to login', () => {
    renderAdminRoute();
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });

  it('keeps customers out of the admin area', () => {
    localStorage.setItem('btp.session', fakeToken({ role: 'CUSTOMER' }));
    renderAdminRoute();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('lets admins in', () => {
    localStorage.setItem('btp.session', fakeToken({ role: 'ADMIN', username: 'admin' }));
    renderAdminRoute();
    expect(screen.getByText('Admin area')).toBeInTheDocument();
  });

  it('treats an expired token as logged out', () => {
    localStorage.setItem(
      'btp.session',
      fakeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) - 10 }),
    );
    renderAdminRoute();
    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(localStorage.getItem('btp.session')).toBeNull();
  });
});
