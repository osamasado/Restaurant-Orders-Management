// Browser walk for the QA checklist (Documentation/qa-checklist.md): every screen, in German, English and
// Arabic, in both themes, seeded through the real APIs so each screen has real data to show.
//
//   node scripts/qa-walk.mjs
//
// Needs, running: the backend started with the dev profile (it seeds the demo staff, menu and tables) and the
// frontend in front of it. Use a throwaway stack, NOT your dev database: the walk places orders, cancels one
// and pairs devices. Prefer a production build (`npm run build`, then `vite preview`) over the dev server: the
// dev server loads modules on demand and, on a slow machine, sometimes has not rendered a page yet when the walk
// looks at it.
//
// Environment (all optional):
//   BASE        frontend URL                      default http://localhost:5174
//   API         backend API URL                   default http://localhost:8080/api
//   PLAYWRIGHT  module to require                 default "playwright"
//   BROWSER     path to a Chromium executable     default: Playwright's own
//   OUT         folder for screenshots + results  default ./qa-shots
//   LANGS       languages to walk                 default de,en,ar
//   THEMES      themes to walk                    default light,dark
//   RETRY       set to 0 to turn off the one retry of a screen group that could not run    default on
//
// For every page it checks: no console error, no unexpected failed request, no missing-translation warning, no
// horizontal overflow, nothing wider than the box it sits in, the right <html lang> and dir, Latin digits only
// in Arabic, no text clipped by an ellipsis or hidden overflow, and no picture that failed to load. It saves a screenshot per page and exits 1 if any check failed.
import { createRequire } from 'node:module'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.BASE ?? 'http://localhost:5174'
const API = process.env.API ?? 'http://localhost:8080/api'
const OUT = process.env.OUT ?? './qa-shots'
const runId = Date.now().toString(36).slice(-4) // keeps the deliberate wrong sign-ins from piling up on one name and tripping the throttle
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')

const ALL_LANGUAGES = ['de', 'en', 'ar'] // the welcome screen lists them in this order
const LANGUAGES = (process.env.LANGS ?? ALL_LANGUAGES.join(',')).split(',')
const THEMES = (process.env.THEMES ?? 'light,dark').split(',')
const locale = (lang) => JSON.parse(readFileSync(fileURLToPath(new URL(`../src/i18n/locales/${lang}.json`, import.meta.url)), 'utf8'))
const tr = (dict, path) => path.split('.').reduce((o, k) => o[k], dict)

mkdirSync(OUT, { recursive: true })

// ---------------------------------------------------------------- seed (real APIs)
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
const tables = (await api('/tables', { cookie: admin })).json
const codes = {}
for (const number of ['1', '2', '7', '9']) {
  const table = tables.find((t) => t.tableNumber === number)
  codes[number] = (await api(`/tables/${table.id}/pair`, { method: 'POST', cookie: admin })).json.pairedDeviceId
}
const menu = (await api('/guest/menu?language=EN')).json
const sizeIds = menu.flatMap((c) => c.meals.map((m) => m.sizes[0].id))
const place = async (table, size, note) =>
  (await api('/guest/orders', {
    method: 'POST',
    body: { deviceCode: codes[table], language: 'EN', paymentMethod: 'CASH', items: [{ sizeId: size, quantity: 1, ...(note ? { note } : {}) }] },
  })).json
const move = (id, status) => api(`/kitchen/orders/${id}/transition`, { method: 'POST', body: { status }, cookie: kitchen })

const served = await place('1', sizeIds[0]); for (const s of ['PREPARING', 'READY', 'SERVED']) await move(served.orderId, s)
const ready = await place('9', sizeIds[1]); for (const s of ['PREPARING', 'READY']) await move(ready.orderId, s)
const preparing = await place('2', sizeIds[2], 'ohne Zwiebeln'); await move(preparing.orderId, 'PREPARING')
await place('1', sizeIds[3])
const acked = await place('2', sizeIds[4])
await api(`/admin/orders/${acked.orderId}/cancel`, { method: 'POST', cookie: admin })
await api(`/kitchen/orders/${acked.orderId}/acknowledge-cancellation`, { method: 'POST', cookie: kitchen })
const banner = await place('9', sizeIds[5])
await api(`/admin/orders/${banner.orderId}/cancel`, { method: 'POST', cookie: admin })
console.log(`seeded: devices ${JSON.stringify(codes)}`)

