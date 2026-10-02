import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { aMovie } from '../test/data'
import { MovieTable } from './MovieTable'

/**
 * jsdom does no layout, so element heights are always 0. These tests set the heights the synopsis
 * measures to say whether its text fits in the collapsed (three-line) view.
 */
function synopsisDoesNotFit() {
  vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(120)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(60)
}

function rowOf(movieName: string) {
  return screen.getByRole('rowheader', { name: new RegExp(movieName) }).closest('tr')!
}

describe('MovieTable synopsis', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('expands and collapses a row when it is clicked', async () => {
    const user = userEvent.setup()
    synopsisDoesNotFit()
    render(<MovieTable movies={[aMovie({ name: 'Vertigo', synopsis: 'A long synopsis.' })]} />)
    const toggle = screen.getByRole('button', { name: 'Show more' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await user.click(rowOf('Vertigo'))
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(toggle).toHaveTextContent('Show less')

    await user.click(rowOf('Vertigo'))
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  it('can also be expanded with the button (keyboard and screen readers)', async () => {
    const user = userEvent.setup()
    synopsisDoesNotFit()
    render(<MovieTable movies={[aMovie({ name: 'Vertigo', synopsis: 'A long synopsis.' })]} />)
    const toggle = screen.getByRole('button', { name: 'Show more' })

    toggle.focus()
    await user.keyboard('{Enter}')

    // Expanded once: the button's click must not also trigger the row's click.
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
  })

  it('expands only the clicked row', async () => {
    const user = userEvent.setup()
    synopsisDoesNotFit()
    render(
      <MovieTable
        movies={[aMovie({ name: 'Vertigo', synopsis: 'First.' }), aMovie({ name: 'Drive', synopsis: 'Second.' })]}
      />,
    )

    await user.click(rowOf('Drive'))

    expect(within(rowOf('Drive')).getByRole('button')).toHaveAttribute('aria-expanded', 'true')
    expect(within(rowOf('Vertigo')).getByRole('button')).toHaveAttribute('aria-expanded', 'false')
  })

  it('does not collapse the row when the click ends a text selection', async () => {
    const user = userEvent.setup()
    synopsisDoesNotFit()
    render(<MovieTable movies={[aMovie({ name: 'Vertigo', synopsis: 'A long synopsis.' })]} />)
    const toggle = screen.getByRole('button', { name: 'Show more' })
    vi.spyOn(window, 'getSelection').mockReturnValue({ toString: () => 'long synopsis' } as Selection)

    await user.click(rowOf('Vertigo'))

    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  it('shows no button when the synopsis fits', () => {
    render(<MovieTable movies={[aMovie({ name: 'Vertigo', synopsis: 'Short.' })]} />)

    expect(screen.getByText('Short.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
