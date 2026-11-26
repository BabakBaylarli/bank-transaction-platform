import { useQuery } from '@tanstack/react-query';
import { BarChart3, ExternalLink, ShieldAlert } from 'lucide-react';
import { useMemo, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { api } from '@/api/endpoints';
import { STATUS_LABELS } from '@/api/messages';
import { queryKeys } from '@/api/queryKeys';
import { CURRENCIES, STATUSES } from '@/api/types';
import { CURRENCY_COLORS } from '@/components/charts/VolumeChart';
import { EmptyState } from '@/components/EmptyState';
import { Money } from '@/components/Money';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { config } from '@/config';
import { formatDay } from '@/lib/format';
import { cn } from '@/lib/utils';

import { aggregate } from './admin/aggregate';

const RANGES = [7, 30, 90] as const;
const STATUS_COLORS = {
  COMPLETED: 'hsl(var(--chart-2))',
  REJECTED: 'hsl(var(--chart-4))',
  PENDING: 'hsl(var(--chart-3))',
} as const;

const tooltipStyle = {
  background: 'hsl(var(--popover))',
  border: '1px solid hsl(var(--border))',
  borderRadius: 8,
  color: 'hsl(var(--popover-foreground))',
};
const axis = { tickLine: false, axisLine: false, fontSize: 12, stroke: 'hsl(var(--muted-foreground))' };

export default function AdminPage() {
  const [days, setDays] = useState<(typeof RANGES)[number]>(7);
  const from = useMemo(() => new Date(Date.now() - days * 86_400_000).toISOString(), [days]);

  const stats = useQuery({
    queryKey: queryKeys.stats(from),
    queryFn: () => api.search.stats(from),
    refetchInterval: 15_000,
  });
  const rules = useQuery({ queryKey: queryKeys.fraudRules, queryFn: api.processing.fraudRules });
  const data = useMemo(() => aggregate(stats.data ?? []), [stats.data]);

  return (
    <>
      <PageHeader
        title="Admin overview"
        description="Aggregations computed by Elasticsearch across all customers."
        actions={
          <Button variant="outline" asChild>
            <a href={config.kibanaUrl} target="_blank" rel="noreferrer">
              Open Kibana <ExternalLink />
            </a>
          </Button>
        }
      />

      <div className="mb-6 inline-flex rounded-lg border p-1" role="group" aria-label="Time range">
        {RANGES.map((r) => (
          <Button
            key={r}
            size="sm"
            variant="ghost"
            aria-pressed={days === r}
            className={cn(days === r && 'bg-accent text-accent-foreground')}
            onClick={() => setDays(r)}
          >
            {r} days
          </Button>
        ))}
      </div>

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Kpi title="Transactions" loading={stats.isLoading} value={data.totals.count.toLocaleString()} />
        <Kpi
          title="Rejection rate"
          loading={stats.isLoading}
          value={`${(data.totals.rejectionRate * 100).toFixed(1)}%`}
          hint={`${data.totals.byStatus.REJECTED} rejected`}
        />
        <Card className="sm:col-span-2">
          <CardHeader className="pb-2">
            <CardDescription>Completed volume</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-x-6 gap-y-1">
            {stats.isLoading ? (
              <Skeleton className="h-7 w-full" />
            ) : (
              CURRENCIES.map((c) => (
                <Money key={c} amount={data.totals.completedVolume[c]} currency={c} className="text-lg" />
              ))
            )}
          </CardContent>
        </Card>
      </div>

      {!stats.isLoading && data.totals.count === 0 ? (
        <EmptyState
          icon={BarChart3}
          title="No transactions in this period"
          description="Make a few transfers and come back."
        />
      ) : (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Transactions per day</CardTitle>
              <CardDescription>By final status</CardDescription>
            </CardHeader>
            <CardContent>
              {stats.isLoading ? (
                <Skeleton className="h-64" />
              ) : (
                <div className="h-64">
                  <ResponsiveContainer>
                    <BarChart data={data.counts} margin={{ left: -16, right: 4 }}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                      <XAxis dataKey="day" tickFormatter={formatDay} {...axis} />
                      <YAxis allowDecimals={false} {...axis} />
                      <Tooltip
                        contentStyle={tooltipStyle}
                        cursor={{ fill: 'hsl(var(--muted))' }}
                        labelFormatter={(d: string) => formatDay(d)}
                      />
                      <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} />
                      {STATUSES.map((s) => (
                        <Bar
                          key={s}
                          dataKey={s}
                          name={STATUS_LABELS[s]}
                          stackId="s"
                          fill={STATUS_COLORS[s]}
                          maxBarSize={48}
                        />
                      ))}
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Completed volume per day</CardTitle>
              <CardDescription>Per currency</CardDescription>
            </CardHeader>
            <CardContent>
              {stats.isLoading ? (
                <Skeleton className="h-64" />
              ) : (
                <div className="h-64">
                  <ResponsiveContainer>
                    <LineChart data={data.volume} margin={{ left: -4, right: 8 }}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                      <XAxis dataKey="day" tickFormatter={formatDay} {...axis} />
                      <YAxis {...axis} width={64} />
                      <Tooltip contentStyle={tooltipStyle} labelFormatter={(d: string) => formatDay(d)} />
                      <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} />
                      {CURRENCIES.map((c) => (
                        <Line
                          key={c}
                          dataKey={c}
                          stroke={CURRENCY_COLORS[c]}
                          strokeWidth={2}
                          dot={{ r: 3 }}
                          type="monotone"
                        />
                      ))}
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldAlert className="h-4 w-4" /> Active fraud rules
          </CardTitle>
          <CardDescription>Served by processing-service</CardDescription>
        </CardHeader>
        <CardContent>
          {rules.data ? (
            <dl className="grid gap-4 sm:grid-cols-3">
              <Rule label="Max single transfer" value={rules.data.maxSingleAmount.toLocaleString()} />
              <Rule label="Daily outgoing limit" value={rules.data.dailyLimit.toLocaleString()} />
              <Rule label="Max transfers / minute" value={String(rules.data.maxTransactionsPerMinute)} />
            </dl>
          ) : (
            <Skeleton className="h-14" />
          )}
        </CardContent>
      </Card>
    </>
  );
}

function Kpi({
  title,
  value,
  hint,
  loading,
}: {
  title: string;
  value: string;
  hint?: string;
  loading: boolean;
}) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{title}</CardDescription>
      </CardHeader>
      <CardContent>
        {loading ? (
          <Skeleton className="h-8 w-24" />
        ) : (
          <p className="tabular text-2xl font-semibold">{value}</p>
        )}
        {hint && !loading && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  );
}

function Rule({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border p-4">
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="tabular mt-1 text-xl font-semibold">{value}</dd>
    </div>
  );
}
