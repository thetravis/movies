import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useCreateMovie } from '../api/movies'
import type { Movie } from '../api/types'
import styles from './MovieForm.module.css'
import {
  emptyMovieForm,
  emptyPerson,
  toFormErrors,
  toNewMovie,
  validateMovieForm,
  type FormErrors,
  type MovieFormValues,
  type PersonValues,
} from './movieFormModel'

interface MovieFormProps {
  onCreated: (movie: Movie) => void
  onCancel: () => void
}

/**
 * Form for adding a movie. Values are checked against the contract's rules before sending; errors
 * from the backend (`400` field errors, `409` duplicate) are shown next to the fields or on top.
 */
export function MovieForm({ onCreated, onCancel }: MovieFormProps) {
  const [values, setValues] = useState<MovieFormValues>(emptyMovieForm)
  const [errors, setErrors] = useState<FormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [focusRequest, setFocusRequest] = useState(0)
  const formRef = useRef<HTMLFormElement>(null)
  const createMovie = useCreateMovie()

  // After a failed submit, move the focus to the first field with an error.
  useEffect(() => {
    if (focusRequest > 0) {
      formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
    }
  }, [focusRequest])

  function change(update: (current: MovieFormValues) => MovieFormValues, path: string) {
    setValues(update)
    // Editing a field clears its error.
    setErrors((current) => {
      const next = { ...current }
      delete next[path]
      return next
    })
  }

  function changeActor(index: number, key: keyof PersonValues, value: string) {
    change(
      (current) => ({
        ...current,
        actors: current.actors.map((actor, i) => (i === index ? { ...actor, [key]: value } : actor)),
      }),
      `actors[${index}].${key}`,
    )
  }

  function removeActor(index: number) {
    setValues((current) => ({ ...current, actors: current.actors.filter((_, i) => i !== index) }))
    // Row numbers shift, so actor errors no longer match their rows; they come back on the next submit.
    setErrors((current) => Object.fromEntries(Object.entries(current).filter(([path]) => !path.startsWith('actors['))))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    const clientErrors = validateMovieForm(values)
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) {
      setFocusRequest((n) => n + 1)
      return
    }
    createMovie.mutate(toNewMovie(values), {
      onSuccess: onCreated,
      onError: (error) => {
        const fieldErrors = error.problem?.errors
        if (fieldErrors && fieldErrors.length > 0) {
          const serverErrors = toFormErrors(fieldErrors)
          const unknown = Object.entries(serverErrors).filter(([path]) => !fieldPaths(values).includes(path))
          setErrors(serverErrors)
          setFormError(
            unknown.length > 0
              ? `Please correct the following: ${unknown.map(([path, message]) => `${path}: ${message}`).join(' ')}`
              : 'Please correct the marked fields.',
          )
          setFocusRequest((n) => n + 1)
        } else {
          setFormError(error.message)
        }
      },
    })
  }

  const field = (path: string) => ({ path, error: errors[path] })

  return (
    <form ref={formRef} className={styles.form} onSubmit={handleSubmit} noValidate aria-labelledby="add-movie-title">
      <h2 id="add-movie-title">Add a movie</h2>

      {formError && (
        <p role="alert" className={styles.formError}>
          {formError}
        </p>
      )}

      <div className={styles.row}>
        <Field label="Name" {...field('name')} wide>
          {(props) => (
            <input
              {...props}
              autoFocus
              maxLength={255}
              value={values.name}
              onChange={(e) => change((v) => ({ ...v, name: e.target.value }), 'name')}
            />
          )}
        </Field>
        <Field label="Year" {...field('year')}>
          {(props) => (
            <input
              {...props}
              type="number"
              min={1888}
              max={2100}
              value={values.year}
              onChange={(e) => change((v) => ({ ...v, year: e.target.value }), 'year')}
            />
          )}
        </Field>
      </div>

      <div className={styles.row}>
        <Field label="Genres" hint="Separate with commas, e.g. Drama, Thriller" {...field('genres')} wide>
          {(props) => (
            <input
              {...props}
              value={values.genres}
              onChange={(e) => change((v) => ({ ...v, genres: e.target.value }), 'genres')}
            />
          )}
        </Field>
        <Field label="Age limit" {...field('ageLimit')}>
          {(props) => (
            <input
              {...props}
              type="number"
              min={0}
              max={21}
              value={values.ageLimit}
              onChange={(e) => change((v) => ({ ...v, ageLimit: e.target.value }), 'ageLimit')}
            />
          )}
        </Field>
        <Field label="Rating" {...field('rating')}>
          {(props) => (
            <select
              {...props}
              value={values.rating}
              onChange={(e) => change((v) => ({ ...v, rating: e.target.value }), 'rating')}
            >
              <option value="">Choose…</option>
              {[1, 2, 3, 4, 5].map((rating) => (
                <option key={rating} value={rating}>
                  {rating} / 5
                </option>
              ))}
            </select>
          )}
        </Field>
      </div>

      <fieldset className={styles.fieldset}>
        <legend>Director</legend>
        <PersonFields
          path="director"
          person={values.director}
          errors={errors}
          onChange={(key, value) =>
            change((v) => ({ ...v, director: { ...v.director, [key]: value } }), `director.${key}`)
          }
        />
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend>Actors</legend>
        {values.actors.length === 0 && <p className={styles.hint}>No actors.</p>}
        {values.actors.map((actor, index) => (
          <div key={index} className={styles.actor}>
            <PersonFields
              path={`actors[${index}]`}
              label={`Actor ${index + 1}`}
              person={actor}
              errors={errors}
              onChange={(key, value) => changeActor(index, key, value)}
            />
            <button type="button" onClick={() => removeActor(index)} aria-label={`Remove actor ${index + 1}`}>
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={() => setValues((v) => ({ ...v, actors: [...v.actors, emptyPerson()] }))}
        >
          Add actor
        </button>
      </fieldset>

      <Field label="Synopsis" hint="Optional" {...field('synopsis')} wide>
        {(props) => (
          <textarea
            {...props}
            rows={4}
            maxLength={2000}
            value={values.synopsis}
            onChange={(e) => change((v) => ({ ...v, synopsis: e.target.value }), 'synopsis')}
          />
        )}
      </Field>

      <div className={styles.actions}>
        <button type="submit" className={styles.primary} disabled={createMovie.isPending}>
          {createMovie.isPending ? 'Saving…' : 'Save movie'}
        </button>
        <button type="button" onClick={onCancel} disabled={createMovie.isPending}>
          Cancel
        </button>
      </div>
    </form>
  )
}

interface PersonFieldsProps {
  path: string
  /** Prefix for the labels, e.g. "Actor 2"; without it the labels are just "First name"/"Last name". */
  label?: string
  person: PersonValues
  errors: FormErrors
  onChange: (key: keyof PersonValues, value: string) => void
}

function PersonFields({ path, label, person, errors, onChange }: PersonFieldsProps) {
  const prefix = label ? `${label} ` : ''
  return (
    <div className={styles.row}>
      {(['firstName', 'lastName'] as const).map((key) => (
        <Field
          key={key}
          label={`${prefix}${key === 'firstName' ? 'first name' : 'last name'}`}
          path={`${path}.${key}`}
          error={errors[`${path}.${key}`]}
          wide
        >
          {(props) => (
            <input {...props} maxLength={100} value={person[key]} onChange={(e) => onChange(key, e.target.value)} />
          )}
        </Field>
      ))}
    </div>
  )
}

interface ControlProps {
  id: string
  'aria-invalid': boolean
  'aria-describedby': string | undefined
}

interface FieldProps {
  label: string
  path: string
  error: string | undefined
  hint?: string
  wide?: boolean
  children: (props: ControlProps) => ReactNode
}

/** A label, its input and its hint and error message, connected for screen readers. */
function Field({ label, path, error, hint, wide = false, children }: FieldProps) {
  const id = `movie-${path.replace(/[^a-zA-Z0-9]+/g, '-')}`
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined
  return (
    <div className={wide ? `${styles.field} ${styles.wide}` : styles.field}>
      <label htmlFor={id}>{capitalizeFirst(label)}</label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {hint && (
        <small id={`${id}-hint`} className={styles.hint}>
          {hint}
        </small>
      )}
      {error && (
        <small id={`${id}-error`} className={styles.error}>
          {error}
        </small>
      )}
    </div>
  )
}

/** The form's field paths, in the backend's notation, for the current number of actor rows. */
function fieldPaths(values: MovieFormValues): string[] {
  const person = (path: string) => [`${path}.firstName`, `${path}.lastName`]
  return [
    'name',
    'year',
    'genres',
    'ageLimit',
    'rating',
    ...person('director'),
    ...values.actors.flatMap((_, index) => person(`actors[${index}]`)),
    'synopsis',
  ]
}

function capitalizeFirst(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
