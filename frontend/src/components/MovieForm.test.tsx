import { screen, within } from '@testing-library/react'
import userEvent, { PointerEventsCheckLevel, type UserEvent } from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { NewMovie } from '../api/types'
import App from '../App'
import { aMovie, aProblem } from '../test/data'
import { serveMovies } from '../test/handlers'
import { renderWithQueryClient } from '../test/render'
import { server } from '../test/server'

/** Accepts `POST /api/movies` like the backend; returns the received request bodies. */
function acceptNewMovies({ latency = 0 } = {}) {
  const received: NewMovie[] = []
  server.use(
    http.post('*/api/movies', async ({ request }) => {
      const body = (await request.json()) as NewMovie
      received.push(body)
      await delay(latency)
      const movie = aMovie({ ...body, actors: body.actors ?? [], synopsis: body.synopsis ?? null })
      return HttpResponse.json(movie, { status: 201, headers: { Location: `/api/movies/${movie.id}` } })
    }),
  )
  return received
}

function rejectNewMovies(status: number, problem: ReturnType<typeof aProblem>) {
  server.use(http.post('*/api/movies', () => HttpResponse.json(problem, { status })))
}

async function openForm(user: UserEvent) {
  renderWithQueryClient(<App />)
  await user.click(screen.getByRole('button', { name: 'Add a movie' }))
  return screen.getByRole('dialog', { name: 'Add a movie' })
}

async function fillRequiredFields(user: UserEvent) {
  await user.type(screen.getByLabelText('Name'), 'Jaws')
  await user.type(screen.getByLabelText('Year'), '1975')
  await user.type(screen.getByLabelText('Genres'), 'Thriller, Adventure')
  await user.type(screen.getByLabelText('Age limit'), '12')
  await user.selectOptions(screen.getByLabelText('Rating'), '5')
  const director = screen.getByRole('group', { name: 'Director' })
  await user.type(within(director).getByLabelText('First name'), 'Steven')
  await user.type(within(director).getByLabelText('Last name'), 'Spielberg')
}

function errorOf(label: string) {
  const input = screen.getByLabelText(label)
  expect(input).toHaveAttribute('aria-invalid', 'true')
  return document.getElementById(input.getAttribute('aria-describedby')!.split(' ').at(-1)!)!.textContent
}

