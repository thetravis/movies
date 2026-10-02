import { MovieList } from './components/MovieList'
import styles from './App.module.css'

export default function App() {
  return (
    <div className={styles.app}>
      <header>
        <h1>Movies</h1>
      </header>
      <main>
        <MovieList />
      </main>
    </div>
  )
}
