// Drives the restart of the displayed order number in a real browser (issue: admin resets the displayed order
// number from Settings), in German, English and Arabic (right to left):
//
//   - the Settings card shows the next number and, after a restart, who restarted and when
//   - "Restart at 001" opens a confirmation that says what happens; Escape and Cancel change nothing
//   - confirming restarts the series: the server says so, the next order is 001, the internal number is not
//     reset, and the card says so (the button is then disabled: nothing to restart)
//   - with an open order the same button opens an explanation with the number of open orders and NO confirm button,
//     and the server refuses a restart sent anyway (409)
//   - the audit history search for "1" finds the order of the old series and the new one
//   - the dialogs fit the screen in every language: nothing cut off, the number stays left to right in Arabic
//
//   node scripts/order-number-check.mjs
//
// Needs, running: the backend started with the dev profile (it seeds the demo staff, menu and tables) and the
// frontend in front of it. Use a throwaway stack, NOT your dev database: it places and serves orders and restarts the
// order number. Prefer a production build.
//
// Environment (all optional): BASE (default http://localhost:5174), API (default http://localhost:8080/api),
// PLAYWRIGHT (module to require), BROWSER (Chromium executable), OUT (screenshots, default ./order-number-shots).
import { createRequire } from 'node:module'
import { mkdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.BASE ?? 'http://localhost:5174'
const API = process.env.API ?? 'http://localhost:8080/api'
const OUT = process.env.OUT ?? './order-number-shots'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')
mkdirSync(OUT, { recursive: true })

const locales = Object.fromEntries(
  ['en', 'de', 'ar'].map((l) => [l, JSON.parse(readFileSync(fileURLToPath(new URL(`../src/i18n/locales/${l}.json`, import.meta.url)), 'utf8'))]),
)
const text = (lang, path, params = {}) =>
  path.split('.').reduce((o, k) => o[k], locales[lang]).replace(/\{\{(\w+)\}\}/g, (_, k) => params[k])
const pad = (n) => String(n).padStart(3, '0')

const results = []
const check = (name, ok, extra = '') => {
  results.push(ok)
  console.log(`${ok ? 'ok  ' : 'FAIL'}  ${name}${extra ? ` (${extra})` : ''}`)
}

// ---------------------------------------------------------------- setup through the real APIs
async function api(path, { method = 'GET', body, cookie, raw = false } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (raw) return res
  const payload = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${payload}`)
  return { res, json: payload ? JSON.parse(payload) : null }
}
const login = async (name) =>
  (await api('/staff/login', { method: 'POST', body: { name, pin: '1234' } })).res.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')
const admin = await login('O. Sado')
const kitchen = await login('M. Behr')
const table = (await api('/tables', { cookie: admin })).json.find((t) => t.tableNumber === '7')
const deviceCode = (await api(`/tables/${table.id}/pair`, { method: 'POST', cookie: admin })).json.pairedDeviceId
const sizeId = (await api('/guest/menu?language=EN')).json.flatMap((c) => c.meals)[0].sizes[0].id

const status = () => api('/settings/order-number', { cookie: admin }).then((r) => r.json)
const placeOrder = () =>
  api('/guest/orders', { method: 'POST', body: { deviceCode, language: 'EN', paymentMethod: 'CASH', items: [{ sizeId, quantity: 1, note: null }] } }).then((r) => r.json)
const step = (orderId, to) => api(`/admin/orders/${orderId}/transition`, { method: 'POST', body: { status: to }, cookie: admin })
const serve = async (orderId) => {
  for (const to of ['PREPARING', 'READY', 'SERVED']) await step(orderId, to)
}
async function serveEveryOpenOrder() {
  const page = (await api('/admin/orders?size=100', { cookie: admin })).json
  for (const row of page.orders ?? page.content ?? []) {
    const rest = { SUBMITTED: ['PREPARING', 'READY', 'SERVED'], PREPARING: ['READY', 'SERVED'], READY: ['SERVED'] }[row.status]
    for (const to of rest ?? []) await step(row.orderId, to)
  }
}
/** Makes sure the series is part way through (the next number is above 001), with nothing open. */
async function seriesPartWay() {
  await serveEveryOpenOrder()
  if ((await status()).nextDisplayNumber === 1) await serve((await placeOrder()).orderId)
}

const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})

/** Overflow, clipping and off-screen parts inside the element: the "no clipped text" check. */
const layoutProblems = (page, selector) =>
  page.evaluate((sel) => {
    const root = document.querySelector(sel)
    if (!root) return ['missing: ' + sel]
    const problems = []
    const view = { w: window.innerWidth, h: window.innerHeight }
    const box = root.getBoundingClientRect()
    if (box.left < -0.5 || box.right > view.w + 0.5 || box.top < -0.5 || box.bottom > view.h + 0.5) problems.push('dialog outside the screen')
    for (const el of [root, ...root.querySelectorAll('*')]) {
      const style = getComputedStyle(el)
      if (style.display === 'none' || style.visibility === 'hidden') continue
      if (el.scrollWidth > el.clientWidth + 1 && style.overflowX !== 'visible' && el.clientWidth > 0) problems.push('clipped: ' + el.className)
      if (el.scrollWidth > el.clientWidth + 1 && style.overflowX === 'visible' && el.clientWidth > 0 && el.children.length === 0) problems.push('overflowing text: ' + (el.textContent ?? '').slice(0, 30))
    }
    return problems
  }, selector)

for (const lang of ['en', 'de', 'ar']) {
  console.log(`\n--- ${lang.toUpperCase()}`)
  const context = await browser.newContext({ viewport: { width: lang === 'de' ? 1100 : 1280, height: 800 } })
  await context.addInitScript(([l]) => {
    localStorage.setItem('rom-language', l)
    localStorage.setItem('rom-theme', 'light')
  }, [lang])
  const page = await context.newPage()
  page.setDefaultTimeout(20000)
  await page.goto(`${BASE}/admin`)
  await page.locator('.login-form input').nth(0).fill('O. Sado')
  await page.locator('.login-form input').nth(1).fill('1234')
  await page.click('.login-form__submit')
  await page.waitForSelector('.admin-sidebar__logout')
  await page.goto(`${BASE}/admin/settings`)
  await page.locator('.order-number-card').waitFor()

  check(`${lang}: the page is ${lang === 'ar' ? 'right to left' : 'left to right'}`, (await page.evaluate(() => document.documentElement.dir)) === (lang === 'ar' ? 'rtl' : 'ltr'))

  // ---- 1. the card, with the series part way through
  await seriesPartWay()
  await page.reload()
  await page.locator('.order-number-card__value').waitFor()
  let state = await status()
  const shown = (await page.locator('.order-number-card__value').innerText()).trim()
  check(`${lang}: the card shows the next order number`, shown === pad(state.nextDisplayNumber), `${shown} vs ${pad(state.nextDisplayNumber)}`)
  check(`${lang}: the number is left to right and in the mono face`, await page.locator('.order-number-card__value').evaluate((el) => getComputedStyle(el).direction === 'ltr' && getComputedStyle(el).fontFamily.includes('Plex Mono')))
  check(`${lang}: the card is titled and the restart button is enabled`, (await page.locator('.order-number-card .settings-card__title').innerText()) === text(lang, 'admin.settings.orderNumber.title') && (await page.locator('.order-number-card__restart').isEnabled()))
  await page.screenshot({ path: `${OUT}/${lang}-card.png` })

  // ---- 2. the confirmation: says what happens, and Escape / Cancel change nothing
  const before = state.nextDisplayNumber
  await page.locator('.order-number-card__restart').click()
  const dialog = page.locator('[role="dialog"]')
  await dialog.waitFor()
  check(`${lang}: the confirmation says what will happen`, (await dialog.innerText()).includes(text(lang, 'admin.settings.orderNumber.dialogMessage')))
  check(`${lang}: the confirmation is titled`, (await dialog.locator('h2, h1, [id]').first().innerText()).includes(text(lang, 'admin.settings.orderNumber.dialogTitle')))
  const problems = await layoutProblems(page, '[role="dialog"]')
  check(`${lang}: the confirmation fits (nothing cut off)`, problems.length === 0, problems.join('; '))
  await page.waitForTimeout(500) // the dialog fades in
  await page.screenshot({ path: `${OUT}/${lang}-confirm.png` })
  await page.keyboard.press('Escape')
  await dialog.waitFor({ state: 'detached' })
  check(`${lang}: Escape closes it and nothing is restarted`, (await status()).nextDisplayNumber === before)
  await page.locator('.order-number-card__restart').click()
  await dialog.waitFor()
  await dialog.locator('button', { hasText: text(lang, 'common.cancel') }).click()
  await dialog.waitFor({ state: 'detached' })
  check(`${lang}: Cancel closes it and nothing is restarted`, (await status()).nextDisplayNumber === before)

  // ---- 3. an open order: explained, no confirm button, the server refuses too
  const open = await placeOrder()
  await page.locator('.order-number-card__restart').click()
  await dialog.waitFor()
  const blockedText = await dialog.innerText()
  check(`${lang}: with an open order the dialog explains why, with the count`, blockedText.includes(text(lang, 'admin.settings.orderNumber.blockedMessage', { count: 1 })))
  check(`${lang}: and offers no confirm button`, (await dialog.locator('button', { hasText: text(lang, 'admin.settings.orderNumber.confirm') }).count()) === 0)
  const blockedProblems = await layoutProblems(page, '[role="dialog"]')
  check(`${lang}: the explanation fits (nothing cut off)`, blockedProblems.length === 0, blockedProblems.join('; '))
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${OUT}/${lang}-blocked.png` })
  const refused = await api('/settings/order-number/reset', { method: 'POST', cookie: admin, raw: true })
  check(`${lang}: a restart sent anyway is refused by the server (409)`, refused.status === 409, String(refused.status))
  check(`${lang}: and the series is untouched`, (await status()).nextDisplayNumber === before + 1)
  await dialog.locator('button', { hasText: text(lang, 'common.close') }).first().click()
  await dialog.waitFor({ state: 'detached' })
  await serve(open.orderId)

  // ---- 4. the restart itself
  await page.reload()
  await page.locator('.order-number-card__restart').click()
  await dialog.waitFor()
  await dialog.locator('button', { hasText: text(lang, 'admin.settings.orderNumber.confirm') }).click()
  await dialog.waitFor({ state: 'detached' })
  await page.locator('.settings-view__success').waitFor()
  state = await status()
  check(`${lang}: confirming restarts the series (server)`, state.nextDisplayNumber === 1 && state.openOrders === 0)
  check(`${lang}: the card shows 001 and says it was restarted`, (await page.locator('.order-number-card__value').innerText()).trim() === '001' && (await page.locator('.settings-view__success').innerText()) === text(lang, 'admin.settings.orderNumber.restarted'))
  check(`${lang}: the card names who restarted it`, (await page.locator('.order-number-card').innerText()).includes('O. Sado') && state.lastReset?.staffName === 'O. Sado')
  check(`${lang}: the restart button is disabled, there is nothing to restart`, await page.locator('.order-number-card__restart').isDisabled())
  await page.screenshot({ path: `${OUT}/${lang}-restarted.png` })

  // ---- 5. the next order is 001 on every surface, and the internal number carries on
  const next = await placeOrder()
  check(`${lang}: the next order is 001 for the guest`, next.orderNumber === 1, String(next.orderNumber))
  const kitchenOrders = (await api('/kitchen/orders', { cookie: kitchen })).json
  check(`${lang}: and 001 in the kitchen`, kitchenOrders.some((o) => o.orderId === next.orderId && o.orderNumber === 1))
  await step(next.orderId, 'PREPARING')
  const hall = (await api('/hall/orders')).json
  check(`${lang}: and 001 on the hall board`, hall.preparing.some((e) => e.orderNumber === 1))

  // ---- 6. the audit history search matches the displayed number: the old 001 and the new 001
  await page.goto(`${BASE}/admin/history`)
  await page.locator('.history-view__search input').fill('1')
  await page.waitForTimeout(1200)
  const cards = await page.locator('.history-card').count()
  const body = await page.locator('.history-view').innerText()
  const matches = (body.match(/001/g) ?? []).length
  check(`${lang}: searching 1 in the history finds the old series and the new one`, matches >= 2 && cards >= 2, `${matches} x 001`)
  await page.screenshot({ path: `${OUT}/${lang}-history.png` })

  for (const to of ['READY', 'SERVED']) await step(next.orderId, to)
  await context.close()
}

await browser.close()
const passed = results.filter(Boolean).length
console.log(`\n${passed}/${results.length} checks passed. Screenshots are in ${OUT}`)
process.exit(passed === results.length ? 0 : 1)
