// Drives the guest's start of a visit in a real browser, in German, English and Arabic (right to left):
//
//   language -> eat in or take away (in the chosen language) -> eat in: pairing (in the chosen language) -> menu
//                                                             -> take away: menu, no pairing, no table
//
//   - "/" opens the guest start (the four-button Home page is gone)
//   - the order type screen follows the language just chosen and fits a phone
//   - eat in on an unpaired device asks for the code (translated, wrong code explained, Back returns); a paired
//     device goes straight to the menu with its table
//   - take away reaches the menu without pairing, the order is placed against the take-away table and shows
//     "Take away" (translated) in the kitchen, on the hall board, in the admin Orders list and in the history
//   - a take-away guest follows their own order and cannot read another one (server: 403 without proof, 404 with
//     a wrong secret); the cart survives changing the order type
//   - the admin menu opens the Guest, Kitchen and Hall screens in a new tab; the logo goes to the Dashboard
//
//   node scripts/guest-flow-check.mjs
//
// Needs, running: the backend started with the dev profile (it seeds the demo staff, menu and tables) and the
// frontend in front of it. Use a throwaway stack, NOT your dev database: it places and serves orders.
//
// Environment (all optional): BASE (default http://localhost:5174), API (default http://localhost:8080/api),
// PLAYWRIGHT (module to require), BROWSER (Chromium executable), OUT (screenshots, default ./guest-flow-shots).
import { createRequire } from 'node:module'
import { mkdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.BASE ?? 'http://localhost:5174'
const API = process.env.API ?? 'http://localhost:8080/api'
const OUT = process.env.OUT ?? './guest-flow-shots'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')
mkdirSync(OUT, { recursive: true })

const locales = Object.fromEntries(
  ['en', 'de', 'ar'].map((l) => [l, JSON.parse(readFileSync(fileURLToPath(new URL(`../src/i18n/locales/${l}.json`, import.meta.url)), 'utf8'))]),
)
const text = (lang, path, params = {}) =>
  path.split('.').reduce((o, k) => o[k], locales[lang]).replace(/\{\{(\w+)\}\}/g, (_, k) => params[k])
const LANG_BUTTON = { de: 0, en: 1, ar: 2 }

const results = []
const check = (name, ok, extra = '') => {
  results.push(ok)
  console.log(`${ok ? 'ok  ' : 'FAIL'}  ${name}${extra ? ` (${extra})` : ''}`)
}

async function api(path, { method = 'GET', body, cookie, headers = {}, raw = false } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}), ...headers },
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
const tableSeven = (await api('/tables', { cookie: admin })).json.find((t) => t.tableNumber === '7')
const deviceCode = (await api(`/tables/${tableSeven.id}/pair`, { method: 'POST', cookie: admin })).json.pairedDeviceId
const sizeId = (await api('/guest/menu?language=EN')).json.flatMap((c) => c.meals)[0].sizes[0].id
const step = (orderId, to) => api(`/admin/orders/${orderId}/transition`, { method: 'POST', body: { status: to }, cookie: admin })

check('the admin tables list never shows the reserved take-away table', !(await api('/tables', { cookie: admin })).json.some((t) => t.tableNumber.includes('TAKE_AWAY')))

const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})
async function open(viewport, lang, { device = null, mode = null } = {}) {
  const context = await browser.newContext({ viewport, ...(mode === 'new-tab-allowed' ? {} : {}) })
  await context.addInitScript(([l, d]) => {
    localStorage.setItem('rom-language', l)
    localStorage.setItem('rom-theme', 'light')
    if (d) localStorage.setItem('rom-guest-device-code', d)
  }, [lang, device])
  const page = await context.newPage()
  page.setDefaultTimeout(25000)
  return { context, page }
}
const noOverflow = (page) => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)

