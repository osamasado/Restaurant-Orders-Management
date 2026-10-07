// Renders the seed-art SVGs to PNG with headless Chromium.
//
//   node render.mjs [--out <dir>] [--small] [file.svg ...]
//
// With no files, renders everything under meals/ and raw-materials/. By default the PNGs go to
// ../src/main/resources/seed-images/<folder>/<slug>.png (what DemoDataSeeder attaches); --out writes
// <dir>/<folder>/<slug>.png instead (previews). --small also writes <slug>.46.png, the size of the admin list.
//
// Environment: PLAYWRIGHT (module to require, default "playwright") and BROWSER (Chromium executable).
import { createRequire } from 'node:module'
import { mkdirSync, readdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')

const args = process.argv.slice(2)
let out = resolve(here, '../src/main/resources/seed-images')
let small = false
const files = []
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--out') out = resolve(args[++i])
  else if (args[i] === '--small') small = true
  else files.push(args[i])
}
const SIZES = { meals: [800, 600], 'raw-materials': [400, 400] }
if (files.length === 0) {
  for (const folder of Object.keys(SIZES)) {
    for (const f of readdirSync(join(here, folder)).filter((n) => n.endsWith('.svg'))) files.push(`${folder}/${f}`)
  }
}

const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})
let count = 0
for (const file of files) {
  const [folder, name] = file.split('/')
  const slug = name.replace(/\.svg$/, '')
  const [width, height] = SIZES[folder]
  const page = await browser.newPage({ viewport: { width, height } })
  // Open the SVG itself (not an <img> inside setContent: a page without a URL may not load file:// images).
  await page.goto(pathToFileURL(resolve(here, file)).href)
  mkdirSync(join(out, folder), { recursive: true })
  await page.screenshot({ path: join(out, folder, `${slug}.png`) })
  if (small) {
    // The admin list shows the picture at 46px, cropped to a square (object-fit: cover).
    const holder = join(tmpdir(), `seed-art-${slug}.html`)
    writeFileSync(
      holder,
      `<html><body style="margin:0"><img src="${pathToFileURL(resolve(here, file)).href}" style="display:block;width:46px;height:46px;object-fit:cover"></body></html>`,
    )
    await page.setViewportSize({ width: 46, height: 46 })
    await page.goto(pathToFileURL(holder).href)
    await page.waitForFunction(() => document.images[0].complete)
    await page.screenshot({ path: join(out, folder, `${slug}.46.png`) })
  }
  await page.close()
  count++
}
await browser.close()
console.log(`rendered ${count} image(s) to ${out}`)
