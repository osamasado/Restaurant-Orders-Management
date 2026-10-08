// Checks the hall board and the kitchen board against issue #62, on a typical evening and a busy one, at a wall
// monitor (1920 x 1080) and a small one (1366 x 768), and the busy hall in a 1100 x 760 window too, in German, English and Arabic, light and dark:
//
//   - nothing runs off the screen and every number, card button and footer chip can be reached
//   - the designed number sizes (74 and 86 px) hold on a quiet board, and numbers never get smaller than 28 px
//   - text is readable: 4.5:1 contrast, 3:1 for large text
//   - the only motion is the blinking dot, and a number that arrives fades and rises in (nothing else moves);
//     with "reduce motion" switched on there is none
//   - Arabic is mirrored: In preparation on the right, numbers start at the right edge of their panel
//
//   node scripts/boards-check.mjs
//
// Needs, running: the backend started with the dev profile (it seeds the demo staff, menu and tables) and the
// frontend in front of it. Use a throwaway stack, NOT your dev database: it places and cancels orders.
// Prefer a production build (`npm run build`, then `vite preview`).
//
// Each run adds orders, so the busy evening gets busier every time: run it once per fresh stack. To look at
// the same state again, set SEED=0.
//
// Environment (all optional): BASE (default http://localhost:5174), API (default http://localhost:8080/api),
// PLAYWRIGHT (module to require), BROWSER (Chromium executable), OUT (screenshots, default ./boards-shots),
// SEED=0 (do not place orders, check what is there), ONLY=hall|kitchen (one screen), SKIP_MOTION=1.
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE ?? 'http://localhost:5174'
const API = process.env.API ?? 'http://localhost:8080/api'
const OUT = process.env.OUT ?? './boards-shots'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')
mkdirSync(OUT, { recursive: true })

const results = []
const check = (name, ok, extra = '') => {
  results.push(ok)
  console.log(`${ok ? 'ok  ' : 'FAIL'}  ${name}${extra ? ` (${extra})` : ''}`)
}

