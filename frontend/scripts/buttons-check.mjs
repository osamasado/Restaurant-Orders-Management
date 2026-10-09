// Checks the one button system across every screen, in a real browser:
//
//   - every visible button has a surface (a background colour or gradient), rounded corners and, for the shared
//     .button family, a height between 32 and 48 px
//   - hovering a button changes how it looks (its surface, its shadow or its position), without changing its size
//   - a keyboard-focused button shows a focus ring
//   - a pressed primary, success, warning or danger button sinks and a disabled one is flat but keeps its surface
//
// A few controls are on purpose not raised buttons: the sidebar's page links, the order number that selects a
// table row, the category chips, the whole-card hit area of a guest meal, the welcome screen's language rows and
// the guest's size and payment rows. They are listed below, each with its reason, and are checked for a hover
// change only.
//
//   node scripts/buttons-check.mjs
//
// Needs, running: the backend started with the dev profile (it seeds the demo staff, menu and tables) and the
// frontend in front of it; the throwaway stack the other scripts use is right. Environment (all optional): BASE
// (default http://localhost:5174), API (default http://localhost:8080/api), PLAYWRIGHT, BROWSER, THEMES (default
// dark,light), LANGS (default en,ar).
import { createRequire } from 'node:module'

const BASE = process.env.BASE ?? 'http://localhost:5174'
const API = process.env.API ?? 'http://localhost:8080/api'
const THEMES = (process.env.THEMES ?? 'dark,light').split(',')
const LANGS = (process.env.LANGS ?? 'en,ar').split(',')
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')

/** Controls that are not raised buttons on purpose (navigation, selection hit areas): hover is still checked. */
const NOT_RAISED = [
  ['.admin-sidebar__link', 'a page in the navigation rail, shown on the active-page shape'],
  ['.order-reports__select', 'the order number that selects a table row'],
  ['.category-chip', 'a round picture with its name'],
  ['.meal-card__open', 'the name of a guest meal, stretched over the whole card'],
  ['.welcome-screen__language-button', 'a full-width row on the welcome screen'],
  ['.meal-detail-screen__size-row', 'a size row (a radio choice)'],
  ['.payment-screen__method', 'a payment method row (a radio choice)'],
  ['.meal-detail-screen__back', 'a round control over the meal photo'],
  ['.cart-screen__back', 'a round back control'],
  ['.segmented__option', 'an option inside a segmented control (the tray is the surface)'],
]
const exempt = (el) => NOT_RAISED.some(([selector]) => el.matches(selector))

let failures = 0
let checked = 0
const fail = (label, message) => {
  failures += 1
  console.log(`FAIL  ${label}: ${message}`)
}

async function api(path, { method = 'GET', body, cookie } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(cookie ? { Cookie: cookie } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { res, json: await res.json().catch(() => null) }
}
const login = async (name) => {
  const { res } = await api('/staff/login', { method: 'POST', body: { name, pin: '1234' } })
  return res.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')
}
const adminCookie = await login('O. Sado')
const tables = (await api('/tables', { cookie: adminCookie })).json ?? []
const deviceCode = tables.find((table) => table.tableNumber === '7')?.pairedDeviceId ?? tables.find((table) => table.pairedDeviceId)?.pairedDeviceId

const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})

async function open(viewport, lang, theme, device) {
  const context = await browser.newContext({ viewport })
  await context.addInitScript(([l, t, d]) => {
    localStorage.setItem('rom-language', l)
    localStorage.setItem('rom-theme', t)
    if (d) {
      localStorage.setItem('rom-guest-device-code', d)
      sessionStorage.setItem('rom-guest-step', 'ordering')
    }
  }, [lang, theme, device ?? null])
  return { context, page: await context.newPage() }
}
async function staffSignIn(page, path, name) {
  await page.goto(`${BASE}${path}`)
  await page.waitForSelector('.login-form input')
  await page.locator('.login-form input').nth(0).fill(name)
  await page.locator('.login-form input').nth(1).fill('1234')
  await page.click('.login-form__submit')
  await page.waitForTimeout(1500)
}

