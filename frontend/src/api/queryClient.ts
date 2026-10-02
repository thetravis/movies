import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './errors'

/**
 * The app's TanStack Query client. Failed requests are retried once, but only when that can help:
 * a 4xx answer (e.g. validation) would fail again in the same way.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) =>
          failureCount < 1 && (!(error instanceof ApiError) || error.isRetryable),
      },
    },
  })
}
