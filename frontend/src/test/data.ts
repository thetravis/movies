import type { Movie, MoviePage, Problem } from '../api/types'

// Test data builders. Typed from the contract: if the API changes, test data fails to compile.

let nextId = 1

export function aMovie(overrides: Partial<Movie> = {}): Movie {
  const id = nextId++
  return {
    id,
    name: `Movie ${id}`,
    year: 2000,
    genres: ['Drama'],
    ageLimit: 12,
    rating: 3,
    actors: [{ firstName: 'Actor', lastName: `${id}` }],
    director: { firstName: 'Director', lastName: `${id}` },
    synopsis: `Synopsis of movie ${id}.`,
    ...overrides,
  }
}

/** A page as the backend returns it for the given page index and size out of `totalItems` movies. */
export function aPage(items: Movie[], { page = 0, size = 20, totalItems = items.length } = {}): MoviePage {
  return { items, page, size, totalItems, totalPages: Math.ceil(totalItems / size) }
}

export function aProblem(overrides: Partial<Problem> = {}): Problem {
  return {
    type: 'about:blank',
    title: 'Internal Server Error',
    status: 500,
    detail: null,
    instance: '/api/movies',
    errors: null,
    ...overrides,
  }
}