/** Every distinct kind of visible button on the page (by its classes), with how it looks at rest. */
async function inspect(page, label) {
  const found = await page.evaluate(() => {
    const seen = new Map()
    for (const el of document.querySelectorAll('button, a.button')) {
      const rect = el.getBoundingClientRect()
      if (rect.width === 0 || rect.height === 0 || getComputedStyle(el).visibility === 'hidden') continue
      const key = `${el.className}|${el.disabled ? 'disabled' : 'enabled'}`
      if (seen.has(key)) continue
      el.setAttribute('data-bc', String(seen.size))
      const cs = getComputedStyle(el)
      seen.set(key, {
        index: seen.size,
        text: (el.textContent || el.getAttribute('aria-label') || '').trim().slice(0, 28),
        className: el.className,
        disabled: el.disabled,
        height: Math.round(rect.height),
        width: Math.round(rect.width),
        radius: parseFloat(cs.borderTopLeftRadius) || 0,
        hasSurface: cs.backgroundColor !== 'rgba(0, 0, 0, 0)' && cs.backgroundColor !== 'transparent' ? true : cs.backgroundImage !== 'none',
        isButtonClass: el.classList.contains('button'),
        // the whole card is what changes when this stretched hit area is hovered
        hoverOnParent: el.classList.contains('meal-card__open'),
        variant: ['primary', 'success', 'warning', 'danger'].find((v) => el.classList.contains(`button--${v}`)) ?? null,
      })
    }
    return [...seen.values()]
  })
  for (const item of found) {
    checked += 1
    const where = `${label} "${item.text}" (${item.className.split(' ').slice(0, 3).join(' ')})`
    // Some pages refresh their list every few seconds and replace the buttons, so the same button is found again
    // by its classes and its text before each step.
    const retag = () =>
      page.evaluate(({ cls, text }) => {
        document.querySelectorAll('[data-bc]').forEach((node) => node.removeAttribute('data-bc'))
        const node = [...document.querySelectorAll('button, a.button')].find(
          (candidate) =>
            candidate.className === cls &&
            (candidate.textContent || candidate.getAttribute('aria-label') || '').trim().slice(0, 28) === text &&
            candidate.getBoundingClientRect().width > 0,
        )
        if (node) node.setAttribute('data-bc', 'x')
        return Boolean(node)
      }, { cls: item.className, text: item.text })
    const el = page.locator('[data-bc="x"]')
    if (!(await retag())) continue
    const isExempt = await el.evaluate((node, list) => list.some((selector) => node.matches(selector)), NOT_RAISED.map(([selector]) => selector)).catch(() => false)
    if (!isExempt) {
      if (!item.hasSurface) fail(where, 'no background colour')
      if (item.radius === 0) fail(where, 'sharp corners')
      else if (item.radius < 8 && !item.className.includes('round')) fail(where, `corner radius ${item.radius}px is too small`)
      if (item.isButtonClass && (item.height < 32 || item.height > 48)) fail(where, `height ${item.height}px is outside 32 to 48`)
    }
    if (item.disabled) {
      if (!isExempt && !item.hasSurface) fail(where, 'a disabled button lost its surface')
      continue
    }
    // hover must change something and must not change the size
    await el.scrollIntoViewIfNeeded({ timeout: 2000 }).catch(() => {})
    const box = await el.boundingBox({ timeout: 2000 }).catch(() => null)
    if (!box) continue
    const cx = box.x + box.width / 2
    const cy = box.y + box.height / 2
    // A button under a dialog's overlay (or another layer) cannot be reached by a mouse: nothing to check there.
    const reachable = await el.evaluate((node, point) => { const top = document.elementFromPoint(point.x, point.y); return Boolean(top && (top === node || node.contains(top))) }, { x: cx, y: cy }, { timeout: 2000 }).catch(() => false)
    if (!reachable) continue
    const look = () =>
      el.evaluate((node) => {
        const cs = getComputedStyle(node)
        const r = node.getBoundingClientRect()
        return { look: [cs.backgroundColor, cs.backgroundImage, cs.boxShadow, cs.transform, cs.color].join('|'), w: Math.round(r.width), h: Math.round(r.height) }
      }, undefined, { timeout: 2000 })
    await page.mouse.move(0, 0)
    await page.waitForTimeout(260)
    const before = await look().catch(() => null)
    await page.mouse.move(cx, cy)
    await page.waitForTimeout(300)
    const hovered = await el.evaluate((node) => node.matches(':hover'), undefined, { timeout: 2000 }).catch(() => false)
    const after = await look().catch(() => null)
    if (hovered && before && after && !item.hoverOnParent) {
      if (before.look === after.look) fail(where, 'hover changes nothing')
      if (before.w !== after.w || before.h !== after.h) fail(where, `hover changes the size (${before.w}x${before.h} to ${after.w}x${after.h})`)
    }
    if (item.variant && hovered) {
      await page.mouse.down()
      await page.waitForTimeout(300)
      const pressed = await el.evaluate((node) => getComputedStyle(node).transform, undefined, { timeout: 2000 }).catch(() => null)
      // release away from the button so the press is not a click and the page stays as it was
      await page.mouse.move(0, 0)
      await page.mouse.up()
      if (pressed !== null && (pressed === 'none' || !pressed.endsWith(', 2)'))) fail(where, `pressing does not sink it (${pressed})`)
    }
    await page.mouse.move(0, 0)
    await retag()
    // keyboard focus: arrive at the button by Shift+Tab then Tab, as a keyboard user would, so :focus-visible applies
    await el.evaluate((node) => node.focus(), undefined, { timeout: 2000 }).catch(() => {})
    await page.keyboard.press('Shift+Tab')
    await page.keyboard.press('Tab')
    const ring = await el
      .evaluate((node) => {
        // a stretched hit area shows its ring on the card it covers
        const cs = getComputedStyle(node.matches('.meal-card__open') ? node.closest('.meal-card') : node)
        return node === document.activeElement ? `${cs.outlineStyle}|${cs.outlineWidth}` : null
      }, undefined, { timeout: 2000 })
      .catch(() => null)
    if (ring !== null && (!ring.startsWith('solid') || ring.endsWith('|0px'))) fail(where, `no focus ring (${ring})`)
    await el.evaluate((node) => node.blur(), undefined, { timeout: 2000 }).catch(() => {})
  }
}

