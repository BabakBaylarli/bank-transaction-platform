import { useQuery } from '@tanstack/react-query';
import { Filter, RotateCcw, SlidersHorizontal } from 'lucide-react';
import { useMemo, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';

import { api } from '@/api/endpoints';
import { STATUS_LABELS } from '@/api/messages';
import { queryKeys } from '@/api/queryKeys';
import {
  CURRENCIES,
  STATUSES,
  type CurrencyCode,
  type SearchCriteria,
  type TransactionStatus,
} from '@/api/types';
import { PageHeader } from '@/components/PageHeader';
import { Pagination } from '@/components/Pagination';
import { TransactionList } from '@/components/TransactionList';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useAccounts } from '@/hooks/useAccounts';
import { cn } from '@/lib/utils';

const PAGE_SIZE = 15;
const ALL = 'ALL';

/** URL query string <-> search criteria. Filters live in the URL so results are shareable and survive reloads. */
export function criteriaFromParams(params: URLSearchParams): SearchCriteria {
  const num = (k: string) => (params.get(k) ? Number(params.get(k)) : undefined);
  const day = (k: string, endOfDay = false) => {
    const v = params.get(k);
    if (!v) return undefined;
    const d = new Date(`${v}T00:00:00Z`);
    if (endOfDay) d.setUTCDate(d.getUTCDate() + 1); // "to" is exclusive on the backend
    return d.toISOString();
  };
  return {
    q: params.get('q') || undefined,
    status: (params.get('status') as TransactionStatus) || undefined,
    currency: (params.get('currency') as CurrencyCode) || undefined,
    account: params.get('account') || undefined,
    minAmount: num('minAmount'),
    maxAmount: num('maxAmount'),
    from: day('from'),
    to: day('to', true),
    page: num('page') ?? 0,
    size: PAGE_SIZE,
  };
}

export default function TransactionsPage() {
  const [params, setParams] = useSearchParams();
  const accounts = useAccounts();
  const criteria = useMemo(() => criteriaFromParams(params), [params]);
  // on small screens the filter form is collapsed behind a button so results stay visible
  const [filtersOpen, setFiltersOpen] = useState(false);
  const activeFilters = [...params.keys()].filter((k) => k !== 'page').length;

  const results = useQuery({
    queryKey: queryKeys.search(criteria),
    queryFn: ({ signal }) => api.search.transactions(criteria, signal),
    placeholderData: (prev) => prev,
  });

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of ['q', 'account', 'minAmount', 'maxAmount', 'from', 'to']) {
      const value = String(form.get(key) ?? '').trim();
      if (value) next.set(key, value);
    }
    for (const key of ['status', 'currency']) {
      const value = String(form.get(key) ?? '');
      if (value && value !== ALL) next.set(key, value);
    }
    setParams(next);
    setFiltersOpen(false);
  };

  const setPage = (page: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(page));
    setParams(next);
  };

  return (
    <>
      <PageHeader
        title="Transactions"
        description="Full-text search over all your transfers, powered by Elasticsearch."
      />

      <Button
        variant="outline"
        className="mb-4 w-full lg:hidden"
        aria-expanded={filtersOpen}
        onClick={() => setFiltersOpen((open) => !open)}
      >
        <SlidersHorizontal /> Filters{activeFilters ? ` (${activeFilters})` : ''}
      </Button>

      <Card className={cn('mb-6 lg:block', !filtersOpen && 'hidden')}>
        <CardContent className="pt-5">
          <form
            key={params.toString()}
            onSubmit={applyFilters}
            className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
            aria-label="Filters"
          >
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="q">Search</Label>
              <Input
                id="q"
                name="q"
                placeholder="Description, account number…"
                defaultValue={params.get('q') ?? ''}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="status">Status</Label>
              <Select name="status" defaultValue={params.get('status') ?? ALL}>
                <SelectTrigger id="status">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All statuses</SelectItem>
                  {STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {STATUS_LABELS[s]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="currency">Currency</Label>
              <Select name="currency" defaultValue={params.get('currency') ?? ALL}>
                <SelectTrigger id="currency">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All currencies</SelectItem>
                  {CURRENCIES.map((c) => (
                    <SelectItem key={c} value={c}>
                      {c}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="minAmount">Min amount</Label>
              <Input
                id="minAmount"
                name="minAmount"
                type="number"
                min={0}
                step="0.01"
                defaultValue={params.get('minAmount') ?? ''}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="maxAmount">Max amount</Label>
              <Input
                id="maxAmount"
                name="maxAmount"
                type="number"
                min={0}
                step="0.01"
                defaultValue={params.get('maxAmount') ?? ''}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="from">From date</Label>
              <Input id="from" name="from" type="date" defaultValue={params.get('from') ?? ''} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="to">To date</Label>
              <Input id="to" name="to" type="date" defaultValue={params.get('to') ?? ''} />
            </div>
            <div className="flex gap-2 sm:col-span-2 lg:col-span-4 lg:justify-end">
              <Button type="button" variant="ghost" onClick={() => setParams(new URLSearchParams())}>
                <RotateCcw /> Reset
              </Button>
              <Button type="submit">
                <Filter /> Apply filters
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-5">
          {results.data && (
            <p className="mb-3 text-sm text-muted-foreground" aria-live="polite">
              {results.data.totalElements} result{results.data.totalElements === 1 ? '' : 's'}
              {results.isFetching && ' · updating…'}
            </p>
          )}
          <TransactionList
            hits={results.data?.content}
            loading={results.isLoading}
            myAccounts={accounts.numbers}
            emptyText="No transactions match these filters"
          />
          {results.data && (
            <Pagination
              page={results.data.page}
              totalPages={results.data.totalPages}
              total={results.data.totalElements}
              onPage={setPage}
            />
          )}
        </CardContent>
      </Card>
    </>
  );
}
