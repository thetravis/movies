import styles from './Pagination.module.css'

interface PaginationProps {
  /** Zero-based page index, as in the API. */
  page: number
  totalPages: number
  totalItems: number
  size: number
  /** Page sizes offered in the selector. */
  sizeOptions: readonly number[]
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

/** Previous/next paging with "page x of y", the total count and a page size selector. */
export function Pagination({ page, totalPages, totalItems, size, sizeOptions, onPageChange, onSizeChange }: PaginationProps) {
  const isFirst = page <= 0
  const isLast = page >= totalPages - 1

  return (
    <nav className={styles.pagination} aria-label="Pagination">
      <button type="button" onClick={() => onPageChange(page - 1)} disabled={isFirst}>
        ← Previous
      </button>
      <span aria-live="polite">
        Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
      </span>
      <button type="button" onClick={() => onPageChange(page + 1)} disabled={isLast}>
        Next →
      </button>
      <span className={styles.total}>
        {totalItems} {totalItems === 1 ? 'movie' : 'movies'}
      </span>
      <label className={styles.size}>
        Per page{' '}
        <select value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
          {sizeOptions.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
    </nav>
  )
}
