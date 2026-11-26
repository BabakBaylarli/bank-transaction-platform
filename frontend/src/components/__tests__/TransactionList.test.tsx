import { render, screen } from '@testing-library/react';

import type { SearchHit } from '@/api/types';
import { TransactionList } from '@/components/TransactionList';

const MINE = 'AZ85BTPX00000000000000000001';

const hit = (over: Partial<SearchHit>): SearchHit => ({
  id: crypto.randomUUID(),
  sourceAccount: 'AZ31BTPX00000000000000000003',
  targetAccount: MINE,
  amount: 100,
  currency: 'AZN',
  status: 'COMPLETED',
  rejectionReason: null,
  rejectionDetail: null,
  description: 'Incoming',
  createdAt: '2026-11-26T10:00:00Z',
  updatedAt: null,
  ...over,
});

describe('TransactionList amounts', () => {
  it('signs completed incoming money with +', () => {
    render(<TransactionList hits={[hit({})]} loading={false} myAccounts={new Set([MINE])} />);
    expect(screen.getAllByText(/^\+AZN\s100\.00$/).length).toBeGreaterThan(0);
  });

  it('never shows a rejected transfer as money received', () => {
    render(
      <TransactionList
        hits={[hit({ status: 'REJECTED', rejectionReason: 'INSUFFICIENT_FUNDS', amount: 9000 })]}
        loading={false}
        myAccounts={new Set([MINE])}
      />,
    );
    const amounts = screen.getAllByText(/AZN\s9,000\.00/);
    amounts.forEach((el) => {
      expect(el.textContent).not.toMatch(/^[+−]/);
      expect(el).toHaveClass('line-through');
    });
  });
});