// ---------------------------------------------------------------- the walk
const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})
const report = []
let runs = 0

/** One page, audited. problems is a list of strings; empty means the page passed. */
async function audit(page, label, lang, consoleProblems) {
  // Let the page finish loading first: a page that has not rendered yet has nothing to overflow or clip, so it
  // would pass every check below without proving anything.
  await page.waitForLoadState('networkidle').catch(() => {})
  await page.waitForFunction(() => document.body.innerText.trim().length >= 8, undefined, { timeout: 20000 }).catch(() => {})
  // Pictures load after the page: wait for them, or a missing one would only be noticed as an empty slot.
  await page.waitForFunction(() => [...document.images].every((img) => img.complete), undefined, { timeout: 15000 }).catch(() => {})
  const found = await page.evaluate((language) => {
    const root = document.documentElement
    const viewportWidth = window.innerWidth
    const describe = (el) => `${el.tagName.toLowerCase()}${el.className && typeof el.className === 'string' ? '.' + el.className.split(' ')[0] : ''} "${(el.textContent || '').trim().slice(0, 30)}"`
    const all = [...document.querySelectorAll('body *')]
    const overflowers = all
      .filter((el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0 && (r.right > viewportWidth + 1 || r.left < -1) })
      .slice(0, 4).map(describe)
    // A child wider than the box it sits in (a row of buttons longer than its sidebar, say): the viewport check
    // cannot see it when the box itself is inside the viewport.
    const outOfContainer = all
      .filter((el) => {
        const parent = el.parentElement
        if (!parent || parent === document.body) return false
        const cs = getComputedStyle(el)
        const pcs = getComputedStyle(parent)
        if (cs.position === 'absolute' || cs.position === 'fixed' || pcs.display === 'contents') return false
        if (['auto', 'scroll', 'hidden', 'clip'].includes(pcs.overflowX)) return false
        const r = el.getBoundingClientRect()
        const pr = parent.getBoundingClientRect()
        if (r.width === 0 || r.height === 0 || pr.width === 0) return false
        return r.right > pr.right + 1 || r.left < pr.left - 1
      })
      .slice(0, 4).map(describe)
    const clipped = all
      .filter((el) => {
        if (![...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim())) return false
        const cs = getComputedStyle(el)
        return (cs.overflow !== 'visible' || cs.textOverflow === 'ellipsis') && el.scrollWidth > el.clientWidth + 1
      })
      .slice(0, 4).map(describe)
    const brokenImages = [...document.images].filter((img) => img.complete && img.naturalWidth === 0).map((img) => img.src.slice(-60))
    return {
      brokenImages,
      lang: root.lang,
      dir: root.dir,
      textLength: document.body.innerText.trim().length,
      horizontalScroll: root.scrollWidth > root.clientWidth + 1,
      overflowers,
      outOfContainer,
      clipped,
      arabicIndicDigits: language === 'ar' && /[٠-٩۰-۹]/.test(document.body.innerText),
    }
  }, lang)
  const problems = [...consoleProblems.splice(0)]
  if (found.textLength < 8) problems.push('page is blank')
  if (found.lang !== lang) problems.push(`html lang is "${found.lang}", expected "${lang}"`)
  if (found.dir !== (lang === 'ar' ? 'rtl' : 'ltr')) problems.push(`html dir is "${found.dir}"`)
  if (found.horizontalScroll) problems.push('page scrolls horizontally')
  if (found.overflowers.length) problems.push(`sticks out of the viewport: ${found.overflowers.join(' | ')}`)
  if (found.outOfContainer.length) problems.push(`wider than its container: ${found.outOfContainer.join(' | ')}`)
  if (found.clipped.length) problems.push(`text is clipped: ${found.clipped.join(' | ')}`)
  if (found.arabicIndicDigits) problems.push('Arabic-Indic digits on an Arabic page')
  if (found.brokenImages.length) problems.push(`picture did not load: ${found.brokenImages.join(' | ')}`)
  runs += 1
  report.push({ label, problems })
  console.log(`${problems.length ? 'FAIL' : 'ok  '}  ${label}${problems.length ? '\n        ' + problems.join('\n        ') : ''}`)
}