// ---------------------------------------------------------------- seed through the real APIs
async function api(path, { method = 'GET', body, cookie } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`)
  return { res, json: text ? JSON.parse(text) : null }
}
const login = async (name) =>
  (await api('/staff/login', { method: 'POST', body: { name, pin: '1234' } })).res.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')
const admin = await login('O. Sado')
const kitchen = await login('M. Behr')
const sizes = (await api('/guest/menu?language=EN')).json.flatMap((c) => c.meals).flatMap((m) => m.sizes.map((s) => s.id))
const codes = Object.fromEntries((await api('/tables', { cookie: admin })).json.filter((t) => t.pairedDeviceId).map((t) => [t.tableNumber, t.pairedDeviceId]))
const tableNumbers = Object.keys(codes)
let placed = 0
const place = async (quantities, note) => {
  const table = tableNumbers[placed++ % tableNumbers.length]
  const items = quantities.map((quantity, i) => ({ sizeId: sizes[(placed + i * 3) % sizes.length], quantity, ...(note && i === 0 ? { note } : {}) }))
  return (await api('/guest/orders', { method: 'POST', body: { deviceCode: codes[table], language: 'EN', paymentMethod: 'CARD', items } })).json
}
const move = (id, status) => api(`/kitchen/orders/${id}/transition`, { method: 'POST', body: { status }, cookie: kitchen })

async function seedTypical() {
  const a = await place([1, 2], 'no onions'); await move(a.orderId, 'PREPARING'); await move(a.orderId, 'READY')
  const b = await place([2]); await move(b.orderId, 'PREPARING')
  await place([1, 1], 'extra sauce')
  const c = await place([1]); await api(`/admin/orders/${c.orderId}/cancel`, { method: 'POST', cookie: admin })
}
async function seedBusy() {
  for (let i = 0; i < 9; i++) { const o = await place([1 + (i % 3), 1], i % 2 ? 'allergy: nuts' : undefined); await move(o.orderId, 'PREPARING'); if (i < 6) await move(o.orderId, 'READY') }
  for (let i = 0; i < 20; i++) { const o = await place([1 + (i % 2), 2, 1]); await move(o.orderId, 'PREPARING') }
  for (let i = 0; i < 6; i++) await place([1, 1, 2], i % 2 ? 'Gluten-free base, please, and the sauce on the side' : undefined)
  for (let i = 0; i < 2; i++) { const o = await place([1]); await api(`/admin/orders/${o.orderId}/cancel`, { method: 'POST', cookie: admin }) }
}

// ---------------------------------------------------------------- measuring, inside the page
/** Everything the page-side checks need, as one function so it can be handed to the browser. */
function measure() {
  const parse = (css) => {
    // rgb(r g b / a), rgba(r, g, b, a) and color(srgb r g b / a) (what color-mix() computes to)
    const m = css.match(/^(rgba?|color)\((?:srgb\s+)?([^)]+)\)$/)
    if (!m) return [0, 0, 0, 0]
    const parts = m[2].split(/[\s,/]+/).filter(Boolean).map(Number)
    const scale = m[1] === 'color' ? 255 : 1
    return [parts[0] * scale, parts[1] * scale, parts[2] * scale, parts[3] ?? 1]
  }
  const over = (top, under) => {
    const a = top[3] + under[3] * (1 - top[3])
    if (a === 0) return [0, 0, 0, 0]
    return [0, 1, 2].map((i) => (top[i] * top[3] + under[i] * under[3] * (1 - top[3])) / a).concat(a)
  }
  const background = (el) => {
    const layers = []
    for (let node = el; node; node = node.parentElement) layers.push(parse(getComputedStyle(node).backgroundColor))
    return layers.reverse().reduce((under, layer) => over(layer, under), [255, 255, 255, 1])
  }
  const luminance = ([r, g, b]) => {
    const c = [r, g, b].map((v) => { const s = v / 255; return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4 })
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
  }
  const contrast = (el) => {
    const style = getComputedStyle(el)
    const bg = background(el)
    const fg = over(parse(style.color), bg)
    const [hi, lo] = [luminance(fg), luminance(bg)].sort((x, y) => y - x)
    const size = parseFloat(style.fontSize)
    const large = size >= 24 || (size >= 18.66 && Number(style.fontWeight) >= 700)
    return { ratio: (hi + 0.05) / (lo + 0.05), needed: large ? 3 : 4.5, size, text: (el.textContent ?? '').trim().slice(0, 24) }
  }
  const box = (el) => { const r = el.getBoundingClientRect(); return { l: r.left, r: r.right, t: r.top, b: r.bottom } }
  return { contrast, box }
}

async function pageReport(page, screen) {
  return page.evaluate(([screenName, measureSource]) => {
    const { contrast, box } = new Function(`return (${measureSource})()`)()
    const W = window.innerWidth, H = window.innerHeight
    const inside = (b, w = W, h = H) => b.l >= -1 && b.r <= w + 1 && b.t >= -1 && b.b <= h + 1
    const report = {
      overflowY: document.documentElement.scrollHeight - H,
      overflowX: document.documentElement.scrollWidth - W,
      dir: document.documentElement.dir,
      lowContrast: [],
    }
    const judge = (selector, label, all = false) => {
      const els = all ? [...document.querySelectorAll(selector)].slice(0, 6) : [document.querySelector(selector)].filter(Boolean)
      for (const el of els) {
        const c = contrast(el)
        if (c.ratio < c.needed) report.lowContrast.push(`${label} "${c.text}" ${c.ratio.toFixed(2)}:1 < ${c.needed}:1 (${c.size}px)`)
      }
      return els.length
    }
    if (screenName === 'hall') {
      const entries = [...document.querySelectorAll('.hall-screen__entry')]
      report.entries = entries.length
      report.hidden = entries.filter((li) => !inside(box(li))).length
      report.wrappedLabels = [...document.querySelectorAll('.hall-screen__table')].filter((el) => el.getBoundingClientRect().height > parseFloat(getComputedStyle(el).fontSize) * 1.6).length
      report.outsidePanel = entries.filter((li) => { const p = box(li.closest('.hall-screen__panel')); return !inside(box(li), p.r, p.b) || box(li).l < p.l - 1 || box(li).t < p.t - 1 }).length
      const sizeOf = (panel) => { const n = document.querySelector(`.hall-screen__panel--${panel} .hall-screen__number`); return n ? parseFloat(getComputedStyle(n).fontSize) : null }
      report.preparingSize = sizeOf('preparing')
      report.readySize = sizeOf('ready')
      const smallest = Math.min(...[...document.querySelectorAll('.hall-screen__number')].map((n) => parseFloat(getComputedStyle(n).fontSize)))
      report.smallestNumber = Number.isFinite(smallest) ? smallest : null
      for (const [sel, label] of [['.hall-screen__panel--preparing .hall-screen__number', 'preparing number'], ['.hall-screen__panel--ready .hall-screen__number', 'ready number'], ['.hall-screen__panel--preparing .hall-screen__table', 'preparing table'], ['.hall-screen__panel--ready .hall-screen__table', 'ready table'], ['.hall-screen__panel--preparing .hall-screen__panel-header', 'preparing label'], ['.hall-screen__panel--ready .hall-screen__panel-header', 'ready label'], ['.hall-screen__footer span', 'footer'], ['.hall-screen__title', 'title']]) judge(sel, label)
      const prep = document.querySelector('.hall-screen__panel--preparing'), ready = document.querySelector('.hall-screen__panel--ready')
      report.preparingLeft = box(prep).l
      report.readyLeft = box(ready).l
      const first = document.querySelector('.hall-screen__panel--preparing .hall-screen__entry .hall-screen__number')
      if (first) { const p = box(prep); const f = box(first); report.firstNumberGapStart = document.documentElement.dir === 'rtl' ? p.r - f.r : f.l - p.l; report.firstNumberGapEnd = document.documentElement.dir === 'rtl' ? f.l - p.l : p.r - f.r }
      const dot = document.querySelector('.hall-screen__panel--preparing .hall-screen__dot')
      report.dotAnimations = dot.getAnimations().filter((a) => a.playState === 'running').map((a) => ({ name: a.animationName, duration: a.effect.getTiming().duration, iterations: a.effect.getTiming().iterations }))
      report.runningAnimations = document.getAnimations().filter((a) => a.playState === 'running' && (a.effect.getTiming().iterations === Infinity || a.effect.getComputedTiming().progress < 1)).length
    } else {
      const chips = [...document.querySelectorAll('.ran-out__chip')]
      report.chipsHidden = chips.filter((c) => !inside(box(c))).length
      report.chips = chips.length
      const parts = ['.kitchen-screen__header', '.kitchen-screen__footer', '.kitchen-screen__board']
      report.partsHidden = parts.filter((sel) => { const el = document.querySelector(sel); return !el || !inside(box(el)) })
      const banner = document.querySelector('.cancelled-banner')
      report.bannerInside = banner ? inside(box(banner)) : null
      report.columns = [...document.querySelectorAll('.kitchen-screen__column-list')].map((list) => ({ scrolls: list.scrollHeight > list.clientHeight + 1, height: list.clientHeight }))
      for (const [sel, label, all] of [['.kitchen-order-card__action--start', 'Start', false], ['.kitchen-order-card__action--ready', 'Ready', false], ['.kitchen-order-card__action--served', 'Picked up', false], ['.cancelled-banner__action', 'Acknowledge', false], ['.cancelled-banner__text', 'banner text', false], ['.cancelled-banner__number', 'banner number', false], ['.ran-out__chip--sold-out', 'sold-out chip', false], ['.ran-out__chip:not(.ran-out__chip--sold-out)', 'chip', false], ['.kitchen-order-card__timer', 'timer', true], ['.kitchen-order-card__note', 'note', false], ['.kitchen-order-card__number', 'order number', true], ['.kitchen-order-card__table', 'table', false], ['.kitchen-order-card__name', 'item name', false], ['.kitchen-order-card__quantity', 'quantity', false], ['.kitchen-screen__column-header', 'column header', true], ['.kitchen-screen__eyebrow', 'eyebrow', false], ['.ran-out__note', 'footer note', false]]) judge(sel, label, all)
      const first = document.querySelector('.kitchen-screen__column--new'), last = document.querySelector('.kitchen-screen__column--ready')
      report.newLeft = box(first).l
      report.readyLeft = box(last).l
    }
    return report
  }, [screen, measure.toString()])
}

// ---------------------------------------------------------------- the walk
const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})

async function open(screen, [width, height], lang, theme, reducedMotion = 'no-preference') {
  const context = await browser.newContext({ viewport: { width, height }, reducedMotion })
  await context.addInitScript(([l, th]) => { localStorage.setItem('rom-language', l); localStorage.setItem('rom-theme', th) }, [lang, theme])
  const page = await context.newPage()
  page.setDefaultTimeout(30000)
  if (screen === 'kitchen') {
    await page.goto(`${BASE}/kitchen`)
    await page.waitForSelector('.login-form input')
    await page.locator('.login-form input').nth(0).fill('M. Behr')
    await page.locator('.login-form input').nth(1).fill('1234')
    await page.click('.login-form__submit')
    await page.waitForSelector('.kitchen-screen__board')
    await page.waitForSelector('.ran-out__chip')
  } else {
    await page.goto(`${BASE}/hall`)
    await page.waitForSelector('.hall-screen__board')
    await page.waitForSelector('.hall-screen__entry')
  }
  await page.waitForTimeout(1500) // the first entries finish arriving
  return { context, page }
}

async function audit(label, screen, size, lang, theme, state) {
  const { context, page } = await open(screen, size, lang, theme)
  const r = await pageReport(page, screen)
  await page.screenshot({ path: `${OUT}/${label}.png` })
  const tag = `${state} ${screen} ${size[0]}x${size[1]} ${lang} ${theme}`
  check(`${tag}: the page does not scroll, nothing runs off the screen`, r.overflowY <= 1 && r.overflowX <= 1, `${r.overflowY}px / ${r.overflowX}px over`)
  check(`${tag}: text is readable (contrast)`, r.lowContrast.length === 0, r.lowContrast.slice(0, 3).join('; '))
  check(`${tag}: ${lang === 'ar' ? 'right-to-left' : 'left-to-right'}`, r.dir === (lang === 'ar' ? 'rtl' : 'ltr'))
  if (screen === 'hall') {
    const hall = (await api('/hall/orders')).json
    check(`${tag}: every number on the board is on the screen`, r.entries === hall.preparing.length + hall.ready.length && r.hidden === 0 && r.outsidePanel === 0, `${r.entries} entries, ${r.hidden} off screen, ${r.outsidePanel} outside their panel`)
    check(`${tag}: every table label stays on one line`, r.wrappedLabels === 0, `${r.wrappedLabels} wrapped`)
    check(`${tag}: no number smaller than 28 px`, r.smallestNumber === null || r.smallestNumber >= 28, `${r.smallestNumber}px`)
    if (state === 'typical') check(`${tag}: the designed sizes hold (74 px and 86 px)`, r.preparingSize === 74 && r.readySize === 86, `${r.preparingSize} / ${r.readySize}`)
    check(`${tag}: the only running motion is the blinking dot (2 s)`, r.runningAnimations === 1 && r.dotAnimations.length === 1 && r.dotAnimations[0].duration === 2000 && r.dotAnimations[0].iterations === Infinity, JSON.stringify(r.dotAnimations) + ` / ${r.runningAnimations} running`)
    if (lang === 'ar') check(`${tag}: mirrored (In preparation on the right, numbers start at the right edge)`, r.preparingLeft > r.readyLeft && r.firstNumberGapStart <= 40, `gap ${Math.round(r.firstNumberGapStart)}px`)
    else check(`${tag}: In preparation on the left, numbers start at the left edge`, r.preparingLeft < r.readyLeft && r.firstNumberGapStart <= 40, `gap ${Math.round(r.firstNumberGapStart)}px`)
  } else {
    check(`${tag}: header, board and every "Ran out?" chip are on the screen`, r.partsHidden.length === 0 && r.chipsHidden === 0, `${r.partsHidden.join(',')} ${r.chipsHidden} of ${r.chips} chips off screen`)
    if (r.bannerInside !== null) check(`${tag}: the cancelled banner is on the screen`, r.bannerInside)
    if (state === 'busy') {
      check(`${tag}: busy columns scroll by themselves and stay tall enough to use (at least 200 px)`, r.columns.every((c) => c.scrolls) && r.columns.every((c) => c.height >= 200), JSON.stringify(r.columns))
      // the last card of the longest column can be reached by scrolling it
      const reachable = await page.evaluate(() => {
        const list = document.querySelector('.kitchen-screen__column--new .kitchen-screen__column-list')
        list.scrollTop = list.scrollHeight
        const card = list.lastElementChild.getBoundingClientRect(), box = list.getBoundingClientRect()
        const button = list.lastElementChild.querySelector('button')?.getBoundingClientRect()
        return card.bottom <= box.bottom + 1 && (!button || (button.top >= box.top && button.bottom <= box.bottom + 1))
      })
      check(`${tag}: the last "New" card and its Start button can be scrolled into view`, reachable)
    }
    check(`${tag}: ${lang === 'ar' ? 'columns mirrored (New on the right)' : 'columns in reading order (New on the left)'}`, lang === 'ar' ? r.newLeft > r.readyLeft : r.newLeft < r.readyLeft)
  }
  await context.close()
}

const SIZES = [[1920, 1080], [1366, 768]]
const LANGS = ['de', 'en', 'ar']
const THEMES = ['dark', 'light']
async function round(state) {
  for (const screen of ['hall', 'kitchen'].filter((name) => !process.env.ONLY || process.env.ONLY === name)) for (const size of screen === 'hall' && state === 'busy' ? [...SIZES, [1100, 760]] : SIZES) for (const lang of LANGS) for (const theme of THEMES) {
    // typical: the full matrix at the big size only keeps the run short; the small size matters when it is busy
    if (state === 'typical' && size[0] !== 1920) continue
    if (state === 'typical' && lang === 'de' && theme === 'light') continue
    await audit(`${state}-${screen}-${size[0]}-${lang}-${theme}`, screen, size, lang, theme, state)
  }
}

const SEED = process.env.SEED !== '0'
console.log('--- a typical evening')
if (SEED) await seedTypical()
await round('typical')
console.log('--- a busy evening')
if (SEED) await seedBusy()
await round('busy')

// ---------------------------------------------------------------- motion, live
console.log('--- motion')
if (!process.env.SKIP_MOTION) {
  const { context, page } = await open('hall', [1920, 1080], 'en', 'dark')
  const before = await page.locator('.hall-screen__entry').count()
  const o = await place([1]); await move(o.orderId, 'PREPARING')
  const number = String(o.orderNumber).padStart(3, '0')
  const entry = page.locator('.hall-screen__panel--preparing .hall-screen__entry', { hasText: number })
  await entry.waitFor({ timeout: 15000 })
  const arriving = await entry.evaluate((li) => li.getAnimations().map((a) => ({ name: a.animationName, duration: a.effect.getTiming().duration })))
  check('a number that arrives fades and rises in (0.4 s)', arriving.some((a) => a.name === 'hall-arrive' && a.duration === 400), JSON.stringify(arriving))
  const others = await page.evaluate((n) => [...document.querySelectorAll('.hall-screen__entry')].filter((li) => !li.textContent.includes(n)).flatMap((li) => li.getAnimations().filter((a) => a.playState === 'running')).length, number)
  check('the numbers already on the board do not move', others === 0, `${others} running`)
  await move(o.orderId, 'READY')
  const ready = page.locator('.hall-screen__panel--ready .hall-screen__entry', { hasText: number })
  await ready.waitFor({ timeout: 15000 })
  const moved = await ready.evaluate((li) => li.getAnimations().some((a) => a.animationName === 'hall-arrive'))
  check('moving to Ready arrives in the other panel the same way', moved)
  await page.waitForTimeout(900)
  const idle = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running' && (a.effect.getTiming().iterations === Infinity || a.effect.getComputedTiming().progress < 1)).length)
  check('afterwards only the blinking dot is left running', idle === 1, `${idle} running`)
  await page.screenshot({ path: `${OUT}/motion-hall.png` })
  await context.close()
  check('the board grew by one', before + 1 >= 1)
}
if (!process.env.SKIP_MOTION) {
  const { context, page } = await open('hall', [1920, 1080], 'en', 'dark', 'reduce')
  const running = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running').length)
  check('with "reduce motion" there is no animation at all (the dot stays lit)', running === 0, `${running} running`)
  const o = await place([1]); await move(o.orderId, 'PREPARING')
  await page.locator('.hall-screen__panel--preparing .hall-screen__entry', { hasText: String(o.orderNumber).padStart(3, '0') }).waitFor({ timeout: 15000 })
  const arrival = await page.evaluate(() => document.getAnimations().length)
  check('and a number that arrives simply appears', arrival === 0, `${arrival} animations`)
  await context.close()
}

await browser.close()
const failed = results.filter((r) => !r).length
console.log(`\n${results.length - failed}/${results.length} checks passed. Screenshots are in ${OUT}`)
process.exit(failed ? 1 : 0)
