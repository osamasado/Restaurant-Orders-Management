// Drives every admin dialog in a real browser (issue: replace the browser's alert, confirm and prompt):
//
//   - the browser's own dialogs never appear (any one that does fails the run)
//   - each delete asks in a dialog that names the item, starts on the safe button, and a failed delete shows its
//     reason inside the dialog with "Try again"
//   - the PIN reset: visible label and hint, the error appears when leaving the field and goes the moment the PIN
//     is fixed, a server error stays in the dialog, and the new PIN really works (and the old one does not)
//   - the pairing dialog shows the code large, copies it, and a failed pairing becomes a toast
//   - every dialog takes focus, keeps Tab inside, closes on Escape and on a click outside, returns focus to what
//     opened it, and locks the page behind it
//   - toasts are announced, can be dismissed and go away by themselves
//   - "reduce motion": no animation; German and Arabic (right to left): nothing cut off, the code stays
//     left to right, text contrast 4.5:1 in light and dark
//
//   node scripts/dialogs-check.mjs
//
// Needs, running: the backend started with the dev profile (it seeds the demo staff, menu and tables) and the
// frontend in front of it. Use a throwaway stack, NOT your dev database: it creates and deletes accounts, tables,
// categories, meals and raw materials, and it resets a PIN. Prefer a production build.
//
// Environment (all optional): BASE (default http://localhost:5174), API (default http://localhost:8080/api),
// PLAYWRIGHT (module to require), BROWSER (Chromium executable), OUT (screenshots, default ./dialog-shots).
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE ?? 'http://localhost:5174'
const API = process.env.API ?? 'http://localhost:8080/api'
const OUT = process.env.OUT ?? './dialog-shots'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright')
mkdirSync(OUT, { recursive: true })
const rid = Date.now().toString(36).slice(-5)

const results = []
const check = (name, ok, extra = '') => {
  results.push(ok)
  console.log(`${ok ? 'ok  ' : 'FAIL'}  ${name}${extra ? ` (${extra})` : ''}`)
}

