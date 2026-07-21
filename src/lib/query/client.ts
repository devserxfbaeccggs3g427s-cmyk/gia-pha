import { QueryClient } from '@tanstack/react-query';
import { MutationApiError, isRetryableMutationError } from '@/lib/api/mutations';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        networkMode: 'online',
        staleTime: 0,
        gcTime: 10 * 60_000,
        retry: (failureCount, error) => failureCount < 2 && isRetryableQueryError(error),
        retryDelay: (attempt) => Math.min(1_000 * 2 ** attempt, 10_000),
        refetchOnMount: 'always',
        refetchOnReconnect: 'always',
        refetchOnWindowFocus: 'always'
      },
      mutations: {
        // Initial request + two retries = three attempts, matching the Blob strategy.
        retry: (failureCount, error) => failureCount < 2 && isRetryableMutationError(error),
        retryDelay: (attempt) => Math.min(1_000 * 2 ** attempt, 15_000)
      }
    }
  });
}

function isRetryableQueryError(error: unknown): boolean {
  return !(error instanceof MutationApiError) || isRetryableMutationError(error);
}
