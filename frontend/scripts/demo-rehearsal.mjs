// Rehearses the demo from Documentation/demo-script.md in a real browser, with timings: an order placed on the
// guest phone appears on the kitchen screen, moves across the hall board as the kitchen advances it, the guest's
// own timeline follows, an admin cancels another order (kitchen banner, hall board), and a sold-out meal leaves
// the guest menu. The three screens (guest phone, kitchen, hall) are also captured side by side after each step.
//
//   node scripts/demo-rehearsal.mjs
//
// Needs, running: the backend started with the dev profile (it seeds the demo staff, menu and tables) and the
// frontend in front of it. Use a throwaway stack, NOT your dev database: it places orders and cancels one.
// Prefer a production build (`npm run build`, then `vite preview`).
//
// Environment (all optional):
//   BASE        frontend URL                      default http://localhost:5174
//   API         backend API URL                   default http://localhost:8080/api
//   PLAYWRIGHT  module to require                 default "playwright"
//   BROWSER     path to a Chromium executable     default: Playwright's own
//   OUT         folder for the screenshots        default ./demo-shots
//   MAX_SECONDS slowest acceptable hand-off       default 10  (the screens refresh every 5 s)
import { createRequire } from 'node:module'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.BASE ?? 'http://localhost:5174'
const API = process.env.API ?? 'http://localhost:8080/api'
const OUT = process.env.OUT ?? './demo-shots'
const MAX_MS = Number(process.env.MAX_SECONDS ?? 10) * 1000
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')
const en = JSON.parse(readFileSync(fileURLToPath(new URL('../src/i18n/locales/en.json', import.meta.url)), 'utf8'))
const tr = (path) => path.split('.').reduce((o, k) => o[k], en)
mkdirSync(OUT, { recursive: true })

const results = []
const timings = []
const check = (name, ok, extra = '') => {
  results.push(ok)
  console.log(`${ok ? 'ok  ' : 'FAIL'}  ${name}${extra ? ` (${extra})` : ''}`)
}
/** Waits for the condition, and judges how long it took (the hand-off a presenter has to wait for). */
async function handOff(name, since, wait) {
  try {
    await wait()
    const ms = Date.now() - since
    timings.push([name, ms])
    check(`${name} within ${MAX_MS / 1000} s`, ms <= MAX_MS, `${(ms / 1000).toFixed(1)} s`)
  } catch (error) {
    check(name, false, error.message.split('\n')[0].slice(0, 120))
  }
}

