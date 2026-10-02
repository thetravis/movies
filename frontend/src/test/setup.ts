import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// The API is mocked with MSW: tests talk to it through the real API client, over (mocked) HTTP.
// Unhandled requests fail the test, so a test can't silently depend on a missing mock.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
  // The movie list keeps its state in the URL; jsdom would carry it over to the next test.
  window.history.replaceState(null, '', '/')
})
afterAll(() => server.close())
