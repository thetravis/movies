import { useEffect, useState } from 'react'
import { useMovies } from '../api/movies'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useMovieListParams } from '../hooks/useMovieListParams'
import { MovieTable } from './MovieTable'
import { Pagination } from './Pagination'
import { SearchBox } from './SearchBox'
import styles from './MovieList.module.css'

const PAGE_SIZES = [10, 20, 50]
/** Module-level constant: the URL hook's Back/Forward listener depends on it. */
const PARAMS_OPTIONS = { sizes: PAGE_SIZES, defaultSize: 20 }
/** How long typing must pause before the search is sent. */
const SEARCH_DELAY_MS = 300

/**
 * The movie list: search, paging and the loading, error and empty states. The search term, page
 * and page size live in the URL (see {@link useMovieListParams}).
 */
export function MovieList() {
  const { params, update } = useMovieListParams(PARAMS_OPTIONS)
  const [searchInput, setSearchInput] = useState(params.search)
  const debouncedSearch = useDebouncedValue(searchInput, SEARCH_DELAY_MS)

  // A new search term (once typing pauses) starts from the first page. Replaces the history entry:
  // Back shouldn't step through every word typed.
  useEffect(() => {
    if (debouncedSearch !== params.search) {
      update({ search: debouncedSearch, page: 0 }, 'replace')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to the typed term
  }, [debouncedSearch])

  // The URL changed from outside (Back/Forward): show its term in the search box.
  useEffect(() => {
    if (params.search !== debouncedSearch) {
      setSearchInput(params.search)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to the URL's term
  }, [params.search])

  const search = params.search.trim()
  const { data, error, isPending, isFetching, isPlaceholderData, refetch } = useMovies({
    search: search || undefined,
    page: params.page,
    size: params.size,
  })

  return (
    <section aria-label="Movies">
      <SearchBox value={searchInput} onChange={setSearchInput} />
      {renderContent()}
    </section>
  )

  function renderContent() {
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
      return <p>{search ? `No movies match "${search}".` : 'No movies yet.'}</p>
    }

    if (data.items.length === 0) {
      // E.g. an old link to a page that no longer exists.
      return (
        <p>
          There are no movies on page {params.page + 1}.{' '}
          <button type="button" onClick={() => update({ page: 0 }, 'push')}>
            Go to the first page
          </button>
        </p>
      )
    }

    return (
      <>
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
          size={params.size}
          sizeOptions={PAGE_SIZES}
          onPageChange={(page) => update({ page }, 'push')}
          onSizeChange={(size) => update({ size, page: 0 }, 'push')}
        />
      </>
    )
  }
}
