import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './i18n/i18n'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Installable app: the service worker keeps the app shell available on a flaky connection (see public/sw.js).
// Only in the production build, so the dev server keeps serving fresh modules. Registration starts a few seconds
// after the page has loaded: a page that is left sooner never starts it, instead of logging an interrupted
// fetch of the worker script.
const SERVICE_WORKER_DELAY_MS = 3000
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    window.setTimeout(() => {
      navigator.serviceWorker.register('/sw.js').catch(() => undefined)
    }, SERVICE_WORKER_DELAY_MS)
  })
}
