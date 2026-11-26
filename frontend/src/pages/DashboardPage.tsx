import { useQuery } from '@tanstack/react-query';
import { ArrowLeftRight, Plus, Wallet } from 'lucide-react';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';

import { api } from '@/api/endpoints';
import { queryKeys } from '@/api/queryKeys';
import type { CurrencyCode, SearchHit } from '@/api/types';
import { AccountCard } from '@/components/AccountCard';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { TransactionList } from '@/components/TransactionList';
import { VolumeChart, type VolumePoint } from '@/components/charts/VolumeChart';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAccounts } from '@/hooks/useAccounts';
import { useAuth } from '@/auth/AuthProvider';

const DAYS = 7;

/** Customers cannot call the ADMIN stats endpoint, so the chart aggregates their own search results. */
export function buildDailyVolume(hits: SearchHit[], today = new Date()): VolumePoint[] {
  const days: VolumePoint[] = [];
  for (let i = DAYS - 1; i >= 0; i--) {
    const d = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() - i));
    days.push({ day: d.toISOString().slice(0, 10), AZN: 0, USD: 0, EUR: 0 });
  }
  const index = new Map(days.map((d) => [d.day, d]));
  for (const hit of hits) {
    if (hit.status !== 'COMPLETED' || !hit.createdAt) continue;
    const point = index.get(hit.createdAt.slice(0, 10));
    if (point) point[hit.currency as CurrencyCode] += Number(hit.amount);
  }
  return days;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const accounts = useAccounts();
  const since = useMemo(() => new Date(Date.now() - DAYS * 86_400_000).toISOString(), []);

  const recent = useQuery({
    queryKey: queryKeys.search({ size: 6 }),
    queryFn: ({ signal }) => api.search.transactions({ size: 6 }, signal),
    refetchInterval: 10_000,
  });
  const week = useQuery({
    queryKey: queryKeys.search({ from: since, size: 100, status: 'COMPLETED' }),
    queryFn: ({ signal }) => api.search.transactions({ from: since, size: 100, status: 'COMPLETED' }, signal),
  });
  const volume = useMemo(() => buildDailyVolume(week.data?.content ?? []), [week.data]);

  return (
    <>
      <PageHeader
        title={`Hello, ${user?.username}`}
        description="Your accounts and latest activity."
        actions={
          <Button asChild>
            <Link to="/transfer">
              <ArrowLeftRight /> New transfer
            </Link>
          </Button>
        }
      />

      <section aria-label="Accounts" className="mb-8">
        {accounts.isLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-[150px] rounded-xl" />
            ))}
          </div>
        ) : accounts.data?.length ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {accounts.data.map((a) => (
              <AccountCard key={a.id} account={a} />
            ))}
          </div>
        ) : (
          <EmptyState
            icon={Wallet}
            title="No accounts yet"
            description="Open an account in AZN, USD or EUR to start sending money."
            action={
              <Button asChild>
                <Link to="/accounts">
                  <Plus /> Open account
                </Link>
              </Button>
            }
          />
        )}
      </section>

      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <div>
              <CardTitle>Recent transactions</CardTitle>
              <CardDescription>Incoming and outgoing</CardDescription>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/transactions">View all</Link>
            </Button>
          </CardHeader>
          <CardContent>
            <TransactionList
              hits={recent.data?.content}
              loading={recent.isLoading}
              myAccounts={accounts.numbers}
            />
          </CardContent>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Volume · last 7 days</CardTitle>
            <CardDescription>Completed transfers per currency</CardDescription>
          </CardHeader>
          <CardContent>
            {week.isLoading ? <Skeleton className="h-60 w-full" /> : <VolumeChart data={volume} />}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
