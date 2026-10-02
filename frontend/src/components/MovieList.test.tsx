import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { aMovie, aProblem } from '../test/data'
import { manyMovies, serveMovies } from '../test/handlers'
import { renderWithQueryClient } from '../test/render'
import { server } from '../test/server'
import { MovieList } from './MovieList'

function movieRows() {
  // The first row is the header.
  return screen.getAllByRole('row').slice(1)
}

describe('MovieList', () => {
  it('shows the movies with their details', async () => {
    serveMovies([
      aMovie({
        name: 'Vertigo',
        year: 1958,
        genres: ['Mystery', 'Thriller'],
        rating: 5,
        ageLimit: 12,
        director: { firstName: 'Alfred', lastName: 'Hitchcock' },
        actors: [
          { firstName: 'James', lastName: 'Stewart' },
          { firstName: 'Kim', lastName: 'Novak' },
        ],
        synopsis: 'A detective with a fear of heights.',
      }),
      aMovie({ name: 'Spirited Away', ageLimit: 0, actors: [], synopsis: null }),
    ])

    renderWithQueryClient(<MovieList />)

    const [vertigo, spiritedAway] = await screen.findAllByRole('row').then((rows) => rows.slice(1))
    expect(within(vertigo).getByRole('rowheader')).toHaveTextContent('Vertigo (1958)')
    expect(vertigo).toHaveTextContent('Mystery, Thriller')
    expect(within(vertigo).getByLabelText('5 out of 5')).toBeInTheDocument()
    expect(vertigo).toHaveTextContent('12+')
    expect(vertigo).toHaveTextContent('Alfred Hitchcock')
    expect(vertigo).toHaveTextContent('James Stewart, Kim Novak')
    expect(vertigo).toHaveTextContent('A detective with a fear of heights.')

    // No actors and a null synopsis (the contract always sends the field) are shown as a dash.
    expect(spiritedAway).toHaveTextContent('All ages')
    expect(within(spiritedAway).getAllByText('—')).toHaveLength(2)
  })

  it('shows a loading message until the movies arrive', async () => {
    serveMovies([aMovie({ name: 'Vertigo' })], { latency: 50 })

    renderWithQueryClient(<MovieList />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading movies…')
    expect(await screen.findByText('Vertigo')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('pages through the movies', async () => {
    const user = userEvent.setup()
    const requests = serveMovies(manyMovies(45))
    renderWithQueryClient(<MovieList />)

    expect(await screen.findByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByText('45 movies')).toBeInTheDocument()
    expect(movieRows()).toHaveLength(20)
    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()
    expect(screen.getByText('Movie 21')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(await screen.findByText('Page 3 of 3')).toBeInTheDocument()
    expect(movieRows()).toHaveLength(5)
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /previous/i }))
    expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()

    // Going back shows the cached page at once and refreshes it in the background (4th request).
    expect(requests.map((url) => url.searchParams.get('page'))).toEqual(['0', '1', '2', '1'])
  })

  it('goes back to the first page when the page size changes', async () => {
    const user = userEvent.setup()
    const requests = serveMovies(manyMovies(45))
    renderWithQueryClient(<MovieList />)
    await user.click(await screen.findByRole('button', { name: /next/i }))
    await screen.findByText('Page 2 of 3')

    await user.selectOptions(screen.getByLabelText('Per page'), '50')

    expect(await screen.findByText('Page 1 of 1')).toBeInTheDocument()
    expect(movieRows()).toHaveLength(45)
    const last = requests.at(-1)!
    expect([last.searchParams.get('page'), last.searchParams.get('size')]).toEqual(['0', '50'])
  })

  it('keeps the current page visible while the next one loads', async () => {
    const user = userEvent.setup()
    serveMovies(manyMovies(45), { latency: 100 })
    renderWithQueryClient(<MovieList />)
    await screen.findByText('Movie 01')

    await user.click(screen.getByRole('button', { name: /next/i }))

    // The old rows stay (marked busy) instead of being replaced by a loading message.
    expect(screen.getByText('Movie 01')).toBeInTheDocument()
    expect(screen.getByRole('table')).toHaveAttribute('aria-busy', 'true')
    expect(await screen.findByText('Movie 21')).toBeInTheDocument()
    expect(screen.getByRole('table')).toHaveAttribute('aria-busy', 'false')
  })

  it('shows a message when there are no movies', async () => {
    serveMovies([])

    renderWithQueryClient(<MovieList />)

    expect(await screen.findByText('No movies yet.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  describe('errors', () => {
    it("shows the problem's detail", async () => {
      server.use(
        http.get('*/api/movies', () =>
          HttpResponse.json(aProblem({ status: 400, title: 'Bad Request', detail: 'Failed to convert page.' }), {
            status: 400,
          }),
        ),
      )

      renderWithQueryClient(<MovieList />)

      expect(await screen.findByRole('alert')).toHaveTextContent('Failed to convert page.')
    })

    it("falls back to the problem's title when there is no detail", async () => {
      server.use(
        http.get('*/api/movies', () => HttpResponse.json(aProblem({ detail: null }), { status: 500 })),
      )

      renderWithQueryClient(<MovieList />)

      expect(await screen.findByRole('alert')).toHaveTextContent('Internal Server Error')
    })

    it('explains when the backend cannot be reached, and can try again', async () => {
      const user = userEvent.setup()
      server.use(http.get('*/api/movies', () => HttpResponse.error()))
      renderWithQueryClient(<MovieList />)

      expect(await screen.findByRole('alert')).toHaveTextContent('Could not connect to the server')

      serveMovies([aMovie({ name: 'Vertigo' })])
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByText('Vertigo')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })
})