async function open(viewport, lang, theme, device) {
  const context = await browser.newContext({ viewport })
  await context.addInitScript(([l, th, d]) => {
    localStorage.setItem('rom-language', l)
    localStorage.setItem('rom-theme', th)
    if (d) localStorage.setItem('rom-guest-device-code', d)
  }, [lang, theme, device ?? null])
  const page = await context.newPage()
  page.setDefaultTimeout(60000)
  const consoleProblems = []
  // Chrome logs every failed request as a console error with no URL, so those are judged from the response
  // instead: the only expected failures are the signed-out session check and a wrong sign-in (both 401).
  page.on('console', (m) => {
    if (m.text().includes('[i18n]')) consoleProblems.push(`console: ${m.text().slice(0, 160)}`)
    else if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) consoleProblems.push(`console: ${m.text().slice(0, 160)}`)
  })
  page.on('pageerror', (e) => consoleProblems.push(`page error: ${e.message.slice(0, 160)}`))
  page.on('response', (r) => {
    const path = new URL(r.url()).pathname
    const expected = r.status() === 401 && (path === '/api/staff/me' || path === '/api/staff/login')
    if (r.status() >= 400 && !expected) consoleProblems.push(`HTTP ${r.status()} ${path}`)
  })
  return { context, page, consoleProblems }
}
const shot = (page, name) => page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
async function staffSignIn(page, path, name, pin) {
  await page.goto(`${BASE}${path}`)
  await page.waitForSelector('.login-form input')
  await page.locator('.login-form input').nth(0).fill(name)
  await page.locator('.login-form input').nth(1).fill(pin)
  await page.click('.login-form__submit')
}
let stepDepth = 0
const retried = []
async function step(label, fn) {
  const topLevel = stepDepth === 0
  const reportMark = report.length
  const runsMark = runs
  stepDepth += 1
  try {
    await fn()
  } catch (error) {
    const reason = error.message.split('\n').slice(0, 3).join(' ').slice(0, 300)
    if (topLevel && process.env.RETRY !== '0') {
      // Run the whole group once more with a fresh browser context; whatever the failed attempt reported is dropped.
      console.log(`retry ${label}: ${reason}`)
      report.length = reportMark
      runs = runsMark
      try {
        await fn()
        retried.push(label)
        console.log(`      ${label} passed on retry`)
      } catch (second) {
        runs += 1
        const again = second.message.split('\n').slice(0, 3).join(' ').slice(0, 300)
        report.push({ label, problems: [`could not run (twice): ${again}`] })
        console.log(`FAIL  ${label}\n        could not run (twice): ${again}`)
      }
    } else {
      runs += 1
      report.push({ label, problems: [`could not run: ${reason}`] })
      console.log(`FAIL  ${label}\n        could not run: ${reason}`)
    }
  } finally {
    stepDepth -= 1
  }
}