// ---------------------------------------------------------------- "/" opens the guest start
{
  const { context, page } = await open({ width: 390, height: 844 }, 'en')
  await page.goto(`${BASE}/`)
  await page.waitForSelector('.welcome-screen__language-button')
  check('"/" redirects to the guest start (the language screen)', new URL(page.url()).pathname === '/guest')
  check('the welcome logo is not a link (nothing to go to)', (await page.locator('.welcome-screen a').count()) === 0)
  await context.close()
}

let takeAwayOrderId = null
let takeAwayToken = null

for (const lang of ['en', 'de', 'ar']) {
  console.log(`\n--- ${lang.toUpperCase()}`)

  // ---- the order type screen follows the language
  {
    const { context, page } = await open({ width: 390, height: 844 }, lang === 'en' ? 'de' : 'en') // start in another language than the one chosen
    await page.goto(`${BASE}/guest`)
    await page.waitForSelector('.welcome-screen__language-button')
    await page.locator('.welcome-screen__language-button').nth(LANG_BUTTON[lang]).click()
    await page.waitForSelector('.order-type-screen__choice')
    await page.waitForTimeout(700)
    const body = await page.locator('.order-type-screen').innerText()
    check(`${lang}: the choice is asked in the chosen language`, body.includes(text(lang, 'guest.orderType.title')))
    check(`${lang}: both options and their hints are translated`, [text(lang, 'guest.orderType.eatIn'), text(lang, 'common.takeAway'), text(lang, 'guest.orderType.eatInHint'), text(lang, 'guest.orderType.takeAwayHint')].every((s) => body.includes(s)))
    check(`${lang}: the screen is ${lang === 'ar' ? 'right to left' : 'left to right'} and fits the phone`, (await page.evaluate(() => document.documentElement.dir)) === (lang === 'ar' ? 'rtl' : 'ltr') && (await noOverflow(page)))
    await page.screenshot({ path: `${OUT}/${lang}-order-type.png` })

    // ---- eat in on an unpaired device: the pairing screen, translated, with a way back
    await page.locator('.order-type-screen__choice').nth(0).click()
    await page.waitForSelector('.pairing-screen__form')
    const pairing = await page.locator('.pairing-screen').innerText()
    check(`${lang}: eat in on an unpaired device asks for the code, in the chosen language`, pairing.includes(text(lang, 'guest.pairing.title')) && pairing.includes(text(lang, 'guest.pairing.hint')))
    check(`${lang}: the pairing screen fits the phone`, await noOverflow(page))
    await page.locator('.pairing-screen__input').fill('ZZZZZZ')
    await page.locator('.pairing-screen__submit').click()
    await page.locator('.pairing-screen__error').waitFor()
    check(`${lang}: a wrong code is explained in the chosen language`, (await page.locator('.pairing-screen__error').innerText()) === text(lang, 'guest.pairing.unknownCode'))
    await page.screenshot({ path: `${OUT}/${lang}-pairing.png` })
    await page.getByRole('button', { name: text(lang, 'guest.pairing.back') }).click()
    await page.waitForSelector('.order-type-screen__choice')
    check(`${lang}: Back returns to the choice`, true)
    await page.locator('.order-type-screen__choice').nth(0).click()
    await page.locator('.pairing-screen__input').fill(deviceCode)
    await page.locator('.pairing-screen__submit').click()
    await page.waitForSelector('.meal-card')
    check(`${lang}: the right code opens the menu with the table`, (await page.locator('.guest-header__title').innerText()) === text(lang, 'guest.tableLabel', { number: '7' }))
    await context.close()
  }

  // ---- eat in on a paired device: straight to the menu
  {
    const { context, page } = await open({ width: 390, height: 844 }, lang, { device: deviceCode })
    await page.goto(`${BASE}/guest`)
    await page.waitForSelector('.welcome-screen__language-button')
    await page.locator('.welcome-screen__language-button').nth(LANG_BUTTON[lang]).click()
    await page.waitForSelector('.order-type-screen__choice')
    await page.locator('.order-type-screen__choice').nth(0).click()
    await page.waitForSelector('.meal-card')
    check(`${lang}: a paired device skips the pairing screen`, (await page.locator('.pairing-screen').count()) === 0)
    check(`${lang}: and shows its table`, (await page.locator('.guest-header__title').innerText()) === text(lang, 'guest.tableLabel', { number: '7' }))
    await context.close()
  }

  // ---- take away: no pairing, no table; changing the type keeps the cart; the order is a take-away order
  {
    const { context, page } = await open({ width: 390, height: 844 }, lang)
    await page.goto(`${BASE}/guest`)
    await page.waitForSelector('.welcome-screen__language-button')
    await page.locator('.welcome-screen__language-button').nth(LANG_BUTTON[lang]).click()
    await page.waitForSelector('.order-type-screen__choice')
    await page.locator('.order-type-screen__choice').nth(1).click()
    await page.waitForSelector('.meal-card')
    check(`${lang}: take away reaches the menu without pairing`, (await page.locator('.pairing-screen').count()) === 0)
    check(`${lang}: the header says "Take away" instead of a table`, (await page.locator('.guest-header__title').innerText()) === text(lang, 'common.takeAway'))
    check(`${lang}: the menu header fits the phone (nothing cut off at the edge)`, await noOverflow(page) && (await page.locator('.guest-screen__header-controls').evaluate((el) => el.getBoundingClientRect().right <= window.innerWidth + 0.5 && el.getBoundingClientRect().left >= -0.5)))
    await page.locator('.meal-card').nth(2).click()
    await page.getByRole('button', { name: text(lang, 'guest.detail.addToOrder') }).click()
    await page.waitForSelector('.guest-screen__cart-bar')
    // Switch to eating in and back: this device is not paired, so eating in asks for the code; Back returns to the choice.
    await page.locator('.guest-mode__switch .segmented__option').nth(0).click()
    await page.waitForSelector('.pairing-screen__form')
    await page.getByRole('button', { name: text(lang, 'guest.pairing.back') }).click()
    await page.waitForSelector('.order-type-screen__choice')
    await page.locator('.order-type-screen__choice').nth(1).click()
    await page.waitForSelector('.guest-mode__switch')
    check(`${lang}: switching the order type and back keeps the cart`, (await page.locator('.guest-screen__cart-bar').count()) === 1)
    check(`${lang}: the switch shows which one is chosen`, (await page.locator('.guest-mode__switch .segmented__option').nth(1).getAttribute('aria-pressed')) === 'true')
    await page.screenshot({ path: `${OUT}/${lang}-takeaway-menu.png` })
    await page.click('.guest-screen__cart-bar')
    await page.getByRole('button', { name: new RegExp(text(lang, 'guest.cart.choosePayment')) }).click()
    await page.waitForSelector('.payment-screen__method')
    await page.locator('.payment-screen__method').first().click()
    await page.getByRole('button', { name: new RegExp(text(lang, 'guest.payment.confirm')) }).click()
    await page.waitForSelector('.order-confirmation-screen__number')
    const stored = JSON.parse(await page.evaluate(() => sessionStorage.getItem('rom-guest-order')))
    takeAwayOrderId = stored.orderId
    takeAwayToken = stored.token
    check(`${lang}: the take-away order is placed and the guest keeps its secret`, Number.isInteger(takeAwayOrderId) && typeof takeAwayToken === 'string' && takeAwayToken.length >= 40)
    check(`${lang}: the confirmation tells a take-away guest where to collect`, (await page.locator('.order-confirmation-screen__guidance').innerText()) === text(lang, 'guest.confirmation.takeAwayGuidance'))
    await page.screenshot({ path: `${OUT}/${lang}-takeaway-confirmation.png` })

    // The guest follows their own order: it is "In preparation" on their screen after the kitchen starts it.
    await step(takeAwayOrderId, 'PREPARING')
    await page.waitForFunction(() => document.querySelectorAll('.order-status-timeline__row--reached').length > 1, null, { timeout: 15000 })
    check(`${lang}: the take-away guest sees the status move on (their own order, by their secret)`, (await page.locator('.order-confirmation-screen__reconnecting').count()) === 0)

    // ---- the server's privacy rules for take-away orders
    const none = await api(`/guest/orders/${takeAwayOrderId}`, { raw: true })
    const wrong = await api(`/guest/orders/${takeAwayOrderId}`, { headers: { 'X-Order-Token': 'wrong-secret-wrong-secret-wrong-secret-0000' }, raw: true })
    const byTable = await api(`/guest/orders/${takeAwayOrderId}`, { headers: { 'X-Device-Code': deviceCode }, raw: true })
    const own = await api(`/guest/orders/${takeAwayOrderId}`, { headers: { 'X-Order-Token': takeAwayToken }, raw: true })
    check(`${lang}: another guest cannot read it: no proof 403, wrong secret 404, a table's device code 404; theirs is 200`, none.status === 403 && wrong.status === 404 && byTable.status === 404 && own.status === 200, `${none.status}/${wrong.status}/${byTable.status}/${own.status}`)

    // ---- it shows as take away on the staff screens
    const kitchenOrders = (await api('/kitchen/orders', { cookie: kitchen })).json
    check(`${lang}: the kitchen API marks it take away`, kitchenOrders.some((o) => o.orderId === takeAwayOrderId && o.takeAway === true))
    const hall = (await api('/hall/orders')).json
    check(`${lang}: the hall API marks it take away`, hall.preparing.some((e) => e.takeAway === true))
    await context.close()
  }

  // ---- the staff screens show the label, in this language
  {
    const { context: kContext, page: kPage } = await open({ width: 1280, height: 800 }, lang)
    await kPage.goto(`${BASE}/kitchen`)
    await kPage.locator('.login-form input').nth(0).fill('M. Behr')
    await kPage.locator('.login-form input').nth(1).fill('1234')
    await kPage.click('.login-form__submit')
    await kPage.waitForSelector('.kitchen-order-card')
    check(`${lang}: the kitchen card marks it clearly: a coral "Take away" chip with a bag beside the number, and a coral edge on the card`, await kPage.locator('.kitchen-order-card--take-away').first().evaluate((card) => {
      const chip = card.querySelector('.kitchen-order-card__place--take-away')
      const number = card.querySelector('.kitchen-order-card__number')
      if (!chip || !number || !chip.querySelector('svg')) return false
      const chipBox = chip.getBoundingClientRect(), numberBox = number.getBoundingClientRect()
      const sameRow = Math.abs((chipBox.top + chipBox.bottom) / 2 - (numberBox.top + numberBox.bottom) / 2) < 20
      return sameRow && getComputedStyle(chip).backgroundColor !== getComputedStyle(card).backgroundColor && parseFloat(getComputedStyle(card).borderInlineStartWidth) >= 4
    }))
    check(`${lang}: the kitchen card says "Take away"`, (await kPage.locator('.kitchen-order-card__place').allInnerTexts()).some((s) => s.toLowerCase() === text(lang, 'common.takeAway').toLowerCase()))
    await kPage.screenshot({ path: `${OUT}/${lang}-kitchen.png` })
    await kContext.close()

    const { context: hContext, page: hPage } = await open({ width: 1280, height: 800 }, lang)
    await hPage.goto(`${BASE}/hall`)
    await hPage.waitForSelector('.hall-screen__entry')
    check(`${lang}: the hall board says "Take away" beside the number`, (await hPage.locator('.hall-screen__table').allInnerTexts()).some((s) => s.toLowerCase().includes(text(lang, 'common.takeAway').toLowerCase())))
    await hContext.close()
  }

  // ---- the admin menu: the screens in a new tab, and the logo goes to the Dashboard
  {
    const { context, page } = await open({ width: 1440, height: 900 }, lang)
    await page.goto(`${BASE}/admin`)
    await page.locator('.login-form input').nth(0).fill('O. Sado')
    await page.locator('.login-form input').nth(1).fill('1234')
    await page.click('.login-form__submit')
    await page.waitForSelector('.admin-sidebar__logout')
    const links = await page.locator('.admin-sidebar__screen').evaluateAll((els) => els.map((el) => ({ href: el.getAttribute('href'), target: el.getAttribute('target'), rel: el.getAttribute('rel'), label: el.getAttribute('aria-label') })))
    check(`${lang}: the admin menu has Guest, Kitchen and Hall, each opening in a new tab`, JSON.stringify(links.map((l) => l.href)) === '["/guest","/kitchen","/hall"]' && links.every((l) => l.target === '_blank' && l.rel.includes('noopener')))
    check(`${lang}: their names are translated and say they open in a new tab`, links.every((l, i) => l.label === `${text(lang, ['admin.nav.guestScreen', 'admin.nav.kitchenScreen', 'admin.nav.hallScreen'][i])} (${text(lang, 'admin.nav.opensInNewTab')})`))
    check(`${lang}: the rail still fits (no scrolling needed)`, await page.locator('.admin-sidebar').evaluate((el) => el.scrollHeight <= el.clientHeight + 1))
    const [popup] = await Promise.all([context.waitForEvent('page'), page.locator('.admin-sidebar__screen').nth(1).click()])
    await popup.waitForLoadState()
    check(`${lang}: the Kitchen link opens the kitchen screen in a new tab`, new URL(popup.url()).pathname === '/kitchen')
    await popup.close()

    await page.goto(`${BASE}/admin/settings`)
    await page.locator('.admin-sidebar__logo').waitFor()
    check(`${lang}: the logo is a link named for where it goes`, (await page.locator('.admin-sidebar__logo').getAttribute('aria-label')) === text(lang, 'admin.nav.home'))
    await page.locator('.admin-sidebar__logo').click()
    await page.waitForURL(/\/admin\/dashboard/)
    check(`${lang}: the logo goes to the Dashboard`, true)

    // the Orders list and the history show the label
    await page.goto(`${BASE}/admin/orders`)
    await page.locator('.order-row').first().waitFor()
    check(`${lang}: the admin Orders list says "Take away"`, (await page.locator('.order-row').allInnerTexts()).some((s) => s.toLowerCase().includes(text(lang, 'common.takeAway').toLowerCase())))
    await page.goto(`${BASE}/admin/history`)
    await page.locator('.history-card').first().waitFor()
    check(`${lang}: the history says "Take away"`, (await page.locator('.history-card').allInnerTexts()).some((s) => s.toLowerCase().includes(text(lang, 'common.takeAway').toLowerCase())))
    await page.screenshot({ path: `${OUT}/${lang}-admin-history.png` })

    // the phone drawer has them too
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(`${BASE}/admin/dashboard`)
    await page.locator('.admin-topbar__menu').click()
    await page.locator('.admin-sidebar__screen').first().waitFor()
    check(`${lang}: the phone drawer offers the screens and the logo link too`, (await page.locator('.admin-sidebar__screen').count()) === 3 && (await page.locator('.admin-topbar__brand').getAttribute('href')) === '/admin/dashboard')
    await page.screenshot({ path: `${OUT}/${lang}-admin-drawer.png` })
    await context.close()
  }

  // finish the order so the next language starts with nothing open
  await step(takeAwayOrderId, 'READY').catch(() => undefined)
  await step(takeAwayOrderId, 'SERVED').catch(() => undefined)
}

await browser.close()
const passed = results.filter(Boolean).length
console.log(`\n${passed}/${results.length} checks passed. Screenshots are in ${OUT}`)
process.exit(passed === results.length ? 0 : 1)
