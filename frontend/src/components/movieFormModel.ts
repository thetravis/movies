import type { FieldError, NewMovie } from '../api/types'

// The add-movie form's logic, kept free of React so it can be tested on its own.

export interface PersonValues {
  firstName: string
  lastName: string
}

/** What the user has typed. Everything is text, as in the inputs. */
export interface MovieFormValues {
  name: string
  year: string
  /** Comma-separated, e.g. "Drama, Thriller". */
  genres: string
  ageLimit: string
  rating: string
  director: PersonValues
  actors: PersonValues[]
  synopsis: string
}

/** Error messages by field path, using the backend's paths: `name`, `director.firstName`, `actors[0].lastName`, ... */
export type FormErrors = Record<string, string>

export const emptyPerson = (): PersonValues => ({ firstName: '', lastName: '' })

export const emptyMovieForm = (): MovieFormValues => ({
  name: '',
  year: '',
  genres: '',
  ageLimit: '',
  rating: '',
  director: emptyPerson(),
  actors: [emptyPerson()],
  synopsis: '',
})

// Limits from the NewMovie schema in the API contract (openapi.yaml). The backend checks them too;
// checking here gives immediate feedback.
const LIMITS = {
  name: 255,
  personName: 100,
  genre: 50,
  synopsis: 2000,
  year: { min: 1888, max: 2100 },
  ageLimit: { min: 0, max: 21 },
  rating: { min: 1, max: 5 },
} as const

/** Checks the values against the contract's rules. Returns an empty object when everything is valid. */
export function validateMovieForm(values: MovieFormValues): FormErrors {
  const errors: FormErrors = {}

  checkText(errors, 'name', values.name, LIMITS.name, true)
  checkInteger(errors, 'year', values.year, LIMITS.year)
  checkInteger(errors, 'ageLimit', values.ageLimit, LIMITS.ageLimit)
  checkInteger(errors, 'rating', values.rating, LIMITS.rating)

  const genres = parseGenres(values.genres)
  if (genres.length === 0) {
    errors.genres = 'Enter at least one genre.'
  } else if (genres.some((genre) => genre.length > LIMITS.genre)) {
    errors.genres = `Each genre can be at most ${LIMITS.genre} characters.`
  }

  checkPerson(errors, 'director', values.director)
  values.actors.forEach((actor, index) => {
    if (!isEmptyPerson(actor)) {
      checkPerson(errors, `actors[${index}]`, actor)
    }
  })

  checkText(errors, 'synopsis', values.synopsis, LIMITS.synopsis, false)
  return errors
}

/** Converts valid form values to the API's `NewMovie`. Text is trimmed; empty actor rows are left out. */
export function toNewMovie(values: MovieFormValues): NewMovie {
  const synopsis = values.synopsis.trim()
  return {
    name: values.name.trim(),
    year: Number(values.year),
    genres: parseGenres(values.genres),
    ageLimit: Number(values.ageLimit),
    rating: Number(values.rating),
    director: trimPerson(values.director),
    actors: values.actors.filter((actor) => !isEmptyPerson(actor)).map(trimPerson),
    synopsis: synopsis === '' ? null : synopsis,
  }
}

/**
 * Turns the backend's field errors (`Problem.errors`) into form errors. Paths of set elements, such
 * as `genres[]` (what the backend sends for an invalid genre), are attributed to the field itself.
 */
export function toFormErrors(fieldErrors: FieldError[]): FormErrors {
  const errors: FormErrors = {}
  for (const { field, message } of fieldErrors) {
    const path = field.replace(/\[\].*$/, '')
    errors[path] ??= capitalize(message)
  }
  return errors
}

/** Comma-separated genres → trimmed, non-empty, without repeats (ignoring case). */
export function parseGenres(text: string): string[] {
  const genres = new Map<string, string>()
  for (const genre of text.split(',').map((part) => part.trim())) {
    if (genre !== '' && !genres.has(genre.toLowerCase())) {
      genres.set(genre.toLowerCase(), genre)
    }
  }
  return [...genres.values()]
}

function checkText(errors: FormErrors, field: string, value: string, maxLength: number, required: boolean) {
  const text = value.trim()
  if (required && text === '') {
    errors[field] = 'Required.'
  } else if (text.length > maxLength) {
    errors[field] = `At most ${maxLength} characters.`
  }
}

function checkInteger(errors: FormErrors, field: string, value: string, { min, max }: { min: number; max: number }) {
  if (value.trim() === '') {
    errors[field] = 'Required.'
    return
  }
  const number = Number(value)
  if (!Number.isInteger(number) || number < min || number > max) {
    errors[field] = `Enter a whole number from ${min} to ${max}.`
  }
}

function checkPerson(errors: FormErrors, path: string, person: PersonValues) {
  checkText(errors, `${path}.firstName`, person.firstName, LIMITS.personName, true)
  checkText(errors, `${path}.lastName`, person.lastName, LIMITS.personName, true)
}

function isEmptyPerson(person: PersonValues): boolean {
  return person.firstName.trim() === '' && person.lastName.trim() === ''
}

function trimPerson(person: PersonValues) {
  return { firstName: person.firstName.trim(), lastName: person.lastName.trim() }
}

function capitalize(message: string): string {
  const text = message.charAt(0).toUpperCase() + message.slice(1)
  return /[.!?]$/.test(text) ? text : `${text}.`
}