for (const lang of LANGUAGES) {
  const dict = locale(lang)
  for (const theme of THEMES) {
    const tag = `${lang}-${theme}`

    await step(`guest ${tag}`, async () => {
      const { context, page, consoleProblems } = await open({ width: 390, height: 844 }, lang, theme, codes['7'])
      await page.goto(`${BASE}/guest`)
      await page.waitForSelector('.welcome-screen__language-button')
      await audit(page, `guest ${tag} welcome`, lang, consoleProblems); await shot(page, `guest-${tag}-1-welcome`)
      await page.locator('.welcome-screen__language-button').nth(ALL_LANGUAGES.indexOf(lang)).click()
      await page.waitForSelector('.meal-card'); await page.waitForTimeout(500)
      await audit(page, `guest ${tag} menu`, lang, consoleProblems); await shot(page, `guest-${tag}-2-menu`)
      await page.locator('.meal-card').nth(2).click()
      await page.getByRole('button', { name: tr(dict, 'guest.detail.addToOrder') }).waitFor(); await page.waitForTimeout(300)
      await audit(page, `guest ${tag} meal detail`, lang, consoleProblems); await shot(page, `guest-${tag}-3-detail`)
      await page.getByRole('button', { name: tr(dict, 'guest.detail.addToOrder') }).click()
      await page.click('.guest-screen__cart-bar')
      await page.getByRole('button', { name: new RegExp(tr(dict, 'guest.cart.choosePayment')) }).waitFor(); await page.waitForTimeout(900)
      await audit(page, `guest ${tag} cart`, lang, consoleProblems); await shot(page, `guest-${tag}-4-cart`)
      await page.getByRole('button', { name: new RegExp(tr(dict, 'guest.cart.choosePayment')) }).click()
      await page.waitForSelector('.payment-screen__method'); await page.locator('.payment-screen__method').first().click()
      await audit(page, `guest ${tag} payment`, lang, consoleProblems); await shot(page, `guest-${tag}-5-payment`)
      await page.getByRole('button', { name: new RegExp(tr(dict, 'guest.payment.confirm')) }).click()
      await page.waitForSelector('.order-confirmation-screen__number'); await page.waitForTimeout(1200)
      await audit(page, `guest ${tag} confirmation`, lang, consoleProblems); await shot(page, `guest-${tag}-6-confirmation`)
      await context.close()
    })

    await step(`kitchen ${tag}`, async () => {
      const { context, page, consoleProblems } = await open({ width: 1440, height: 900 }, lang, theme)
      await page.goto(`${BASE}/kitchen`)
      await page.waitForSelector('.login-form input')
      await audit(page, `kitchen ${tag} sign-in`, lang, consoleProblems); await shot(page, `kitchen-${tag}-1-signin`)
      await page.locator('.login-form input').nth(0).fill(`Nobody ${tag} ${runId}`)
      await page.locator('.login-form input').nth(1).fill('0000')
      await page.click('.login-form__submit'); await page.waitForSelector('.login-form__error')
      await audit(page, `kitchen ${tag} wrong PIN`, lang, consoleProblems)
      await staffSignIn(page, '/kitchen', 'M. Behr', '1234')
      await page.waitForSelector('.cancelled-banner', { timeout: 20000 }); await page.waitForSelector('.ran-out__chip', { timeout: 20000 }); await page.waitForTimeout(500)
      await audit(page, `kitchen ${tag} board`, lang, consoleProblems); await shot(page, `kitchen-${tag}-2-board`)
      await context.close()
    })

    await step(`hall ${tag}`, async () => {
      const { context, page, consoleProblems } = await open({ width: 1920, height: 1080 }, lang, theme)
      await page.goto(`${BASE}/hall`)
      await page.waitForSelector('.hall-screen__entry'); await page.waitForTimeout(400)
      await audit(page, `hall ${tag} board`, lang, consoleProblems); await shot(page, `hall-${tag}`)
      await context.close()
    })

    await step(`admin ${tag}`, async () => {
      const { context, page, consoleProblems } = await open({ width: 1440, height: 900 }, lang, theme)
      await staffSignIn(page, '/admin', 'O. Sado', '1234')
      await page.waitForSelector('.admin-screen__logout')
      for (const view of ['history', 'orders', 'meals', 'materials', 'tables', 'staff', 'settings']) {
        await step(`admin ${tag} ${view}`, async () => {
          await page.goto(`${BASE}/admin/${view}`)
          await page.locator('.admin-screen__content h2').first().waitFor({ timeout: 20000 })
          // The orders list loads after its heading: wait for real rows, or the audit would look at an empty page.
          if (view === 'orders') await page.locator('.order-row').first().waitFor({ timeout: 20000 })
          await audit(page, `admin ${tag} ${view}`, lang, consoleProblems); await shot(page, `admin-${tag}-${view}`)
        })
      }
      await step(`admin ${tag} orders cancel confirmation`, async () => {
        await page.goto(`${BASE}/admin/orders`)
        // Cancel only asks here: the walk presses "Keep" so the seeded orders stay as they are.
        await page.locator('.order-row__button--cancel').first().waitFor({ timeout: 20000 })
        await page.locator('.order-row__button--cancel').first().click()
        await page.waitForSelector('.order-row__confirm'); await page.waitForTimeout(300)
        await audit(page, `admin ${tag} orders cancel confirmation`, lang, consoleProblems); await shot(page, `admin-${tag}-orders-confirm`)
        await page.getByRole('button', { name: tr(dict, 'admin.orders.cancelKeep') }).click()
        await page.waitForSelector('.order-row__confirm', { state: 'detached' })
      })
      // The three kinds of dialog that replaced the browser's own: a delete confirmation (the walk closes it, nothing is
      // deleted), the PIN reset, and the pairing code (on a spare table the walk creates).
      await step(`admin ${tag} dialogs`, async () => {
        await page.goto(`${BASE}/admin/tables`)
        await page.waitForSelector('.table-row')
        await page.locator('.table-row').first().locator('.table-row__actions button').nth(2).click()
        await page.waitForSelector('.modal__card'); await page.waitForTimeout(300)
        await audit(page, `admin ${tag} delete confirmation`, lang, consoleProblems); await shot(page, `admin-${tag}-dialog-delete`)
        await page.keyboard.press('Escape'); await page.waitForSelector('.modal__card', { state: 'detached' })

        await page.goto(`${BASE}/admin/staff`)
        await page.waitForSelector('.staff-row')
        await page.locator('.staff-row').first().locator('.staff-row__actions button').nth(1).click()
        await page.waitForSelector('.pin-dialog'); await page.waitForTimeout(300)
        await audit(page, `admin ${tag} PIN dialog`, lang, consoleProblems); await shot(page, `admin-${tag}-dialog-pin`)
        await page.keyboard.press('Escape'); await page.waitForSelector('.modal__card', { state: 'detached' })

        const spare = `QA-${tag}-${runId}`
        await api('/tables', { method: 'POST', body: { tableNumber: spare, room: 'QA', seats: 2 }, cookie: admin })
        await page.goto(`${BASE}/admin/tables`)
        await page.locator('.table-row', { hasText: spare }).locator('.table-row__actions button').first().click()
        await page.waitForSelector('.pairing-dialog__code'); await page.waitForTimeout(300)
        await audit(page, `admin ${tag} pairing dialog`, lang, consoleProblems); await shot(page, `admin-${tag}-dialog-pairing`)
        await page.getByRole('button', { name: tr(dict, 'common.done') }).click()
      })
      await step(`admin ${tag} meal form`, async () => {
        await page.goto(`${BASE}/admin/meals`)
        await page.locator('.meal-row').first().waitFor({ timeout: 20000 })
        await page.locator('.meal-row').first().getByRole('button', { name: /DE EN AR/ }).click()
        await page.waitForSelector('.modal__card'); await page.waitForTimeout(400)
        await audit(page, `admin ${tag} meal form`, lang, consoleProblems); await shot(page, `admin-${tag}-meal-form`)
      })
      await step(`admin ${tag} staff form`, async () => {
        await page.goto(`${BASE}/admin/staff`); await page.waitForSelector('.staff-row')
        await page.getByRole('button', { name: tr(dict, 'admin.staff.add') }).click()
        await page.waitForSelector('.staff-form'); await page.waitForTimeout(300)
        await audit(page, `admin ${tag} staff form`, lang, consoleProblems); await shot(page, `admin-${tag}-staff-form`)
      })
      await context.close()
    })
  }
}

await browser.close()
const failed = report.filter((r) => r.problems.length)
writeFileSync(`${OUT}/qa-results.json`, JSON.stringify({ runs, failed: failed.length, retried, report }, null, 2))
console.log(`\n${runs - failed.length}/${runs} pages passed${retried.length ? `; ${retried.length} group(s) needed a retry: ${retried.join(', ')}` : ''}. Screenshots and qa-results.json are in ${OUT}`)
process.exit(failed.length ? 1 : 0)
