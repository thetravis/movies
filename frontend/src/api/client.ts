import createClient from 'openapi-fetch'
import type { paths } from './schema'

/**
 * Creates a client for the Movies API. Paths, query parameters, request bodies and responses are
 * all typed from the backend's OpenAPI contract (see `npm run generateTypes`), so a contract change
 * shows up as a compile error here.
 */
export function createApiClient(baseUrl: string) {
  return createClient<paths>({
    baseUrl,
    // Look up fetch on every call. openapi-fetch would otherwise keep the fetch that existed when
    // the client was created, and miss a fetch replaced later (e.g. by the API mock in tests).
    fetch: (request) => globalThis.fetch(request),
  })
}

/**
 * The client used by the app. Requests go to the page's own origin: in development the Vite dev
 * server proxies `/api` to the backend (see vite.config.ts). An absolute base URL is used (instead
 * of relative URLs) so that the same code also works in tests.
 */
export const apiClient = createApiClient(window.location.origin)
