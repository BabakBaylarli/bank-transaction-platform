import type { DailyStats, SearchHit } from '@/api/types';
import { aggregate } from '@/pages/admin/aggregate';
import { buildDailyVolume } from '@/pages/DashboardPage';
import { criteriaFromParams } from '@/pages/TransactionsPage';
import { transferSchema } from '@/pages/TransferPage';

const hit = (over: Partial<SearchHit>): SearchHit => ({
  id: crypto.randomUUID(),
  sourceAccount: 'A',
  targetAccount: 'B',
  amount: 10,
  currency: 'AZN',
  status: 'COMPLETED',
  rejectionReason: null,
  rejectionDetail: null,
  description: null,
  createdAt: '2026-11-26T09:00:00Z',
  updatedAt: null,
  ...over,
});

describe('dashboard volume', () => {
  it('buckets completed transfers into the last 7 UTC days per currency', () => {
    const points = buildDailyVolume(
      [
        hit({ amount: 10 }),
        hit({ amount: 5.5, currency: 'USD' }),
        hit({ amount: 99, status: 'REJECTED' }),
        hit({ amount: 1, createdAt: '2026-11-01T00:00:00Z' }),
      ],
      new Date('2026-11-26T12:00:00Z'),
    );
    expect(points).toHaveLength(7);
    expect(points[0].day).toBe('2026-11-20');
    expect(points[6]).toEqual({ day: '2026-11-26', AZN: 10, USD: 5.5, EUR: 0 });
  });
});

describe('admin aggregation', () => {
  it('pivots stats rows and computes the rejection rate', () => {
    const rows: DailyStats[] = [
      { day: '2026-11-25', status: 'COMPLETED', currency: 'AZN', count: 3, totalAmount: 300 },
      { day: '2026-11-26', status: 'COMPLETED', currency: 'USD', count: 1, totalAmount: 50 },
      { day: '2026-11-26', status: 'REJECTED', currency: 'AZN', count: 1, totalAmount: 12000 },
    ];
    const { counts, volume, totals } = aggregate(rows);
    expect(counts.map((c) => c.day)).toEqual(['2026-11-25', '2026-11-26']);
    expect(volume[1]).toEqual({ day: '2026-11-26', AZN: 0, USD: 50, EUR: 0 });
    expect(totals.count).toBe(5);
    expect(totals.rejectionRate).toBeCloseTo(0.2);
    expect(totals.completedVolume.AZN).toBe(300);
  });
});

describe('search filters from URL', () => {
  it('makes the "to" date inclusive by sending the next midnight', () => {
    const c = criteriaFromParams(
      new URLSearchParams('status=REJECTED&minAmount=100&from=2026-11-01&to=2026-11-26'),
    );
    expect(c).toMatchObject({
      status: 'REJECTED',
      minAmount: 100,
      from: '2026-11-01T00:00:00.000Z',
      to: '2026-11-27T00:00:00.000Z',
      page: 0,
    });
  });
});

describe('transfer form schema', () => {
  const valid = { sourceAccount: 'AZ85BTPX00000000000000000001', amount: '10.50', description: '' };

  it('normalises spaces and case in the target account', () => {
    const parsed = transferSchema.parse({
      ...valid,
      targetAccount: 'az31 btpx 0000 0000 0000 0000 0003',
    });
    expect(parsed.targetAccount).toBe('AZ31BTPX00000000000000000003');
    expect(parsed.amount).toBe(10.5);
  });

  it.each([
    [{ amount: '0' }, 'Amount must be greater than 0'],
    [{ amount: '1.005' }, 'At most 2 decimals'],
    [{ targetAccount: 'hello' }, 'Enter a valid account number'],
    [{ targetAccount: 'AZ85BTPX00000000000000000001' }, 'Choose a different target account'],
  ])('rejects %o', (override, message) => {
    const result = transferSchema.safeParse({
      ...valid,
      targetAccount: 'AZ31BTPX00000000000000000003',
      ...override,
    });
    expect(result.success).toBe(false);
    expect(JSON.stringify(result.error?.issues)).toContain(message);
  });
});
