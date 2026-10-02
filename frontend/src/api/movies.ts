import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import { ApiError, toApiError, toNetworkError } from './errors'
import type { ListMoviesParams, MoviePage } from './types'

/** TanStack Query keys for movie data; one place, so invalidation can't miss a key. */
export const movieKeys = {
  all: ['movies'] as const,
  list: (params: ListMoviesParams) => [...movieKeys.all, 'list', params] as const,
}

/** `GET /api/movies`. Throws an {@link ApiError} on failure. */
export async function fetchMovies(params: ListMoviesParams, signal?: AbortSignal): Promise<MoviePage> {
  let result
  try {
    result = await apiClient.GET('/api/movies', { params: { query: params }, signal })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause // cancelled by TanStack Query, not a failure
    }
    throw toNetworkError(cause)
  }
  if (result.data === undefined) {
    throw toApiError(result.error, result.response)
  }
  return result.data
}

/**
 * One page of movies. While the next page loads, the previous one stays available
 * (`placeholderData`), so the list doesn't flash empty when paging.
 */
export function useMovies(params: ListMoviesParams) {
  return useQuery<MoviePage, ApiError>({
    queryKey: movieKeys.list(params),
    queryFn: ({ signal }) => fetchMovies(params, signal),
    placeholderData: keepPreviousData,
  })
}
