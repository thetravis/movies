import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { aMovie } from '../test/data'
import { manyMovies, serveMovies } from '../test/handlers'
import { renderWithQueryClient } from '../test/render'
import { MovieList } from './MovieList'

const MOVIES = [
  aMovie({ name: 'Vertigo' }),
  aMovie({ name: 'Drive' }),
  aMovie({ name: 'Blade Runner 2049' }),
  aMovie({ name: 'Raiders of the Lost Ark' }),
]

function searchBox() {
  return screen.getByRole('searchbox', { name: 'Search movies' })
}

function url() {
  return window.location.pathname + window.location.search
}

/** Simulates the browser's Back button (jsdom's history.back() doesn't fire popstate reliably). */
function goBack(toUrl: string) {
  act(() => {
    window.history.replaceState(null, '', toUrl)
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
}

describe('MovieList search', () => {
  it('searches once typing pauses, and only for the final term', async () => {
    const user = userEvent.setup()
    const requests = serveMovies(MOVIES)
    renderWithQueryClient(<MovieList />)
    await screen.findByText('Vertigo')

    await user.type(searchBox(), 'drive')

    expect(await screen.findByText('1 movie')).toBeInTheDocument()
    expect(screen.getByText('Drive')).toBeInTheDocument()
    expect(screen.queryByText('Vertigo')).not.toBeInTheDocument()
    // One request for the initial list and one for "drive" – none for "d", "dr", ...
    expect(requests.map((request) => request.searchParams.get('search'))).toEqual([null, 'drive'])
    expect(url()).toBe('/?search=drive')
  })

  it('starts a new search from the first page', async () => {
    const user = userEvent.setup()
    const requests = serveMovies(manyMovies(45))
    renderWithQueryClient(<MovieList />)
    await user.click(await screen.findByRole('button', { name: /next/i }))
    await screen.findByText('Page 2 of 3')

    await user.type(searchBox(), 'movie')

    await waitFor(() => expect(requests.at(-1)!.searchParams.get('search')).toBe('movie'))
    expect(requests.at(-1)!.searchParams.get('page')).toBe('0')
    expect(await screen.findByText('Page 1 of 3')).toBeInTheDocument()
    expect(url()).toBe('/?search=movie')
  })

  it('tells when nothing matches', async () => {
    const user = userEvent.setup()
    serveMovies(MOVIES)
    renderWithQueryClient(<MovieList />)
    await screen.findByText('Vertigo')

    await user.type(searchBox(), 'zzz')

    expect(await screen.findByText('No movies match "zzz".')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(searchBox()).toBeInTheDocument() // the search box stays, so the term can be changed
  })

  it('lists all movies again when the search is cleared', async () => {
    const user = userEvent.setup()
    window.history.replaceState(null, '', '/?search=drive')
    const requests = serveMovies(MOVIES)
    renderWithQueryClient(<MovieList />)
    await screen.findByText('1 movie')

    await user.clear(searchBox())

    expect(await screen.findByText('4 movies')).toBeInTheDocument()
    expect(requests.at(-1)!.searchParams.has('search')).toBe(false)
    expect(url()).toBe('/')
  })

  it('does not send a search that is only spaces', async () => {
    const user = userEvent.setup()
    const requests = serveMovies(MOVIES)
    renderWithQueryClient(<MovieList />)
    await screen.findByText('Vertigo')

    await user.type(searchBox(), '   ')
    await new Promise((resolve) => setTimeout(resolve, 400)) // longer than the search delay

    expect(requests.every((request) => !request.searchParams.has('search'))).toBe(true)
  })

  describe('URL', () => {
    it('starts from the search, page and page size in the URL', async () => {
      window.history.replaceState(null, '', '/?search=movie&page=2&size=10')
      const requests = serveMovies(manyMovies(25))

      renderWithQueryClient(<MovieList />)

      expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()
      expect(searchBox()).toHaveValue('movie')
      expect(screen.getByLabelText('Per page')).toHaveValue('10')
      const request = requests[0].searchParams
      expect([request.get('search'), request.get('page'), request.get('size')]).toEqual(['movie', '1', '10'])
    })

    it('ignores invalid values in the URL', async () => {
      window.history.replaceState(null, '', '/?page=abc&size=7')
      const requests = serveMovies(MOVIES)

      renderWithQueryClient(<MovieList />)

      expect(await screen.findByText('Page 1 of 1')).toBeInTheDocument()
      expect([requests[0].searchParams.get('page'), requests[0].searchParams.get('size')]).toEqual(['0', '20'])
    })

    it('records paging in the URL, and Back returns to the previous page', async () => {
      const user = userEvent.setup()
      serveMovies(manyMovies(45))
      renderWithQueryClient(<MovieList />)
      await user.click(await screen.findByRole('button', { name: /next/i }))
      await screen.findByText('Page 2 of 3')
      expect(url()).toBe('/?page=2')

      await user.selectOptions(screen.getByLabelText('Per page'), '10')
      await screen.findByText('Page 1 of 5')
      expect(url()).toBe('/?size=10')

      goBack('/?page=2')
      expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()
    })

    it('restores the search term on Back', async () => {
      const user = userEvent.setup()
      window.history.replaceState(null, '', '/?search=drive')
      serveMovies(MOVIES)
      renderWithQueryClient(<MovieList />)
      await screen.findByText('Drive')

      await user.clear(searchBox())
      await user.type(searchBox(), 'vertigo')
      await screen.findByText('Vertigo')

      goBack('/?search=drive')
      expect(await screen.findByText('Drive')).toBeInTheDocument()
      expect(searchBox()).toHaveValue('drive')
      // Restoring the term must not be taken as a new search that resets the URL.
      await new Promise((resolve) => setTimeout(resolve, 400))
      expect(url()).toBe('/?search=drive')
    })

    it('offers the first page when the page in the URL does not exist', async () => {
      const user = userEvent.setup()
      window.history.replaceState(null, '', '/?page=9')
      serveMovies(MOVIES)
      renderWithQueryClient(<MovieList />)

      await user.click(await screen.findByRole('button', { name: 'Go to the first page' }))

      expect(await screen.findByText('Vertigo')).toBeInTheDocument()
      expect(url()).toBe('/')
    })
  })
})