// ---------------------------------------------------------------- setup through the real APIs
async function api(path, { method = 'GET', body, cookie, headers } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`)
  return { res, json: text ? JSON.parse(text) : null }
}
const login = async (name) =>
  (await api('/staff/login', { method: 'POST', body: { name, pin: '1234' } })).res.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')
const adminCookie = await login('O. Sado')
const kitchenCookie = await login('M. Behr')
const table7 = (await api('/tables', { cookie: adminCookie })).json.find((t) => t.tableNumber === '7')
const deviceCode = (await api(`/tables/${table7.id}/pair`, { method: 'POST', cookie: adminCookie })).json.pairedDeviceId
for (const meal of (await api('/kitchen/meals', { cookie: kitchenCookie })).json) {
  if (!meal.available && meal.names.some((n) => n.name === 'Radler')) await api(`/kitchen/meals/${meal.id}/availability`, { method: 'PATCH', body: { available: true }, cookie: kitchenCookie })
}

const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})
async function open(viewport, deviceCodeForGuest) {
  const context = await browser.newContext({ viewport })
  await context.addInitScript(([code]) => {
    localStorage.setItem('rom-language', 'en')
    localStorage.setItem('rom-theme', 'light')
    if (code) localStorage.setItem('rom-guest-device-code', code)
  }, [deviceCodeForGuest ?? null])
  const page = await context.newPage()
  page.setDefaultTimeout(30000)
  return { context, page }
}
const guest = await open({ width: 390, height: 844 }, deviceCode)
const kitchen = await open({ width: 1100, height: 760 })
const hall = await open({ width: 1100, height: 760 })
const adminScreen = await open({ width: 1280, height: 800 })

/** The three screens side by side, as they would be on the demo desk. */
async function sideBySide(name) {
  const shots = await Promise.all([guest.page, kitchen.page, hall.page].map((p) => p.screenshot()))
  const sheet = await browser.newPage({ viewport: { width: 2640, height: 780 } })
  const img = (buf, w) => `<img style="height:760px;width:${w}px;object-fit:cover;object-position:top" src="data:image/png;base64,${buf.toString('base64')}">`
  await sheet.setContent(`<body style="margin:10px;background:#444;display:flex;gap:10px">${img(shots[0], 350)}${img(shots[1], 1100)}${img(shots[2], 1100)}</body>`)
  await sheet.screenshot({ path: `${OUT}/demo-${name}.png` })
  await sheet.close()
}

// ---------------------------------------------------------------- 1. the staff screens are open
await kitchen.page.goto(`${BASE}/kitchen`)
await kitchen.page.waitForSelector('.login-form input')
await kitchen.page.locator('.login-form input').nth(0).fill('M. Behr')
await kitchen.page.locator('.login-form input').nth(1).fill('1234')
await kitchen.page.click('.login-form__submit')
await kitchen.page.waitForSelector('.kitchen-screen__board')
await hall.page.goto(`${BASE}/hall`)
await hall.page.waitForSelector('.hall-screen__board')
check('kitchen is signed in and the hall board is up', true)

// ---------------------------------------------------------------- 2. the guest orders on the phone
const g = guest.page
await g.goto(`${BASE}/guest`)
await g.waitForSelector('.welcome-screen__language-button')
await g.locator('.welcome-screen__language-button').nth(1).click() // English
await g.waitForSelector('.meal-card')
const photos = await g.locator('.meal-card__photo').count()
check('the menu shows a photo on every meal card', photos === (await g.locator('.meal-card').count()) && photos >= 7, `${photos} photos`)
await g.locator('.meal-card', { hasText: 'Wiener Schnitzel' }).click()
await g.getByRole('button', { name: tr('guest.detail.addToOrder') }).waitFor()
await g.locator('.meal-detail-screen__sizes button').nth(1).click() // 300 g
await g.locator('.meal-detail-screen__stepper-button').nth(1).click() // quantity 2
await g.locator('.meal-detail-screen__note-input').fill('no lingonberries, please')
await g.getByRole('button', { name: tr('guest.detail.addToOrder') }).click()
await g.click('.guest-screen__cart-bar')
await g.getByRole('button', { name: new RegExp(tr('guest.cart.choosePayment')) }).waitFor()
await g.waitForFunction((label) => !document.body.innerText.includes(label), tr('guest.cart.calculating'))
check('the cart shows subtotal, VAT and total from the server', (await g.locator('.cart-screen__totals-row').count()) === 3)
await g.getByRole('button', { name: new RegExp(tr('guest.cart.choosePayment')) }).click()
await g.waitForSelector('.payment-screen__method')
await g.locator('.payment-screen__method').first().click()
const confirmedAt = Date.now()
await g.getByRole('button', { name: new RegExp(tr('guest.payment.confirm')) }).click()
await g.waitForSelector('.order-confirmation-screen__number')
const number = (await g.locator('.order-confirmation-screen__number').textContent()).trim()
check('the guest gets a three-digit order number', /^\d{3,}$/.test(number), number)

// ---------------------------------------------------------------- 3. kitchen -> hall -> guest
const k = kitchen.page
const card = k.locator('.kitchen-order-card', { has: k.locator(`.kitchen-order-card__number:text-is("${number}")`) })
await handOff('the order appears on the kitchen screen', confirmedAt, () => card.waitFor())
check('the card shows table, items, size and the guest note', /table 7/i.test(await card.innerText()) && /2×/.test(await card.innerText()) && /300 g/.test(await card.innerText()) && /lingonberries/.test(await card.innerText()))
await sideBySide('1-order-placed')

const entry = (panel) => hall.page.locator(`.hall-screen__panel--${panel} .hall-screen__entry`, { hasText: number })
const guestStage = (label) => g.locator('.order-status-timeline__row--reached', { hasText: label })

let t = Date.now()
await card.locator('.kitchen-order-card__action--start').click()
await handOff('the number appears on the hall board under "In preparation"', t, () => entry('preparing').waitFor())
await handOff("the guest's timeline shows \"In preparation\"", t, () => guestStage(tr('guest.status.preparing')).waitFor())
check('the hall board shows the table beside the number', /table 7/i.test(await entry('preparing').innerText()))
await sideBySide('2-in-preparation')

t = Date.now()
await card.locator('.kitchen-order-card__action--ready').click()
await handOff('the number moves to "Ready" on the hall board', t, () => entry('ready').waitFor())
check('and leaves "In preparation"', (await entry('preparing').count()) === 0)
await handOff("the guest's timeline shows \"Ready\"", t, () => guestStage(tr('guest.status.ready')).waitFor())
await sideBySide('3-ready')

t = Date.now()
await card.locator('.kitchen-order-card__action--served').click()
await handOff('the number leaves the hall board once picked up', t, () => hall.page.locator('.hall-screen__entry', { hasText: number }).waitFor({ state: 'detached' }))
await handOff("the guest's timeline shows \"Served\"", t, () => guestStage(tr('guest.status.served')).waitFor())
await sideBySide('4-served')

// ---------------------------------------------------------------- 4. the admin sees the whole story
const placed = (await api(`/admin/orders/history?orderNumber=${Number(number)}`, { cookie: adminCookie })).json.orders[0]
const stages = placed.entries.map((e) => `${e.stage}:${e.actor ?? 'Guest'}`)
check('the audit history has every stage with its actor', stages.join(' ') === 'SUBMITTED:Guest PREPARING:M. Behr READY:M. Behr SERVED:M. Behr', stages.join(' '))

// ---------------------------------------------------------------- 5. an admin cancels an order in preparation
const menu = (await api('/guest/menu?language=EN')).json
const sizeId = menu.flatMap((c) => c.meals).find((m) => m.name === 'Flammkuchen').sizes[0].id
const second = (await api('/guest/orders', { method: 'POST', body: { deviceCode, language: 'EN', paymentMethod: 'CASH', items: [{ sizeId, quantity: 1 }] } })).json
const secondNumber = String(second.orderNumber).padStart(3, '0')
const secondCard = k.locator('.kitchen-order-card', { has: k.locator(`.kitchen-order-card__number:text-is("${secondNumber}")`) })
await secondCard.waitFor()
await secondCard.locator('.kitchen-order-card__action--start').click()
await hall.page.locator('.hall-screen__panel--preparing .hall-screen__entry', { hasText: secondNumber }).waitFor()

const a = adminScreen.page
await a.goto(`${BASE}/admin`)
await a.waitForSelector('.login-form input')
await a.locator('.login-form input').nth(0).fill('O. Sado')
await a.locator('.login-form input').nth(1).fill('1234')
await a.click('.login-form__submit')
await a.waitForSelector('.admin-screen__logout')
await a.goto(`${BASE}/admin/orders`)
const row = a.locator('.order-row', { has: a.locator(`.order-row__number:text-is("${secondNumber}")`) })
await row.waitFor()
t = Date.now()
await row.getByRole('button', { name: tr('admin.orders.cancel') }).click()
await row.getByRole('button', { name: tr('admin.orders.cancelYes') }).click()
await handOff('the kitchen shows the cancelled banner', t, () => k.locator('.cancelled-banner__row', { hasText: secondNumber }).waitFor())
await handOff('the hall board drops the cancelled order', t, () => hall.page.locator('.hall-screen__entry', { hasText: secondNumber }).waitFor({ state: 'detached' }))
const secondStatus = (await api(`/guest/orders/${second.orderId}`, { headers: { 'X-Device-Code': deviceCode } })).json.status
check('the guest side sees the order as cancelled', secondStatus === 'CANCELLED', secondStatus)
await sideBySide('5-cancelled')
await k.locator('.cancelled-banner__row', { hasText: secondNumber }).getByRole('button', { name: tr('kitchen.cancelled.acknowledge') }).click()
await k.locator('.cancelled-banner__row', { hasText: secondNumber }).waitFor({ state: 'detached' })
check('acknowledging clears the banner', true)

// ---------------------------------------------------------------- 6. a meal runs out
// A second phone is already looking at the menu: the meal has to vanish from it without anyone touching it.
const phone = await open({ width: 390, height: 844 }, deviceCode)
await phone.page.goto(`${BASE}/guest`)
await phone.page.waitForSelector('.welcome-screen__language-button')
await phone.page.locator('.welcome-screen__language-button').nth(1).click()
await phone.page.waitForSelector('.meal-card')
const radlerCard = phone.page.locator('.meal-card', { hasText: 'Radler' })
check('Radler is on the open guest menu', (await radlerCard.count()) === 1)
t = Date.now()
await k.locator('.ran-out__chip', { hasText: 'Radler' }).click()
await handOff('Radler disappears from the menu that is already open on a table device', t, () => radlerCard.waitFor({ state: 'detached' }))
await k.locator('.ran-out__chip--sold-out', { hasText: 'Radler' }).click()
t = Date.now()
await handOff('and comes back when the kitchen toggles it again', t, () => radlerCard.waitFor())

await browser.close()
const failed = results.filter((r) => !r).length
console.log('\nHand-offs:')
for (const [name, ms] of timings) console.log(`  ${(ms / 1000).toFixed(1).padStart(5)} s  ${name}`)
writeFileSync(`${OUT}/demo-results.json`, JSON.stringify({ checks: results.length, failed, timings }, null, 2))
console.log(`\n${results.length - failed}/${results.length} checks passed. Side-by-side screenshots and demo-results.json are in ${OUT}`)
process.exit(failed ? 1 : 0)
