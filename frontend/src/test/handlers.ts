import { delay, http, HttpResponse } from 'msw'
import type { Movie } from '../api/types'
import { aMovie, aPage } from './data'
import { server } from './server'

/**
 * Serves `movies` from a mock `GET /api/movies`, paged by `page` and `size` like the backend, and
 * filtered by `search` (a simple case-insensitive match on the name is enough for frontend tests).
 * Returns the requested URLs, so tests can check what the app asked for.
 */
export function serveMovies(movies: Movie[], { latency = 0 } = {}) {
  const requests: URL[] = []
  server.use(
    http.get('*/api/movies', async ({ request }) => {
      const url = new URL(request.url)
      requests.push(url)
      const search = url.searchParams.get('search')?.toLowerCase()
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 20)
      const matching = search ? movies.filter((movie) => movie.name.toLowerCase().includes(search)) : movies
      await delay(latency)
      const items = matching.slice(page * size, (page + 1) * size)
      return HttpResponse.json(aPage(items, { page, size, totalItems: matching.length }))
    }),
  )
  return requests
}

/** `count` movies named "Movie 01", "Movie 02", ... */
export function manyMovies(count: number): Movie[] {
  return Array.from({ length: count }, (_, i) => aMovie({ name: `Movie ${String(i + 1).padStart(2, '0')}` }))
}
