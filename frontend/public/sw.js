// Service worker: keeps the app shell available on a flaky or missing connection. It never touches /api/:
// orders, menus and sessions always come live from the server (the boards poll them anyway).
//
//   - Page loads (navigations): network first, but give up after a few seconds and show the cached shell, so a
//     slow connection does not leave a blank screen. Every route is the same single-page app, so one cached
//     copy of "/" serves them all.
//   - /assets/* (the build's hashed scripts and styles): cache first, they never change under the same name.
//   - /images/* (meal and raw-material pictures) and other static files (icons, manifest): show the cached copy
//     at once and refresh it in the background.
//
// Bump VERSION to drop every old cache on the next visit.
const VERSION = 'v1'
const SHELL_CACHE = `rom-shell-${VERSION}`
const ASSET_CACHE = `rom-assets-${VERSION}`
const STATIC_CACHE = `rom-static-${VERSION}`
const CURRENT_CACHES = [SHELL_CACHE, ASSET_CACHE, STATIC_CACHE]
const SHELL_URLS = ['/', '/manifest.webmanifest', '/favicon.svg', '/icons/icon-192.png', '/icons/icon-512.png']
const NAVIGATION_TIMEOUT_MS = 3000

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(SHELL_URLS))
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((name) => !CURRENT_CACHES.includes(name)).map((name) => caches.delete(name))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api/')) return // live data: straight to the network, untouched

  if (request.mode === 'navigate') {
    event.respondWith(networkFirstShell(request))
  } else if (url.pathname.startsWith('/assets/')) {
    event.respondWith(cacheFirst(request, ASSET_CACHE))
  } else {
    event.respondWith(staleWhileRevalidate(request, STATIC_CACHE))
  }
})

async function networkFirstShell(request) {
  const cache = await caches.open(SHELL_CACHE)
  try {
    const response = await Promise.race([
      fetch(request),
      new Promise((_, reject) => setTimeout(() => reject(new Error('slow')), NAVIGATION_TIMEOUT_MS)),
    ])
    if (response.ok) cache.put('/', response.clone())
    return response
  } catch {
    return (await cache.match('/')) ?? Response.error()
  }
}

async function cacheFirst(request, cacheName) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok) cache.put(request, response.clone())
  return response
}

async function staleWhileRevalidate(request, cacheName) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)
  const refresh = fetch(request)
    .then((response) => {
      if (response.ok) cache.put(request, response.clone())
      return response
    })
    .catch(() => undefined)
  return cached ?? (await refresh) ?? Response.error()
}
