import { render, screen } from '@testing-library/react';

import { StatusBadge } from '@/components/StatusBadge';

describe('StatusBadge', () => {
  it.each([
    ['PENDING', 'Pending'],
    ['COMPLETED', 'Completed'],
    ['REJECTED', 'Rejected'],
  ] as const)('renders %s as "%s"', (status, label) => {
    render(<StatusBadge status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
