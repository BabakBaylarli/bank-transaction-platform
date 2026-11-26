import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

import { ApiError } from '@/api/client';

function notify(error: unknown) {
  if (error instanceof ApiError) {
    // 401 is handled by the auth layer (redirect to login), no toast spam
    if (error.status === 401) return;
    toast.error(error.problem.title ?? 'Request failed', {
      description: error.userMessage,
      id: error.problem.correlationId,
    });
  } else if (error instanceof Error) {
    toast.error('Something went wrong', { description: error.message });
  }
}

/**
 * Global error handling: every failed query or mutation shows the server's ProblemDetail as a toast.
 * A mutation can opt out with `meta: { silent: true }` when it renders the error inline instead.
 */
export function createQueryClient() {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (!query.meta?.silent) notify(error);
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, _vars, _ctx, mutation) => {
        if (!mutation.meta?.silent) notify(error);
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 10_000,
        refetchOnWindowFocus: false,
        retry: (count, error) => !(error instanceof ApiError && error.status > 0 && error.status < 500) && count < 2,
      },
    },
  });
}