for (const theme of THEMES) {
  for (const lang of LANGS) {
    const tag = `${lang}-${theme}`
    {
      const { context, page } = await open({ width: 1440, height: 900 }, lang, theme)
      await staffSignIn(page, '/admin/dashboard', 'O. Sado')
      await page.locator('.order-reports__row').first().waitFor({ timeout: 20000 })
      await inspect(page, `admin ${tag} dashboard`)
      for (const view of ['orders', 'history', 'meals', 'materials', 'tables', 'staff', 'settings']) {
        await page.goto(`${BASE}/admin/${view}`)
        await page.locator('.admin-screen__content h2').first().waitFor({ timeout: 20000 })
        await page.waitForTimeout(700)
        await inspect(page, `admin ${tag} ${view}`)
      }
      // dialogs and forms
      await page.goto(`${BASE}/admin/meals`)
      await page.locator('.meal-row').first().waitFor()
      await page.locator('.meal-row__actions button').first().click()
      await page.waitForSelector('.modal__card'); await page.waitForTimeout(400)
      await inspect(page, `admin ${tag} meal form`)
      await page.keyboard.press('Escape')
      await page.locator('.categories-row__actions button').nth(1).click()
      await page.waitForSelector('.modal__card'); await page.waitForTimeout(400)
      await inspect(page, `admin ${tag} delete dialog`)
      await context.close()
    }
    {
      const { context, page } = await open({ width: 1440, height: 900 }, lang, theme)
      await staffSignIn(page, '/kitchen', 'M. Behr')
      await page.locator('.kitchen-order-card, .kitchen-screen__column').first().waitFor({ timeout: 20000 })
      await inspect(page, `kitchen ${tag}`)
      await context.close()
    }
    if (deviceCode) {
      const { context, page } = await open({ width: 1366, height: 860 }, lang, theme, deviceCode)
      await page.goto(`${BASE}/guest`)
      await page.locator('.meal-card').first().waitFor({ timeout: 20000 })
      await page.waitForTimeout(500)
      await inspect(page, `guest ${tag} menu`)
      await page.locator('.meal-card').first().click()
      await page.locator('.meal-detail-screen__stepper-button').first().waitFor()
      await page.waitForTimeout(400)
      await inspect(page, `guest ${tag} meal`)
      await page.locator('.guest-screen__action-bar').click()
      await page.locator('.cart-panel__line').waitFor()
      await page.waitForTimeout(1200)
      await inspect(page, `guest ${tag} cart`)
      await context.close()
    }
  }
}
await browser.close()
console.log(`\n${checked} distinct buttons checked, ${failures} problems.`)
process.exit(failures ? 1 : 0)
