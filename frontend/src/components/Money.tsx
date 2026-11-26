import type { CurrencyCode } from '@/api/types';
import { formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

/** Money is formatted with Intl and tabular numbers so columns line up. */
export function Money({
  amount,
  currency,
  sign,
  className,
}: {
  amount: number;
  currency: CurrencyCode;
  sign?: 'in' | 'out';
  className?: string;
}) {
  return (
    <span
      className={cn(
        'tabular font-medium',
        sign === 'in' && 'text-success',
        sign === 'out' && 'text-foreground',
        className,
      )}
    >
      {sign === 'in' ? '+' : sign === 'out' ? '−' : ''}
      {formatMoney(amount, currency)}
    </span>
  );
}
