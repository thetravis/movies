import { describe, expect, it } from 'vitest'
import {
  emptyMovieForm,
  parseGenres,
  toFormErrors,
  toNewMovie,
  validateMovieForm,
  type MovieFormValues,
} from './movieFormModel'

function validValues(overrides: Partial<MovieFormValues> = {}): MovieFormValues {
  return {
    ...emptyMovieForm(),
    name: 'Jaws',
    year: '1975',
    genres: 'Thriller',
    ageLimit: '12',
    rating: '5',
    director: { firstName: 'Steven', lastName: 'Spielberg' },
    ...overrides,
  }
}

describe('validateMovieForm', () => {
  it('accepts valid values', () => {
    expect(validateMovieForm(validValues())).toEqual({})
  })

  it('reports every missing required field', () => {
    expect(Object.keys(validateMovieForm(emptyMovieForm())).sort()).toEqual(
      ['ageLimit', 'director.firstName', 'director.lastName', 'genres', 'name', 'rating', 'year'].sort(),
    )
  })

  it('treats text of only spaces as missing', () => {
    expect(validateMovieForm(validValues({ name: '   ' }))).toEqual({ name: 'Required.' })
  })

  it.each([
    ['year', '1887'],
    ['year', '2101'],
    ['year', '1975.5'],
    ['ageLimit', '-1'],
    ['ageLimit', '22'],
    ['rating', '0'],
    ['rating', '6'],
  ] as const)('rejects %s = %s (outside the contract range or not a whole number)', (field, value) => {
    expect(validateMovieForm(validValues({ [field]: value }))).toHaveProperty(field)
  })

  it('accepts the range limits', () => {
    expect(validateMovieForm(validValues({ year: '1888', ageLimit: '0', rating: '1' }))).toEqual({})
    expect(validateMovieForm(validValues({ year: '2100', ageLimit: '21', rating: '5' }))).toEqual({})
  })

  it('checks text lengths', () => {
    const errors = validateMovieForm(
      validValues({ name: 'x'.repeat(256), genres: 'x'.repeat(51), synopsis: 'x'.repeat(2001) }),
    )
    expect(Object.keys(errors).sort()).toEqual(['genres', 'name', 'synopsis'])
  })

  it('ignores empty actor rows but checks half-filled ones', () => {
    const errors = validateMovieForm(
      validValues({
        actors: [
          { firstName: '', lastName: '' },
          { firstName: 'Roy', lastName: '' },
        ],
      }),
    )
    expect(errors).toEqual({ 'actors[1].lastName': 'Required.' })
  })
})

describe('toNewMovie', () => {
  it('converts the text inputs to the API request', () => {
    expect(
      toNewMovie(
        validValues({
          name: '  Jaws ',
          genres: 'Thriller, , Adventure,thriller',
          actors: [
            { firstName: ' Roy', lastName: 'Scheider ' },
            { firstName: '', lastName: '' },
          ],
          synopsis: '  A shark.  ',
        }),
      ),
    ).toEqual({
      name: 'Jaws',
      year: 1975,
      genres: ['Thriller', 'Adventure'],
      ageLimit: 12,
      rating: 5,
      director: { firstName: 'Steven', lastName: 'Spielberg' },
      actors: [{ firstName: 'Roy', lastName: 'Scheider' }],
      synopsis: 'A shark.',
    })
  })

  it('sends an empty synopsis as null and no actors as an empty list', () => {
    const movie = toNewMovie(validValues({ synopsis: '   ', actors: [] }))
    expect(movie.synopsis).toBeNull()
    expect(movie.actors).toEqual([])
  })
})

describe('parseGenres', () => {
  it('splits, trims and removes repeats ignoring case', () => {
    expect(parseGenres(' Drama,thriller ,DRAMA,, ')).toEqual(['Drama', 'thriller'])
  })
})

describe('toFormErrors', () => {
  it("maps the backend's field paths to form fields", () => {
    expect(
      toFormErrors([
        { field: 'director.firstName', message: 'must not be blank' },
        { field: 'actors[0].lastName', message: 'size must be between 1 and 100' },
        { field: 'genres[]', message: 'size must be between 1 and 50' }, // the backend's path for a set element
      ]),
    ).toEqual({
      'director.firstName': 'Must not be blank.',
      'actors[0].lastName': 'Size must be between 1 and 100.',
      genres: 'Size must be between 1 and 50.',
    })
  })

  it('keeps the first message when a field has several', () => {
    expect(
      toFormErrors([
        { field: 'name', message: 'must not be blank' },
        { field: 'name', message: 'size must be between 1 and 255' },
      ]),
    ).toEqual({ name: 'Must not be blank.' })
  })
})
