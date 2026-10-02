import type { components, operations } from './schema'

// Short names for the types generated from the OpenAPI contract. Import API types from here rather
// than from the generated schema, so components don't depend on the generated file's structure.

type Schemas = components['schemas']

/** A stored movie, as returned by the API. */
export type Movie = Schemas['Movie']

/** A movie to be added (`POST /api/movies`). */
export type NewMovie = Schemas['NewMovie']

/** One page of movies (`GET /api/movies`). */
export type MoviePage = Schemas['MoviePage']

/** An actor or a director. */
export type Person = Schemas['Person']

/** Error response in Problem Details format (RFC 9457). */
export type Problem = Schemas['Problem']

/** A field-level validation error inside a {@link Problem}. */
export type FieldError = Schemas['FieldError']

/** Query parameters of `GET /api/movies`: `search`, `page`, `size`. */
export type ListMoviesParams = NonNullable<operations['listMovies']['parameters']['query']>
