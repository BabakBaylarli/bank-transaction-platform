import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, ArrowLeftRight, Copy } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';

import { api } from '@/api/endpoints';
import { queryKeys } from '@/api/queryKeys';
import { Money } from '@/components/Money';
import { Pagination } from '@/components/Pagination';
import { TransactionList } from '@/components/TransactionList';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAccounts } from '@/hooks/useAccounts';
import { formatDateTime, formatIban } from '@/lib/format';

const PAGE_SIZE = 10;

export default function AccountDetailPage() {
  const { number = '' } = useParams();
  const [page, setPage] = useState(0);
  const accounts = useAccounts();

  const balance = useQuery({
    queryKey: queryKeys.balance(number),
    queryFn: () => api.accounts.balance(number),
    refetchInterval: 15_000,
  });
  const criteria = { account: number, page, size: PAGE_SIZE };
  const history = useQuery({
    queryKey: queryKeys.search(criteria),
    queryFn: ({ signal }) => api.search.transactions(criteria, signal),
    placeholderData: (prev) => prev,
  });

  return (
    <>
      <Button variant="ghost" size="sm" asChild className="mb-4 -ml-2">
        <Link to="/accounts">
          <ArrowLeft /> Accounts
        </Link>
      </Button>

      <Card className="mb-6 overflow-hidden">
        <div className="flex flex-col gap-4 bg-gradient-to-br from-primary/10 to-transparent p-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-sm text-muted-foreground">Available balance</p>
            {balance.isLoading ? (
              <Skeleton className="mt-2 h-9 w-48" />
            ) : balance.data ? (
              <Money amount={balance.data.balance} currency={balance.data.currency} className="text-3xl font-semibold" />
            ) : (
              <p className="text-3xl font-semibold">—</p>
            )}
            <div className="mt-3 flex items-center gap-2">
              <span className="font-mono text-sm tracking-wider">{formatIban(number)}</span>
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                aria-label="Copy account number"
                onClick={() => navigator.clipboard?.writeText(number).then(() => toast.success('Account number copied'))}
              >
                <Copy />
              </Button>
            </div>
            {balance.data && <p className="mt-1 text-xs text-muted-foreground">As of {formatDateTime(balance.data.asOf)}</p>}
          </div>
          <Button asChild>
            <Link to={`/transfer?from=${number}`}>
              <ArrowLeftRight /> Send money
            </Link>
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Transactions</CardTitle>
          <CardDescription>Everything that touched this account</CardDescription>
        </CardHeader>
        <CardContent>
          <TransactionList hits={history.data?.content} loading={history.isLoading} myAccounts={accounts.numbers} />
          {history.data && (
            <Pagination page={history.data.page} totalPages={history.data.totalPages} total={history.data.totalElements} onPage={setPage} />
          )}
        </CardContent>
      </Card>
    </>
  );
}
