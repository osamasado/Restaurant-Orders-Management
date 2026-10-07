import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The API and the uploaded pictures live on the backend. The dev server and the production preview
// (`npm run build`, then `npm run preview`: the build the installable app and its service worker come from)
// both forward them there, so the app can be tried either way against a locally running backend.
const backend = {
  '/api': {
    target: 'http://localhost:8080'
  },
  '/images': {
    target: 'http://localhost:8080'
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: backend
  },
  preview: {
    proxy: backend
  }
})
