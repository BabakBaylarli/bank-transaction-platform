import type { RejectionReason, TransactionStatus } from './types';

/** Customer-facing explanation of each fraud / ledger rule. */
export const REJECTION_MESSAGES: Record<RejectionReason, { title: string; hint: string }> = {
  AMOUNT_LIMIT_EXCEEDED: {
    title: 'Single transfer limit exceeded',
    hint: 'The amount is above the per-transfer limit. Split it into smaller transfers.',
  },
  DAILY_LIMIT_EXCEEDED: {
    title: 'Daily limit reached',
    hint: 'This account has reached its daily outgoing limit. Try again tomorrow (UTC).',
  },
  VELOCITY_LIMIT_EXCEEDED: {
    title: 'Too many transfers in a short time',
    hint: 'Our fraud protection paused this account briefly. Wait a minute and try again.',
  },
  INSUFFICIENT_FUNDS: {
    title: 'Insufficient funds',
    hint: 'The source account balance is lower than the transfer amount.',
  },
  ACCOUNT_NOT_FOUND: {
    title: 'Account not found',
    hint: 'The source or target account no longer exists.',
  },
  CURRENCY_MISMATCH: {
    title: 'Currency mismatch',
    hint: 'Both accounts must use the same currency as the transfer.',
  },
};

export const STATUS_LABELS: Record<TransactionStatus, string> = {
  PENDING: 'Pending',
  COMPLETED: 'Completed',
  REJECTED: 'Rejected',
};
