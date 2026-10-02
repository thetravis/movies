import { useLayoutEffect, useRef, useState } from 'react'
import styles from './Synopsis.module.css'

interface SynopsisProps {
  text: string
  expanded: boolean
  onToggle: () => void
}

/**
 * A synopsis cut to a few lines, with a "Show more" button when the text doesn't fit. The table
 * row can also be clicked to expand it; the button keeps it usable with a keyboard and screen
 * readers.
 */
export function Synopsis({ text, expanded, onToggle }: SynopsisProps) {
  const textRef = useRef<HTMLDivElement>(null)
  const [overflows, setOverflows] = useState(false)

  // Whether the text is cut off depends on the layout (column width), so it is measured in the
  // browser, and again when the size changes. Only measured while collapsed: expanded text fits.
  useLayoutEffect(() => {
    const element = textRef.current
    if (!element || expanded) {
      return
    }
    const measure = () => setOverflows(element.scrollHeight > element.clientHeight + 1)
    measure()
    if (typeof ResizeObserver === 'undefined') {
      return
    }
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [text, expanded])

  return (
    <>
      <div ref={textRef} className={expanded ? undefined : styles.clamped}>
        {text}
      </div>
      {overflows && (
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={expanded}
          onClick={(event) => {
            event.stopPropagation() // the row's click handler would toggle it back
            onToggle()
          }}
        >
          {expanded ? 'Show less' : 'Show more'}
        </button>
      )}
    </>
  )
}
