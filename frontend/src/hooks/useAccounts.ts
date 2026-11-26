import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { api } from '@/api/endpoints';
import { queryKeys } from '@/api/queryKeys';

export function useAccounts() {
  const query = useQuery({ queryKey: queryKeys.accounts, queryFn: api.accounts.mine });
  const numbers = useMemo(() => new Set((query.data ?? []).map((a) => a.number)), [query.data]);
  return { ...query, numbers };
}
