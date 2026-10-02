import type { Problem } from './types'

/**
 * An error from a Movies API call.
 *
 * - `problem` is the backend's error body (Problem Details) when there is one. The contract
 *   guarantees every field is present, with `detail` and `errors` possibly `null`.
 * - `status` is `null` when the request never got a response (backend not running, network down).
 */
export class ApiError extends Error {
  readonly status: number | null
  readonly problem: Problem | null

  constructor(message: string, status: number | null, problem: Problem | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** The request did not reach the backend, or the backend failed: trying again may help. */
  get isRetryable(): boolean {
    return this.status === null || this.status >= 500
  }
}

const NETWORK_ERROR_MESSAGE = 'Could not connect to the server. Is the backend running?'

/** Converts the `error` and `response` of an openapi-fetch call into an {@link ApiError}. */
export function toApiError(error: unknown, response: Response): ApiError {
  if (isProblem(error)) {
    return new ApiError(error.detail ?? error.title, response.status, error)
  }
  // No Problem body, e.g. the dev server's proxy answering because the backend is down.
  const message = response.status >= 500 ? NETWORK_ERROR_MESSAGE : `Request failed (HTTP ${response.status}).`
  return new ApiError(message, response.status, null)
}

/** Converts an error thrown by `fetch` itself (no response at all) into an {@link ApiError}. */
export function toNetworkError(cause: unknown): ApiError {
  const error = new ApiError(NETWORK_ERROR_MESSAGE, null, null)
  error.cause = cause
  return error
}

function isProblem(value: unknown): value is Problem {
  return typeof value === 'object' && value !== null && 'title' in value && 'status' in value
}
