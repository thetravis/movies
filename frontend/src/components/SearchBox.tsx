import styles from './SearchBox.module.css'

/** Matches the `maxLength` of the `search` query parameter in the API contract. */
const MAX_SEARCH_LENGTH = 100

interface SearchBoxProps {
  value: string
  onChange: (value: string) => void
}

export function SearchBox({ value, onChange }: SearchBoxProps) {
  return (
    <input
      type="search"
      className={styles.search}
      aria-label="Search movies"
      placeholder="Search by name, synopsis, genre, actor or director"
      maxLength={MAX_SEARCH_LENGTH}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}
