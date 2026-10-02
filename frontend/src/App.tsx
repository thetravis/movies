import { useState } from 'react'
import type { Movie } from './api/types'
import { AddMovieDialog } from './components/AddMovieDialog'
import { MovieList } from './components/MovieList'
import styles from './App.module.css'

export default function App() {
  const [added, setAdded] = useState<Movie | null>(null)

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <h1>Movies</h1>
        <AddMovieDialog onCreated={setAdded} />
      </header>
      <main>
        <div role="status" className={styles.notice}>
          {added && `Added "${added.name}" (${added.year}).`}
        </div>
        <MovieList />
      </main>
    </div>
  )
}
