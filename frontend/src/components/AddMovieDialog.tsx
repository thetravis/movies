import * as Dialog from '@radix-ui/react-dialog'
import { useIsMutating } from '@tanstack/react-query'
import { useState } from 'react'
import { movieKeys } from '../api/movies'
import type { Movie } from '../api/types'
import styles from './AddMovieDialog.module.css'
import { MovieForm } from './MovieForm'

interface AddMovieDialogProps {
  onCreated: (movie: Movie) => void
}

/**
 * "Add a movie" button and the dialog with the form. Radix Dialog provides the accessible modal
 * behaviour: focus stays inside, Esc closes, the page behind is inert and focus returns to the
 * button afterwards. The look is ours (CSS module).
 *
 * Clicking outside does not close the dialog (a stray click shouldn't lose the input), and it
 * can't be closed while the movie is being saved.
 */
export function AddMovieDialog({ onCreated }: AddMovieDialogProps) {
  const [open, setOpen] = useState(false)
  const saving = useIsMutating({ mutationKey: movieKeys.create }) > 0

  function changeOpen(next: boolean) {
    if (!next && saving) {
      return
    }
    setOpen(next)
  }

  return (
    <Dialog.Root open={open} onOpenChange={changeOpen}>
      <Dialog.Trigger asChild>
        <button type="button">Add a movie</button>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className={styles.overlay} />
        <Dialog.Content className={styles.content} onInteractOutside={(event) => event.preventDefault()}>
          <Dialog.Title className={styles.title}>Add a movie</Dialog.Title>
          <Dialog.Description className={styles.description}>
            Actors and synopsis are optional; all other fields are required.
          </Dialog.Description>
          <MovieForm
            onCreated={(movie) => {
              setOpen(false)
              onCreated(movie)
            }}
            onCancel={() => setOpen(false)}
          />
          {/* Last in the DOM, so the focus starts in the form's first field rather than here. */}
          <Dialog.Close asChild>
            <button type="button" className={styles.close} aria-label="Close" disabled={saving}>
              ×
            </button>
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