// ---------------------------------------------------------------- test data through the real APIs
async function api(path, { method = 'GET', body, cookie, raw = false } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (raw) return res
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`)
  return { res, json: text ? JSON.parse(text) : null }
}
const loginCookie = async (name, pin = '1234') =>
  (await api('/staff/login', { method: 'POST', body: { name, pin } })).res.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')
const admin = await loginCookie('O. Sado')
const kitchenCookie = await loginCookie('M. Behr')
const mk = {
  staff: (name, pin = '0000', role = 'WAITER') => api('/staff/accounts', { method: 'POST', body: { name, role, pin }, cookie: admin }).then((r) => r.json),
  table: (tableNumber) => api('/tables', { method: 'POST', body: { tableNumber, room: 'QA room', seats: 2 }, cookie: admin }).then((r) => r.json),
  category: (name) => api('/categories', { method: 'POST', body: { sortOrder: 90, translations: ['DE', 'EN', 'AR'].map((language) => ({ language, name })) }, cookie: admin }).then((r) => r.json),
  material: (name) => api('/raw-materials', { method: 'POST', body: { name, unit: 'g', inStockQuantity: 5, supplier: null }, cookie: admin }).then((r) => r.json),
  meal: (categoryId, name) =>
    api('/meals', {
      method: 'POST',
      cookie: admin,
      body: {
        categoryId,
        available: true,
        translations: ['DE', 'EN', 'AR'].map((language) => ({ language, name, description: null, preparationMethod: null, ingredients: [] })),
        sizes: [{ id: null, price: 5, translations: ['DE', 'EN', 'AR'].map((language) => ({ language, label: 'One' })) }],
      },
    }).then((r) => r.json),
}
const listStaff = () => api('/staff/accounts', { cookie: admin }).then((r) => r.json)
const listTables = () => api('/tables', { cookie: admin }).then((r) => r.json)

// An order handled by the kitchen puts M. Behr and table 1 into the history: neither can be deleted any more.
const menu = (await api('/guest/menu?language=EN')).json
const sizeId = menu.flatMap((c) => c.meals)[0].sizes[0].id
const tables0 = await listTables()
const tableOne = tables0.find((t) => t.tableNumber === '1')
const order = (await api('/guest/orders', { method: 'POST', body: { deviceCode: tableOne.pairedDeviceId, language: 'EN', paymentMethod: 'CASH', items: [{ sizeId, quantity: 1 }] } })).json
await api(`/kitchen/orders/${order.orderId}/transition`, { method: 'POST', body: { status: 'PREPARING' }, cookie: kitchenCookie })

// ---------------------------------------------------------------- browser
const browser = await chromium.launch(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {})
const nativeDialogs = []

async function open(lang = 'en', theme = 'light', { reducedMotion = 'no-preference', viewport = { width: 1440, height: 900 } } = {}) {
  const context = await browser.newContext({ viewport, reducedMotion })
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: BASE })
  await context.addInitScript(([l, th]) => { localStorage.setItem('rom-language', l); localStorage.setItem('rom-theme', th) }, [lang, theme])
  const page = await context.newPage()
  page.setDefaultTimeout(20000)
  page.on('dialog', (dialog) => { nativeDialogs.push(`${dialog.type()}: ${dialog.message()}`); void dialog.dismiss() })
  await page.goto(`${BASE}/admin`)
  await page.waitForSelector('.login-form input')
  await page.locator('.login-form input').nth(0).fill('O. Sado')
  await page.locator('.login-form input').nth(1).fill('1234')
  await page.click('.login-form__submit')
  await page.waitForSelector('.admin-sidebar__logout')
  return { context, page }
}
const go = async (page, view, rowSelector) => { await page.goto(`${BASE}/admin/${view}`); await page.waitForSelector(rowSelector) }
const dialogOf = (page) => page.locator('[role="dialog"]')
const active = (page) => page.evaluate(() => { const el = document.activeElement; return { tag: el?.tagName, text: (el?.textContent ?? '').trim().slice(0, 40), type: el?.getAttribute('type'), inDialog: !!el?.closest('[role="dialog"]') } })
const rowOf = (page, selector, text) => page.locator(selector, { hasText: text })
const press = async (page, key, times = 1) => { for (let i = 0; i < times; i++) await page.keyboard.press(key) }
async function focusStaysInside(page, forward = 9, backward = 4) {
  let inside = true
  for (let i = 0; i < forward; i++) { await page.keyboard.press('Tab'); inside &&= (await active(page)).inDialog }
  for (let i = 0; i < backward; i++) { await page.keyboard.press('Shift+Tab'); inside &&= (await active(page)).inDialog }
  return inside
}
const bodyLocked = (page) => page.evaluate(() => document.body.style.overflow === 'hidden')

// ================================================================ the English walk
console.log('--- staff: delete')
{
  const { context, page } = await open()
  const doomed = await mk.staff(`Doomed Waiter ${rid}`)
  await go(page, 'staff', '.staff-row')
  const row = rowOf(page, '.staff-row', doomed.name)
  const del = row.getByRole('button', { name: 'Delete', exact: true })

  await del.click()
  const dlg = dialogOf(page)
  await dlg.waitFor()
  check('staff delete: a dialog opens that names the account', (await dlg.getAttribute('aria-labelledby')) !== null && (await dlg.locator('h2').textContent()).includes(doomed.name) && (await dlg.locator('h2').textContent()).startsWith('Delete'))
  const a1 = await active(page)
  check('staff delete: focus starts on the safe button (Cancel), inside the dialog', a1.text === 'Cancel' && a1.inDialog, a1.text)
  check('staff delete: the page behind is locked while it is open', await bodyLocked(page))
  check('staff delete: Tab and Shift+Tab never leave the dialog', await focusStaysInside(page))
  await page.keyboard.press('Escape')
  await dlg.waitFor({ state: 'detached' })
  const a2 = await active(page)
  check('staff delete: Escape closes it and focus returns to the Delete button', a2.text === 'Delete' && !a2.inDialog, a2.text)
  check('staff delete: the page behind is unlocked again', !(await bodyLocked(page)))
  check('staff delete: Escape deleted nothing', (await listStaff()).some((s) => s.id === doomed.id))

  await del.click()
  await dlg.waitFor()
  await page.mouse.click(4, 4)
  await dlg.waitFor({ state: 'detached' })
  check('staff delete: a click outside closes it too', (await listStaff()).some((s) => s.id === doomed.id))

  await del.click()
  await dlg.getByRole('button', { name: 'Delete staff account' }).click()
  await dlg.waitFor({ state: 'detached' })
  await row.waitFor({ state: 'detached' })
  check('staff delete: confirming deletes the account and the row goes', !(await listStaff()).some((s) => s.id === doomed.id))
  const a3 = await active(page)
  check('staff delete: with the opener gone, focus goes to the page content (not lost on the body)', a3.tag === 'MAIN', a3.tag)

  // an account that is part of an order's history cannot be deleted: the reason appears in the dialog
  await rowOf(page, '.staff-row', 'M. Behr').getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  await dlg.getByRole('button', { name: 'Delete staff account' }).click()
  const alert = dlg.getByRole('alert')
  await alert.waitFor()
  check('staff delete failure: the reason is shown inside the dialog, which stays open', /history/i.test(await alert.textContent()) && (await dlg.isVisible()), await alert.textContent())
  check('staff delete failure: the button now reads "Try again"', (await dlg.getByRole('button', { name: 'Try again' }).count()) === 1)
  await page.screenshot({ path: `${OUT}/staff-delete-failed.png` })
  await dlg.getByRole('button', { name: 'Cancel' }).click()
  check('staff delete failure: nothing was deleted', (await listStaff()).some((s) => s.name === 'M. Behr'))
  await context.close()
}

console.log('--- staff: reset PIN')
{
  const { context, page } = await open()
  const temp = await mk.staff(`PIN Waiter ${rid}`, '0000')
  await go(page, 'staff', '.staff-row')
  const row = rowOf(page, '.staff-row', temp.name)
  const opener = row.getByRole('button', { name: 'Reset PIN', exact: true })
  await opener.click()
  const dlg = dialogOf(page)
  await dlg.waitFor()
  const input = dlg.getByLabel('New PIN')
  check('PIN: the title names the account', (await dlg.locator('h2').textContent()).includes(temp.name) && (await dlg.locator('h2').textContent()).startsWith('Reset PIN for'))
  check('PIN: the field has a visible label and its rule', (await input.isVisible()) && (await dlg.getByText('4 to 8 digits').isVisible()))
  const a = await active(page)
  check('PIN: focus starts in the field (hidden digits, numeric keypad)', a.tag === 'INPUT' && a.type === 'password' && (await input.getAttribute('inputmode')) === 'numeric')
  check('PIN: Tab never leaves the dialog', await focusStaysInside(page, 10, 3))

  await input.fill('12')
  check('PIN: typing does not scold', (await dlg.getByRole('alert').count()) === 0)
  await input.blur()
  const msg = dlg.getByText('The PIN must be 4 to 8 digits.')
  await msg.waitFor()
  check('PIN: leaving the field with a bad PIN shows what is wrong under it', (await input.getAttribute('aria-invalid')) === 'true')
  await input.fill('1234')
  check('PIN: the message goes the moment the PIN is fine (no need to leave the field)', (await msg.count()) === 0 && (await input.getAttribute('aria-invalid')) === 'false')
  await input.fill('')
  await dlg.getByRole('button', { name: 'Set new PIN' }).click()
  await msg.waitFor()
  check('PIN: submitting empty shows the message and puts focus back in the field', (await active(page)).tag === 'INPUT')
  await dlg.getByLabel('Show PIN').check()
  check('PIN: "Show PIN" reveals the digits', (await input.getAttribute('type')) === 'text')

  await page.route('**/reset-pin', (route) => route.abort())
  await input.fill('5678')
  await dlg.getByRole('button', { name: 'Set new PIN' }).click()
  const err = dlg.getByRole('alert').filter({ hasText: 'Could not update the PIN' })
  await err.waitFor()
  check('PIN: a server error stays in the dialog, with "Try again"', (await dlg.getByRole('button', { name: 'Try again' }).count()) === 1)
  await page.unroute('**/reset-pin')
  await dlg.getByRole('button', { name: 'Try again' }).click()
  await dlg.waitFor({ state: 'detached' })
  const toast = page.locator('.toast', { hasText: 'PIN updated for' })
  await toast.waitFor()
  check('PIN: success closes the dialog and a toast names the account (announced politely)', (await toast.textContent()).includes(temp.name) && (await toast.getAttribute('role')) === 'status')
  const fresh = await api('/staff/login', { method: 'POST', body: { name: temp.name, pin: '5678' }, raw: true })
  const stale = await api('/staff/login', { method: 'POST', body: { name: temp.name, pin: '0000' }, raw: true })
  check('PIN: the new PIN works and the old one does not', fresh.status === 200 && stale.status === 401, `${fresh.status} / ${stale.status}`)
  const a2 = await active(page)
  check('PIN: focus is back on the Reset PIN button', a2.text === 'Reset PIN', a2.text)
  await page.screenshot({ path: `${OUT}/pin-toast.png` })
  await toast.waitFor({ state: 'detached', timeout: 8000 })
  check('PIN: the success toast goes away by itself', true)

  await opener.click(); await dlg.waitFor(); await page.keyboard.press('Escape'); await dlg.waitFor({ state: 'detached' })
  check('PIN: Escape closes it and focus returns to the opener', (await active(page)).text === 'Reset PIN')
  await context.close()
}

console.log('--- tables: pairing, delete')
{
  const { context, page } = await open()
  const fresh = await mk.table(`P${rid}`)
  const doomed = await mk.table(`D${rid}`)
  await go(page, 'tables', '.table-row')
  const row = rowOf(page, '.table-row', `P${rid}`)
  const dlg = dialogOf(page)
  await row.getByRole('button', { name: 'Pair device' }).click()
  await dlg.waitFor()
  const code = (await listTables()).find((t) => t.id === fresh.id).pairedDeviceId
  const shown = (await dlg.locator('output').textContent()).trim()
  check('pairing: the dialog is titled with the table and shows the code the server issued', (await dlg.locator('h2').textContent()).includes(`P${rid}`) && shown === code, `${shown} / ${code}`)
  const style = await dlg.locator('output').evaluate((el) => ({ size: parseFloat(getComputedStyle(el).fontSize), dir: getComputedStyle(el).direction, font: getComputedStyle(el).fontFamily }))
  check('pairing: the code is large, monospace and left to right', style.size >= 32 && style.dir === 'ltr' && /mono/i.test(style.font), JSON.stringify(style))
  check('pairing: focus starts on Copy, Tab stays inside', (await active(page)).text === 'Copy' && (await focusStaysInside(page, 6, 2)))
  await dlg.getByRole('button', { name: 'Copy' }).click()
  const copied = dlg.getByText('Copied')
  await copied.waitFor()
  check('pairing: Copy puts the code on the clipboard and says "Copied"', (await page.evaluate(() => navigator.clipboard.readText())) === code)
  await copied.waitFor({ state: 'hidden', timeout: 4000 })
  check('pairing: "Copied" goes away again', true)
  await page.screenshot({ path: `${OUT}/pairing.png` })
  await dlg.getByRole('button', { name: 'Done' }).click()
  await dlg.waitFor({ state: 'detached' })
  check('pairing: Done closes it and focus stays on the page content', ['MAIN', 'BUTTON'].includes((await active(page)).tag))

  // a failed pairing is a toast, not a dialog
  await row.getByRole('button', { name: 'Unpair' }).click()
  await row.getByRole('button', { name: 'Pair device' }).waitFor()
  await page.route('**/pair', (route) => route.abort())
  await row.getByRole('button', { name: 'Pair device' }).click()
  const errToast = page.locator('.toast', { hasText: 'Could not update the paired device' })
  await errToast.waitFor()
  check('pairing failure: a toast says so (announced at once), and no dialog opened', (await errToast.getAttribute('role')) === 'alert' && (await dlg.count()) === 0)
  await errToast.getByRole('button', { name: 'Dismiss' }).click()
  await errToast.waitFor({ state: 'detached' })
  check('pairing failure: the toast can be dismissed', true)
  await page.unroute('**/pair')

  // deleting
  const doomedRow = rowOf(page, '.table-row', `D${rid}`)
  await doomedRow.getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  check('table delete: the dialog names the table and starts on Cancel', (await dlg.locator('h2').textContent()).includes(`D${rid}`) && (await active(page)).text === 'Cancel')
  await dlg.getByRole('button', { name: 'Delete table' }).click()
  await doomedRow.waitFor({ state: 'detached' })
  check('table delete: confirming deletes it', !(await listTables()).some((t) => t.id === doomed.id))

  await page.locator('.table-row', { has: page.locator('.table-row__name', { hasText: /^1$/ }) }).getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  await dlg.getByRole('button', { name: 'Delete table' }).click()
  await dlg.getByRole('alert').waitFor()
  check('table delete failure: a table with orders says why, inside the dialog', /orders/i.test(await dlg.getByRole('alert').textContent()) && (await listTables()).some((t) => t.tableNumber === '1'))
  await dlg.getByRole('button', { name: 'Cancel' }).click()
  await context.close()
}

console.log('--- meals, categories, raw materials')
{
  const { context, page } = await open()
  const dlg = dialogOf(page)
  const cat = await mk.category(`Cat ${rid}`)
  const meal = await mk.meal(cat.id, `Meal ${rid}`)
  const keepMeal = await mk.meal(cat.id, `Keep ${rid}`)
  await go(page, 'meals', '.meal-row')

  await rowOf(page, '.meal-row', `Meal ${rid}`).getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  check('meal delete: the dialog names the meal and says orders keep their copy', (await dlg.locator('h2').textContent()).includes(`Meal ${rid}`) && /own copy/.test(await dlg.textContent()))
  await dlg.getByRole('button', { name: 'Delete meal' }).click()
  await rowOf(page, '.meal-row', `Meal ${rid}`).waitFor({ state: 'detached' })
  check('meal delete: confirming deletes it', !(await api('/meals', { cookie: admin })).json.some((m) => m.id === meal.id))

  await rowOf(page, '.categories-row', 'Mains').getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  await dlg.getByRole('button', { name: 'Delete category' }).click()
  await dlg.getByRole('alert').waitFor()
  check('category delete failure: "it may still have meals" appears inside the dialog', /meals/i.test(await dlg.getByRole('alert').textContent()))
  await dlg.getByRole('button', { name: 'Cancel' }).click()

  await api(`/meals/${keepMeal.id}`, { method: 'DELETE', cookie: admin })
  await page.reload(); await page.waitForSelector('.categories-row')
  await rowOf(page, '.categories-row', `Cat ${rid}`).getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  check('category delete: the dialog names the category', (await dlg.locator('h2').textContent()).includes(`Cat ${rid}`))
  await dlg.getByRole('button', { name: 'Delete category' }).click()
  await rowOf(page, '.categories-row', `Cat ${rid}`).waitFor({ state: 'detached' })
  check('category delete: confirming deletes it', true)

  await page.route('**/availability', (route) => route.abort())
  await page.locator('.meal-row__availability').first().click()
  const t = page.locator('.toast', { hasText: 'Could not update availability' })
  await t.waitFor()
  check('availability failure: a toast, not an alert', (await t.getAttribute('role')) === 'alert')
  await page.unroute('**/availability')

  const mat = await mk.material(`Mat ${rid}`)
  await go(page, 'materials', '.material-row')
  await rowOf(page, '.material-row', `Mat ${rid}`).getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  await dlg.getByRole('button', { name: 'Delete raw material' }).click()
  await rowOf(page, '.material-row', `Mat ${rid}`).waitFor({ state: 'detached' })
  check('raw material delete: confirming deletes it', !(await api('/raw-materials', { cookie: admin })).json.some((m) => m.id === mat.id))
  await rowOf(page, '.material-row', 'Onion').getByRole('button', { name: 'Delete', exact: true }).click()
  await dlg.waitFor()
  await dlg.getByRole('button', { name: 'Delete raw material' }).click()
  await dlg.getByRole('alert').waitFor()
  check('raw material delete failure: "it may still be used in a recipe" appears inside the dialog', /recipe/i.test(await dlg.getByRole('alert').textContent()))
  await dlg.getByRole('button', { name: 'Cancel' }).click()
  await context.close()
}

console.log('--- motion')
const dialogAnimation = (page) => page.evaluate(() => ({
  overlay: getComputedStyle(document.querySelector('.modal__overlay')).animationName,
  card: getComputedStyle(document.querySelector('.modal__card')).animationName,
  toast: document.querySelector('.toast') ? getComputedStyle(document.querySelector('.toast')).animationName : null,
}))
{
  const { context, page } = await open('en', 'light')
  await go(page, 'tables', '.table-row')
  await page.locator('.table-row').first().getByRole('button', { name: 'Delete', exact: true }).click()
  await dialogOf(page).waitFor()
  const anim = await dialogAnimation(page)
  check('motion: by default a dialog fades and rises in (a short fade)', anim.overlay !== 'none' && anim.card !== 'none', JSON.stringify(anim))
  await page.keyboard.press('Escape')
  await context.close()
}
{
  const { context, page } = await open('en', 'light', { reducedMotion: 'reduce' })
  await go(page, 'tables', '.table-row')
  await page.locator('.table-row').first().getByRole('button', { name: 'Delete', exact: true }).click()
  await dialogOf(page).waitFor()
  const anim = await dialogAnimation(page)
  const running = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running').length)
  check('reduce motion: the dialog has no animation at all', anim.overlay === 'none' && anim.card === 'none' && running === 0, JSON.stringify(anim) + ` / ${running} running`)
  await page.keyboard.press('Escape')
  // a toast too
  const spare = await mk.table(`M${rid}`)
  await page.route('**/pair', (route) => route.abort())
  await go(page, 'tables', '.table-row')
  await rowOf(page, '.table-row', spare.tableNumber).getByRole('button', { name: 'Pair device' }).click()
  await page.locator('.toast').first().waitFor()
  check('reduce motion: a toast appears with no animation', (await dialogAnimationToast(page)) === 'none')
  await context.close()
}
async function dialogAnimationToast(page) { return page.evaluate(() => getComputedStyle(document.querySelector('.toast')).animationName) }

// ================================================================ German and Arabic, both themes: nothing cut off, readable
console.log('--- German and Arabic, light and dark')
const contrastOf = () => {
  const parse = (css) => {
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
  const lum = ([r, g, b]) => { const c = [r, g, b].map((v) => { const s = v / 255; return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4 }); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2] }
  return (selector) => [...document.querySelectorAll(selector)].map((el) => {
    const layers = []
    for (let n = el; n; n = n.parentElement) layers.push(parse(getComputedStyle(n).backgroundColor))
    const bg = layers.reverse().reduce((u, l) => over(l, u), [255, 255, 255, 1])
    const fg = over(parse(getComputedStyle(el).color), bg)
    const [hi, lo] = [lum(fg), lum(bg)].sort((x, y) => y - x)
    const size = parseFloat(getComputedStyle(el).fontSize)
    return { ratio: (hi + 0.05) / (lo + 0.05), needed: size >= 24 || (size >= 18.66 && Number(getComputedStyle(el).fontWeight) >= 700) ? 3 : 4.5, text: (el.textContent ?? '').trim().slice(0, 22) }
  })
}
for (const lang of ['de', 'ar']) {
  for (const theme of ['light', 'dark']) {
    const tag = `${lang} ${theme}`
    const { context, page } = await open(lang, theme)
    const temp = await mk.table(`L${lang}${theme[0]}${rid}`)
    const staffAcc = await mk.staff(`Lang ${lang}${theme[0]} ${rid}`)
    const dir = lang === 'ar' ? 'rtl' : 'ltr'
    const measure = async (label) => {
      const dlg = dialogOf(page)
      await dlg.waitFor()
      await page.waitForTimeout(350)
      const r = await page.evaluate((contrast) => {
        const card = document.querySelector('[role=dialog]')
        const box = card.getBoundingClientRect()
        const fn = new Function(`return (${contrast})()`)()
        const checks = ['.modal__title', '.dialog__message', '.dialog__error', '.dialog .button, .modal__body .button', '.pin-dialog__field span', '.pin-dialog__hint', '.pin-dialog__error', '.pairing-dialog__code', '.pairing-dialog__copied', '.toast__message'].flatMap((sel) => fn(sel))
        return {
          dir: document.documentElement.dir,
          inside: box.left >= 0 && box.right <= innerWidth && box.top >= 0 && box.bottom <= innerHeight,
          cutOff: card.scrollWidth > card.clientWidth + 1,
          low: checks.filter((c) => c.ratio < c.needed).map((c) => `"${c.text}" ${c.ratio.toFixed(2)}:1`),
        }
      }, contrastOf.toString())
      check(`${tag}, ${label}: right direction, inside the window, nothing cut off`, r.dir === dir && r.inside && !r.cutOff, JSON.stringify({ dir: r.dir, inside: r.inside, cutOff: r.cutOff }))
      check(`${tag}, ${label}: text contrast 4.5:1 (3:1 for large text)`, r.low.length === 0, r.low.join('; '))
      await page.screenshot({ path: `${OUT}/${lang}-${theme}-${label.replace(/\W+/g, '-')}.png` })
    }
    await go(page, 'tables', '.table-row')
    await rowOf(page, '.table-row', temp.tableNumber).locator('.table-row__actions button').nth(0).click() // Pair device
    await measure('pairing')
    const codeDir = await page.locator('.pairing-dialog__code').evaluate((el) => getComputedStyle(el).direction)
    check(`${tag}, pairing: the code stays left to right`, codeDir === 'ltr')
    await page.keyboard.press('Escape')
    await rowOf(page, '.table-row', temp.tableNumber).locator('.table-row__actions button').nth(2).click() // Delete
    await measure('delete')
    await page.getByRole('dialog').locator('.button--danger').click() // delete the throwaway table
    await dialogOf(page).waitFor({ state: 'detached' })
    await go(page, 'staff', '.staff-row')
    await rowOf(page, '.staff-row', staffAcc.name).locator('.staff-row__actions button').nth(1).click() // Reset PIN
    await measure('PIN')
    await page.keyboard.press('Escape')
    await context.close()
  }
}

await browser.close()
check('the browser\'s own alert, confirm or prompt never appeared', nativeDialogs.length === 0, nativeDialogs.join(' | '))
const failed = results.filter((r) => !r).length
console.log(`\n${results.length - failed}/${results.length} checks passed. Screenshots are in ${OUT}`)
process.exit(failed ? 1 : 0)
