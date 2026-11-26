import { ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';

import type { Account } from '@/api/types';
import { Money } from '@/components/Money';
import { formatIban } from '@/lib/format';
import { cn } from '@/lib/utils';

const GRADIENTS: Record<Account['currency'], string> = {
  AZN: 'from-indigo-600 to-violet-600',
  USD: 'from-emerald-600 to-teal-600',
  EUR: 'from-sky-600 to-blue-700',
};

export function AccountCard({ account }: { account: Account }) {
  return (
    <Link
      to={`/accounts/${account.number}`}
      className={cn(
        'group relative flex min-h-[150px] flex-col justify-between overflow-hidden rounded-xl bg-gradient-to-br p-5 text-white shadow-md transition-transform hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        GRADIENTS[account.currency],
      )}
    >
      <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-white/10" />
      <div className="flex items-start justify-between">
        <span className="rounded-md bg-white/15 px-2 py-0.5 text-xs font-semibold tracking-wide">{account.currency}</span>
        <ArrowUpRight className="h-4 w-4 opacity-60 transition-opacity group-hover:opacity-100" />
      </div>
      <div>
        <p className="text-xs uppercase tracking-wider text-white/70">Balance</p>
        <Money amount={account.balance} currency={account.currency} className="text-2xl font-semibold" />
        <p className="mt-2 font-mono text-xs tracking-wider text-white/80">{formatIban(account.number)}</p>
      </div>
    </Link>
  );
}