describe('Adding a movie', () => {
  it('sends the movie, shows a notice and refreshes the list', async () => {
    const user = userEvent.setup()
    const listRequests = serveMovies([aMovie({ name: 'Vertigo' })])
    const received = acceptNewMovies()
    await openForm(user)
    await fillRequiredFields(user)
    await user.type(screen.getByLabelText('Actor 1 first name'), 'Roy')
    await user.type(screen.getByLabelText('Actor 1 last name'), 'Scheider')
    await user.type(screen.getByLabelText('Synopsis'), 'A giant shark.')
    const listRequestsBefore = listRequests.length

    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    expect(await screen.findByText('Added "Jaws" (1975).')).toBeInTheDocument()
    expect(received).toEqual([
      {
        name: 'Jaws',
        year: 1975,
        genres: ['Thriller', 'Adventure'],
        ageLimit: 12,
        rating: 5,
        director: { firstName: 'Steven', lastName: 'Spielberg' },
        actors: [{ firstName: 'Roy', lastName: 'Scheider' }],
        synopsis: 'A giant shark.',
      },
    ])
    expect(screen.queryByRole('dialog', { name: 'Add a movie' })).not.toBeInTheDocument()
    expect(listRequests.length).toBeGreaterThan(listRequestsBefore) // the list was reloaded
  })

  it('sends optional fields left empty as null and an empty actor list', async () => {
    const user = userEvent.setup()
    serveMovies([])
    const received = acceptNewMovies()
    await openForm(user)
    await fillRequiredFields(user)

    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    await screen.findByText('Added "Jaws" (1975).')
    expect(received[0].synopsis).toBeNull()
    expect(received[0].actors).toEqual([])
  })

  it('checks the fields before sending and focuses the first invalid one', async () => {
    const user = userEvent.setup()
    serveMovies([])
    const received = acceptNewMovies()
    await openForm(user)
    await user.type(screen.getByLabelText('Year'), '1500')

    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    expect(errorOf('Name')).toBe('Required.')
    expect(errorOf('Year')).toBe('Enter a whole number from 1888 to 2100.')
    expect(errorOf('Genres')).toBe('Enter at least one genre.')
    expect(screen.getByLabelText('Name')).toHaveFocus()
    expect(received).toEqual([])
  })

  it('clears the error of a field when it is edited', async () => {
    const user = userEvent.setup()
    serveMovies([])
    await openForm(user)
    await user.click(screen.getByRole('button', { name: 'Save movie' }))
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')

    await user.type(screen.getByLabelText('Name'), 'J')

    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'false')
  })

  it("shows the backend's field errors next to the fields", async () => {
    const user = userEvent.setup()
    serveMovies([])
    rejectNewMovies(
      400,
      aProblem({
        status: 400,
        title: 'Bad Request',
        detail: 'Request validation failed.',
        errors: [
          { field: 'director.firstName', message: 'must not be blank' },
          { field: 'actors[0].lastName', message: 'size must be between 1 and 100' },
        ],
      }),
    )
    await openForm(user)
    await fillRequiredFields(user)
    await user.type(screen.getByLabelText('Actor 1 first name'), 'Roy')
    await user.type(screen.getByLabelText('Actor 1 last name'), 'Scheider')

    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Please correct the marked fields.')
    expect(errorOf('Actor 1 last name')).toBe('Size must be between 1 and 100.')
    const director = screen.getByRole('group', { name: 'Director' })
    expect(within(director).getByLabelText('First name')).toHaveAttribute('aria-invalid', 'true')
    expect(within(director).getByLabelText('First name')).toHaveFocus()
  })

  it('shows the duplicate message and keeps the input', async () => {
    const user = userEvent.setup()
    serveMovies([])
    rejectNewMovies(
      409,
      aProblem({ status: 409, title: 'Conflict', detail: "A movie named 'Jaws' from 1975 already exists." }),
    )
    await openForm(user)
    await fillRequiredFields(user)

    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    expect(await screen.findByRole('alert')).toHaveTextContent("A movie named 'Jaws' from 1975 already exists.")
    expect(screen.getByLabelText('Name')).toHaveValue('Jaws')
  })

  it('explains when the backend cannot be reached', async () => {
    const user = userEvent.setup()
    serveMovies([])
    server.use(http.post('*/api/movies', () => HttpResponse.error()))
    await openForm(user)
    await fillRequiredFields(user)

    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not connect to the server')
  })

  it('prevents sending twice while saving', async () => {
    const user = userEvent.setup()
    serveMovies([])
    acceptNewMovies({ latency: 100 })
    await openForm(user)
    await fillRequiredFields(user)

    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled()
    await screen.findByText('Added "Jaws" (1975).')
  })

  it('adds and removes actor rows', async () => {
    const user = userEvent.setup()
    serveMovies([])
    const received = acceptNewMovies()
    await openForm(user)
    await fillRequiredFields(user)
    await user.click(screen.getByRole('button', { name: 'Add actor' }))
    await user.type(screen.getByLabelText('Actor 1 first name'), 'Roy')
    await user.type(screen.getByLabelText('Actor 1 last name'), 'Scheider')
    await user.type(screen.getByLabelText('Actor 2 first name'), 'Robert')
    await user.type(screen.getByLabelText('Actor 2 last name'), 'Shaw')

    await user.click(screen.getByRole('button', { name: 'Remove actor 1' }))
    await user.click(screen.getByRole('button', { name: 'Save movie' }))

    await screen.findByText('Added "Jaws" (1975).')
    expect(received[0].actors).toEqual([{ firstName: 'Robert', lastName: 'Shaw' }])
  })

  describe('dialog', () => {
    it('opens as a modal dialog with the focus in the first field', async () => {
      const user = userEvent.setup()
      serveMovies([])

      const dialog = await openForm(user)

      expect(dialog).toHaveAccessibleDescription('Actors and synopsis are optional; all other fields are required.')
      expect(screen.getByLabelText('Name')).toHaveFocus()
    })

    it('closes on Cancel without sending, and the focus returns to the button', async () => {
      const user = userEvent.setup()
      serveMovies([])
      const received = acceptNewMovies()
      await openForm(user)
      await user.type(screen.getByLabelText('Name'), 'Jaws')

      await user.click(screen.getByRole('button', { name: 'Cancel' }))

      expect(screen.queryByRole('dialog', { name: 'Add a movie' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Add a movie' })).toHaveFocus()
      expect(received).toEqual([])
    })

    it('closes with Esc and with the close button', async () => {
      const user = userEvent.setup()
      serveMovies([])
      await openForm(user)

      await user.keyboard('{Escape}')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Add a movie' }))
      await user.click(screen.getByRole('button', { name: 'Close' }))
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('stays open when clicking outside, so the input is not lost', async () => {
      // While the dialog is open Radix disables pointer events on the page behind it; send the click
      // anyway, to check that the dialog itself ignores clicks outside.
      const user = userEvent.setup({ pointerEventsCheck: PointerEventsCheckLevel.Never })
      serveMovies([])
      await openForm(user)
      await user.type(screen.getByLabelText('Name'), 'Jaws')

      await user.click(document.body)

      expect(screen.getByRole('dialog', { name: 'Add a movie' })).toBeInTheDocument()
      expect(screen.getByLabelText('Name')).toHaveValue('Jaws')
    })

    it('cannot be closed while saving', async () => {
      const user = userEvent.setup()
      serveMovies([])
      acceptNewMovies({ latency: 200 })
      await openForm(user)
      await fillRequiredFields(user)

      await user.click(screen.getByRole('button', { name: 'Save movie' }))
      await user.keyboard('{Escape}')

      expect(screen.getByRole('dialog', { name: 'Add a movie' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled()
      expect(await screen.findByText('Added "Jaws" (1975).')).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })
})
