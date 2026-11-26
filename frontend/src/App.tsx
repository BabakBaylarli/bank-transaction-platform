import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, Suspense, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';

import { AuthProvider } from '@/auth/AuthProvider';
import { GuestOnly, RequireAuth, RequireRole } from '@/auth/guards';
import { AppLayout } from '@/components/layout/AppLayout';
import { PageLoader } from '@/components/PageLoader';
import { Toaster } from '@/components/ui/sonner';
import { createQueryClient } from '@/lib/queryClient';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ThemeProvider } from '@/theme/ThemeProvider';

// route-level code splitting keeps the first load small (charts are only needed after login)
const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const AccountsPage = lazy(() => import('@/pages/AccountsPage'));
const AccountDetailPage = lazy(() => import('@/pages/AccountDetailPage'));
const TransferPage = lazy(() => import('@/pages/TransferPage'));
const TransactionsPage = lazy(() => import('@/pages/TransactionsPage'));
const AdminPage = lazy(() => import('@/pages/AdminPage'));

export function App() {
  const [queryClient] = useState(createQueryClient);
  return (
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrowserRouter>
            <Suspense fallback={<PageLoader />}>
              <Routes>
                <Route element={<GuestOnly />}>
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/register" element={<RegisterPage />} />
                </Route>
                <Route element={<RequireAuth />}>
                  <Route element={<AppLayout />}>
                    <Route index element={<DashboardPage />} />
                    <Route path="accounts" element={<AccountsPage />} />
                    <Route path="accounts/:number" element={<AccountDetailPage />} />
                    <Route path="transfer" element={<TransferPage />} />
                    <Route path="transactions" element={<TransactionsPage />} />
                    <Route element={<RequireRole role="ADMIN" />}>
                      <Route path="admin" element={<AdminPage />} />
                    </Route>
                  </Route>
                </Route>
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </Suspense>
          </BrowserRouter>
          <Toaster />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
