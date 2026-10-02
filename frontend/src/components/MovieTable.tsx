import type { Movie, Person } from '../api/types'
import styles from './MovieTable.module.css'

interface MovieTableProps {
  movies: Movie[]
  /** True while newer data is loading; the rows shown are then the previous ones. */
  busy?: boolean
}

/**
 * Movies as a table. Presentational only: it gets typed rows and knows nothing about loading or
 * paging, so it can be replaced (e.g. by a data grid) without touching the rest of the app.
 */
export function MovieTable({ movies, busy = false }: MovieTableProps) {
  return (
    <table className={styles.table} aria-busy={busy}>
      <thead>
        <tr>
          <th scope="col">Movie</th>
          <th scope="col">Genres</th>
          <th scope="col">Rating</th>
          <th scope="col">Age limit</th>
          <th scope="col">Director</th>
          <th scope="col">Actors</th>
          <th scope="col">Synopsis</th>
        </tr>
      </thead>
      <tbody>
        {movies.map((movie) => (
          <tr key={movie.id}>
            <th scope="row">
              {movie.name} <span className={styles.year}>({movie.year})</span>
            </th>
            <td>{movie.genres.join(', ')}</td>
            <td aria-label={`${movie.rating} out of 5`}>
              <span aria-hidden="true">{'★'.repeat(movie.rating)}{'☆'.repeat(5 - movie.rating)}</span>
            </td>
            <td>{movie.ageLimit === 0 ? 'All ages' : `${movie.ageLimit}+`}</td>
            <td>{fullName(movie.director)}</td>
            <td>{movie.actors.length > 0 ? movie.actors.map(fullName).join(', ') : '—'}</td>
            <td>
              <div className={styles.synopsis}>{movie.synopsis ?? '—'}</div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function fullName(person: Person): string {
  return `${person.firstName} ${person.lastName}`
}
