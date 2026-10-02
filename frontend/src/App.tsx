import { useState } from 'react'
import type { Movie } from './api/types'
import { MovieForm } from './components/MovieForm'
import { MovieList } from './components/MovieList'
import styles from './App.module.css'

export default function App() {
  const [adding, setAdding] = useState(false)
  const [added, setAdded] = useState<Movie | null>(null)

  function startAdding() {
    setAdded(null)
    setAdding(true)
  }

  function movieAdded(movie: Movie) {
    setAdded(movie)
    setAdding(false)
  }

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <h1>Movies</h1>
        {!adding && (
          <button type="button" onClick={startAdding}>
            Add a movie
          </button>
        )}
      </header>
      <main>
        <div role="status" className={styles.notice}>
          {added && `Added "${added.name}" (${added.year}).`}
        </div>
        {adding && <MovieForm onCreated={movieAdded} onCancel={() => setAdding(false)} />}
        <MovieList />
      </main>
    </div>
  )
}
