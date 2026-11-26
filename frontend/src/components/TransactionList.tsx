import { ArrowDownLeft, ArrowUpRight, Receipt } from 'lucide-react';

import { REJECTION_MESSAGES } from '@/api/messages';
import type { SearchHit } from '@/api/types';
import { EmptyState } from '@/components/EmptyState';
import { Money } from '@/components/Money';
import { StatusBadge } from '@/components/StatusBadge';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDateTime, shortIban } from '@/lib/format';
import { cn } from '@/lib/utils';

interface Props {
  hits: SearchHit[] | undefined;
  loading: boolean;
  /** account numbers of the current user - used to show incoming vs outgoing */
  myAccounts?: Set<string>;
  emptyText?: string;
  /** hide the From → To column (for narrow cards such as the dashboard) */
  compact?: boolean;
}

function direction(hit: SearchHit, mine?: Set<string>): 'in' | 'out' | undefined {
  if (!mine) return undefined;
  if (mine.has(hit.sourceAccount)) return 'out';
  if (mine.has(hit.targetAccount)) return 'in';
  return undefined;
}

/** Only completed transfers moved money: pending/rejected amounts get no +/− and are muted. */
function AmountCell({ hit, mine, className }: { hit: SearchHit; mine?: Set<string>; className?: string }) {
  const moved = hit.status === 'COMPLETED';
  return (
    <Money
      amount={hit.amount}
      currency={hit.currency}
      sign={moved ? direction(hit, mine) : undefined}
      className={cn(
        className,
        !moved && 'text-muted-foreground',
        hit.status === 'REJECTED' && 'line-through',
      )}
    />
  );
}

/** Table on desktop, stacked cards on mobile. */
export function TransactionList({
  hits,
  loading,
  myAccounts,
  emptyText = 'No transactions yet',
  compact = false,
}: Props) {
  if (loading) {
    return (
      <div className="space-y-2" aria-busy="true">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    );
  }
  if (!hits?.length) {
    return (
      <EmptyState
        icon={Receipt}
        title={emptyText}
        description="Transfers appear here a moment after they are processed."
      />
    );
  }

  return (
    <>
      <div className="hidden lg:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Description</TableHead>
              {!compact && <TableHead className="hidden xl:table-cell">From → To</TableHead>}
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Amount</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {hits.map((hit) => (
              <TableRow key={hit.id}>
                <TableCell className="whitespace-nowrap text-muted-foreground">
                  {formatDateTime(hit.createdAt)}
                </TableCell>
                <TableCell>
                  <div className="font-medium">{hit.description || 'Transfer'}</div>
                  {hit.rejectionReason && (
                    <div className="text-xs text-destructive">
                      {REJECTION_MESSAGES[hit.rejectionReason].title}
                    </div>
                  )}
                </TableCell>
                {!compact && (
                  <TableCell className="hidden whitespace-nowrap font-mono text-xs text-muted-foreground xl:table-cell">
                    {shortIban(hit.sourceAccount)} → {shortIban(hit.targetAccount)}
                  </TableCell>
                )}
                <TableCell>
                  <StatusBadge status={hit.status} />
                </TableCell>
                <TableCell className="whitespace-nowrap text-right">
                  <AmountCell hit={hit} mine={myAccounts} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <ul className="divide-y lg:hidden">
        {hits.map((hit) => {
          const dir = direction(hit, myAccounts);
          return (
            <li key={hit.id} className="flex items-center gap-3 py-3">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted">
                {dir === 'in' ? (
                  <ArrowDownLeft className="h-4 w-4 text-success" />
                ) : (
                  <ArrowUpRight className="h-4 w-4" />
                )}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{hit.description || 'Transfer'}</p>
                <p className="text-xs text-muted-foreground">{formatDateTime(hit.createdAt)}</p>
              </div>
              <div className="flex flex-col items-end gap-1">
                <AmountCell hit={hit} mine={myAccounts} className="text-sm" />
                <StatusBadge status={hit.status} />
              </div>
            </li>
          );
        })}
      </ul>
    </>
  );
}
