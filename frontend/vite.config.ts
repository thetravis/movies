import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // The frontend calls relative URLs (/api/...). In development the Vite dev server forwards them
    // to the backend, so the backend needs no CORS configuration.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
