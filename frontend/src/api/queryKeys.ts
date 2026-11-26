import type { SearchCriteria } from './types';

export const queryKeys = {
  accounts: ['accounts'] as const,
  balance: (number: string) => ['accounts', number, 'balance'] as const,
  transaction: (id: string) => ['transactions', id] as const,
  search: (criteria: SearchCriteria) => ['search', criteria] as const,
  stats: (from?: string, to?: string) => ['stats', from, to] as const,
  fraudRules: ['fraud-rules'] as const,
};
