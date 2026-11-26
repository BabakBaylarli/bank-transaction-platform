import { CheckCircle2, Clock, XCircle } from 'lucide-react';

import { STATUS_LABELS } from '@/api/messages';
import type { TransactionStatus } from '@/api/types';
import { Badge } from '@/components/ui/badge';

const STYLE = {
  PENDING: { variant: 'warning', icon: Clock },
  COMPLETED: { variant: 'success', icon: CheckCircle2 },
  REJECTED: { variant: 'destructive', icon: XCircle },
} as const;

export function StatusBadge({ status }: { status: TransactionStatus }) {
  const { variant, icon: Icon } = STYLE[status];
  return (
    <Badge variant={variant}>
      <Icon className="h-3 w-3" aria-hidden />
      {STATUS_LABELS[status]}
    </Badge>
  );
}
