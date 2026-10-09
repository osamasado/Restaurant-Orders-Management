// Renders the PWA icons from public/favicon.svg (the app icon: a coral serving cloche on charcoal).
//
//   node scripts/render-icons.mjs
//
// Writes public/icons/icon-192.png, icon-512.png, icon-maskable-512.png and apple-touch-icon.png. The artwork
// is full-bleed and keeps its drawing inside the central 80%, so the same picture serves as the "maskable"
// icon (Android crops it to a circle or rounded square).
//
// Environment: PLAYWRIGHT (module to require, default "playwright") and BROWSER (Chromium executable).
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')

const source = pathToFileURL(fileURLToPath(new URL('../public/favicon.svg', import.meta.url))).href
const outDir = fileURLToPath(new URL('../public/icons/', import.meta.url))
mkdirSync(outDir, { recursive: true })

const ICONS = [
  ['icon-192.png', 192],
  ['icon-512.png', 512],
  ['icon-maskable-512.png', 512],
  ['apple-touch-icon.png', 180],
]

const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})
for (const [name, size] of ICONS) {
  const page = await browser.newPage({ viewport: { width: size, height: size } })
  await page.goto(source) // the SVG itself, scaled to the viewport by its viewBox
  await page.screenshot({ path: outDir + name })
  await page.close()
}
await browser.close()
console.log(`rendered ${ICONS.length} icons to ${outDir}`)
