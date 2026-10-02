import { useState } from 'react'
import { useMovies } from '../api/movies'
import { MovieTable } from './MovieTable'
import { Pagination } from './Pagination'
import styles from './MovieList.module.css'

const PAGE_SIZES = [10, 20, 50]
const DEFAULT_PAGE_SIZE = 20

/** The paged movie list: loads movies from the API and shows loading, error and empty states. */
export function MovieList() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE)
  const { data, error, isPending, isFetching, isPlaceholderData, refetch } = useMovies({ page, size })

  function changeSize(newSize: number) {
    setSize(newSize)
    setPage(0)
  }

  if (isPending) {
    return <p role="status">Loading movies…</p>
  }

  if (error && !data) {
    return (
      <div role="alert" className={styles.error}>
        <p>{error.message}</p>
        <button type="button" onClick={() => refetch()}>
          Try again
        </button>
      </div>
    )
  }

  if (data.totalItems === 0) {
    return <p>No movies yet.</p>
  }

  return (
    <section aria-label="Movies">
      {error && (
        <p role="alert" className={styles.error}>
          {error.message}
        </p>
      )}
      <MovieTable movies={data.items} busy={isFetching && isPlaceholderData} />
      <Pagination
        page={data.page}
        totalPages={data.totalPages}
        totalItems={data.totalItems}
        size={size}
        sizeOptions={PAGE_SIZES}
        onPageChange={setPage}
        onSizeChange={changeSize}
      />
    </section>
  )
}
