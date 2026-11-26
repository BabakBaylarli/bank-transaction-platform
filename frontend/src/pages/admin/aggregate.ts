import type { CurrencyCode, DailyStats, TransactionStatus } from '@/api/types';

export interface CountPoint {
  day: string;
  PENDING: number;
  COMPLETED: number;
  REJECTED: number;
}

export interface VolumeByCurrencyPoint {
  day: string;
  AZN: number;
  USD: number;
  EUR: number;
}

export interface Totals {
  count: number;
  byStatus: Record<TransactionStatus, number>;
  completedVolume: Record<CurrencyCode, number>;
  rejectionRate: number;
}

/** Pivots the flat (day, status, currency) rows of /stats into chart series. */
export function aggregate(rows: DailyStats[]) {
  const counts = new Map<string, CountPoint>();
  const volume = new Map<string, VolumeByCurrencyPoint>();
  const totals: Totals = {
    count: 0,
    byStatus: { PENDING: 0, COMPLETED: 0, REJECTED: 0 },
    completedVolume: { AZN: 0, USD: 0, EUR: 0 },
    rejectionRate: 0,
  };

  for (const row of rows) {
    const c = counts.get(row.day) ?? { day: row.day, PENDING: 0, COMPLETED: 0, REJECTED: 0 };
    c[row.status] += row.count;
    counts.set(row.day, c);

    totals.count += row.count;
    totals.byStatus[row.status] += row.count;

    if (row.status === 'COMPLETED') {
      const v = volume.get(row.day) ?? { day: row.day, AZN: 0, USD: 0, EUR: 0 };
      v[row.currency] += Number(row.totalAmount);
      volume.set(row.day, v);
      totals.completedVolume[row.currency] += Number(row.totalAmount);
    }
  }
  const decided = totals.byStatus.COMPLETED + totals.byStatus.REJECTED;
  totals.rejectionRate = decided ? totals.byStatus.REJECTED / decided : 0;

  const byDay = <T extends { day: string }>(m: Map<string, T>) =>
    [...m.values()].sort((a, b) => a.day.localeCompare(b.day));
  return { counts: byDay(counts), volume: byDay(volume), totals };
}
