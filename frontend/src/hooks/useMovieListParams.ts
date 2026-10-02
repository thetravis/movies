import { useEffect, useState } from 'react'

export interface MovieListParams {
  /** Search term; empty for no filtering. */
  search: string
  /** Zero-based page index, as in the API. */
  page: number
  size: number
}

interface Options {
  /** Allowed page sizes; any other `size` in the URL is ignored. */
  sizes: readonly number[]
  defaultSize: number
}

/**
 * How a change is recorded in the browser history: `push` adds an entry (Back returns to the
 * previous state), `replace` changes the current one (e.g. while typing a search term).
 */
export type HistoryMode = 'push' | 'replace'

/**
 * The movie list's search term, page and page size, kept in the URL query string
 * (`?search=war&page=2&size=50`), so reload, Back/Forward and shared links work.
 *
 * In the URL the page is 1-based, as people read it; in the app it is 0-based, as in the API.
 * Default values are left out of the URL; invalid values fall back to the defaults.
 */
export function useMovieListParams(options: Options) {
  const [params, setParams] = useState(() => readParams(options))

  // Back/Forward: take the state from the URL again.
  useEffect(() => {
    const onPopState = () => setParams(readParams(options))
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [options])

  function update(changes: Partial<MovieListParams>, mode: HistoryMode) {
    const next = { ...params, ...changes }
    writeParams(next, options, mode)
    setParams(next)
  }

  return { params, update }
}

function readParams({ sizes, defaultSize }: Options): MovieListParams {
  const query = new URLSearchParams(window.location.search)
  const page = Number(query.get('page'))
  const size = Number(query.get('size'))
  return {
    search: query.get('search') ?? '',
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
    size: sizes.includes(size) ? size : defaultSize,
  }
}

function writeParams({ search, page, size }: MovieListParams, { defaultSize }: Options, mode: HistoryMode) {
  const query = new URLSearchParams(window.location.search) // keeps unrelated parameters
  setOrDelete(query, 'search', search.trim() === '' ? null : search)
  setOrDelete(query, 'page', page === 0 ? null : String(page + 1))
  setOrDelete(query, 'size', size === defaultSize ? null : String(size))

  const queryString = query.toString()
  const url = `${window.location.pathname}${queryString ? `?${queryString}` : ''}${window.location.hash}`
  if (url === `${window.location.pathname}${window.location.search}${window.location.hash}`) {
    return // nothing changed: don't add a duplicate history entry
  }
  if (mode === 'push') {
    window.history.pushState(null, '', url)
  } else {
    window.history.replaceState(null, '', url)
  }
}

function setOrDelete(query: URLSearchParams, name: string, value: string | null) {
  if (value === null) {
    query.delete(name)
  } else {
    query.set(name, value)
  }
}
