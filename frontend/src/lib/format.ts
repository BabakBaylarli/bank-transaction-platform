import type { CurrencyCode } from '@/api/types';

const formatters = new Map<string, Intl.NumberFormat>();

export function formatMoney(amount: number | string, currency: CurrencyCode): string {
  const key = currency;
  let f = formatters.get(key);
  if (!f) {
    f = new Intl.NumberFormat('en-US', { style: 'currency', currency, minimumFractionDigits: 2 });
    formatters.set(key, f);
  }
  return f.format(Number(amount));
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
}

export function formatDay(isoDay: string): string {
  return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' }).format(new Date(isoDay));
}

/** AZ85BTPX00000000000000000001 -> AZ85 BTPX 0000 0000 0000 0000 0001 */
export function formatIban(iban: string): string {
  return iban.replace(/(.{4})/g, '$1 ').trim();
}

export function shortIban(iban: string): string {
  return `${iban.slice(0, 4)} •••• ${iban.slice(-4)}`;
}
