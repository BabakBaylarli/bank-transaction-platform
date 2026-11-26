import { render, screen } from '@testing-library/react';

import type { Transaction } from '@/api/types';
import { TransferStatus } from '@/components/TransferStatus';

const base: Transaction = {
  id: 'tx-1',
  sourceAccount: 'AZ85BTPX00000000000000000001',
  targetAccount: 'AZ31BTPX00000000000000000003',
  amount: 12000,
  currency: 'AZN',
  description: null,
  status: 'PENDING',
  rejectionReason: null,
  rejectionDetail: null,
  createdAt: '2026-11-26T10:00:00Z',
  updatedAt: '2026-11-26T10:00:00Z',
};

describe('TransferStatus', () => {
  it('shows a processing hint while pending', () => {
    render(<TransferStatus transaction={base} />);
    expect(screen.getByText('Processing…')).toBeInTheDocument();
  });

  it('explains which fraud rule rejected the transfer', () => {
    render(
      <TransferStatus
        transaction={{
          ...base,
          status: 'REJECTED',
          rejectionReason: 'AMOUNT_LIMIT_EXCEEDED',
          rejectionDetail: 'Amount 12000.00 exceeds the single-transfer limit of 10000.00',
        }}
      />,
    );
    expect(screen.getByText('Single transfer limit exceeded')).toBeInTheDocument();
    expect(screen.getByText(/exceeds the single-transfer limit/)).toBeInTheDocument();
    expect(screen.getByText('Rule: AMOUNT_LIMIT_EXCEEDED')).toBeInTheDocument();
    expect(screen.queryByText('Processing…')).not.toBeInTheDocument();
  });

  it('confirms completion', () => {
    render(<TransferStatus transaction={{ ...base, status: 'COMPLETED', amount: 42.5 }} />);
    expect(screen.getByText('Transfer completed')).toBeInTheDocument();
  });
});
