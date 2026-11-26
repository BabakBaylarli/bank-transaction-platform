import { CheckCircle2, Clock, Loader2, ShieldAlert } from 'lucide-react';

import { REJECTION_MESSAGES } from '@/api/messages';
import type { Transaction } from '@/api/types';
import { Money } from '@/components/Money';
import { StatusBadge } from '@/components/StatusBadge';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { formatIban } from '@/lib/format';
import { cn } from '@/lib/utils';

const STEPS = ['Accepted', 'Fraud checks & settlement', 'Done'] as const;

/** Live view of a transfer while processing-service works on it. */
export function TransferStatus({ transaction }: { transaction: Transaction }) {
  const done = transaction.status !== 'PENDING';
  const rejected = transaction.status === 'REJECTED';
  const activeStep = done ? 2 : 1;

  return (
    <div className="space-y-5" aria-live="polite">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm text-muted-foreground">Transfer</p>
          <Money
            amount={transaction.amount}
            currency={transaction.currency}
            className="text-2xl font-semibold"
          />
          <p className="mt-1 font-mono text-xs text-muted-foreground">
            to {formatIban(transaction.targetAccount)}
          </p>
        </div>
        <StatusBadge status={transaction.status} />
      </div>

      <ol className="grid grid-cols-3 gap-2">
        {STEPS.map((label, i) => {
          const reached = i <= activeStep;
          const current = i === activeStep && !done;
          return (
            <li key={label} className="space-y-2">
              <div
                className={cn(
                  'h-1.5 rounded-full bg-muted transition-colors',
                  reached && (rejected && i === 2 ? 'bg-destructive' : 'bg-primary'),
                  reached && !rejected && done && 'bg-success',
                )}
              />
              <p
                className={cn(
                  'flex items-center gap-1 text-xs',
                  reached ? 'text-foreground' : 'text-muted-foreground',
                )}
              >
                {current && <Loader2 className="h-3 w-3 animate-spin" />}
                {label}
              </p>
            </li>
          );
        })}
      </ol>

      {transaction.status === 'PENDING' && (
        <Alert variant="info">
          <Clock className="h-4 w-4" />
          <AlertTitle>Processing…</AlertTitle>
          <AlertDescription>
            The transfer is on its way through Kafka. This usually takes under a second.
          </AlertDescription>
        </Alert>
      )}
      {transaction.status === 'COMPLETED' && (
        <Alert variant="success">
          <CheckCircle2 className="h-4 w-4" />
          <AlertTitle>Transfer completed</AlertTitle>
          <AlertDescription>The money has been moved and the balances are updated.</AlertDescription>
        </Alert>
      )}
      {rejected && transaction.rejectionReason && (
        <Alert variant="destructive">
          <ShieldAlert className="h-4 w-4" />
          <AlertTitle>{REJECTION_MESSAGES[transaction.rejectionReason].title}</AlertTitle>
          <AlertDescription>
            <p>{REJECTION_MESSAGES[transaction.rejectionReason].hint}</p>
            {transaction.rejectionDetail && (
              <p className="mt-1 text-xs text-muted-foreground">Details: {transaction.rejectionDetail}</p>
            )}
            <p className="mt-1 font-mono text-[11px] text-muted-foreground">
              Rule: {transaction.rejectionReason}
            </p>
          </AlertDescription>
        </Alert>
      )}
    </div>
  );
}
