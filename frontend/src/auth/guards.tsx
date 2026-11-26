import { Navigate, Outlet, useLocation } from 'react-router-dom';

import type { Role } from '@/api/types';

import { useAuth } from './AuthProvider';

/** Redirects anonymous users to /login and remembers where they wanted to go. */
export function RequireAuth() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

/** Only renders the child routes for the given role; everyone else is sent to the dashboard. */
export function RequireRole({ role }: { role: Role }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to="/" replace />;
  return <Outlet />;
}

/** Login/register pages are pointless for a logged-in user. */
export function GuestOnly() {
  const { user } = useAuth();
  return user ? <Navigate to="/" replace /> : <Outlet />;
}
