# QA checklist

The QA pass for issue #31: all four screens (guest, kitchen, hall, admin) in all three languages (German, English, Arabic). Each row says how it was verified:

- **Walk**: `frontend/scripts/qa-walk.mjs` drove a real browser through the screen in each language and both themes, with orders in every state, and the page was also looked at in its screenshot. This proves the screen renders correctly with real data in that language.
- **Test**: an automated test verifies the behaviour. The logic is the same in every language, so one test covers all three.
- **Human**: needs a person. These boxes are left for whoever does it.

✅ = verified, ⬜ = still to do.

Run the walk against a throwaway stack (own database), never your dev database: it places orders. Prefer a production build (`npm run build`, then `vite preview`) over the dev server: the dev server loads modules on demand and, on a slow machine, sometimes had not rendered a page when the walk looked at it.

A second script, `frontend/scripts/boards-check.mjs` (issue #62), checks the two wall boards only, on a typical and on a busy evening, at 1920 x 1080 and 1366 x 768, in three languages and both themes: nothing off the screen, every number reachable, the designed sizes, contrast, motion (including "reduce motion") and Arabic mirroring. It places orders, so it also needs a throwaway stack, and a fresh one per run (a busy evening gets busier every time).

## The automated walk

It checks, for every page: no console error and no unexpected failed request; no missing-translation warning; no horizontal overflow; nothing wider than the box it sits in; the right `<html lang>` and `dir`; Latin digits only in Arabic; no text clipped by an ellipsis or hidden overflow; no picture that failed to load; and that the page is not blank. It saves a screenshot per page. Options (languages, themes, retry) are at the top of the script.

### Result

**162 of 162 pages pass** on the production build, on the first attempt, with retries turned off: 3 languages x 2 themes x 27 pages (guest 6, kitchen 3, hall 1, admin 17: eight views including the Dashboard, the Orders cancel confirmation, the meal form and the staff form, three dialogs (a delete confirmation, the PIN reset and the pairing code), and three phone pages (the Dashboard, the navigation drawer and an order opened in its dialog)). The German, English and Arabic screenshots were reviewed by eye as well; German was reviewed for the first time. After the dark, coral restyle (#79) the walk was refined in two ways: it ignores text that is hidden on purpose (`clip-path`) and elements that are off-screen on purpose (a closed drawer, a row inside its own sideways scroller), and the German phone pages found a real bug that was fixed (the "Nicht verfügbar" pill ran out of its meal card).

What the pass found and fixed:

- **German sidebar overflow (fixed).** In the admin sidebar the language and theme buttons were wider than the sidebar in German, so "DUNKEL" was cut off. The row now wraps. The walk's old checks only compared against the viewport, so a "wider than its container" check was added; it would have caught this.
- **Seeded pairing codes could not be typed (fixed, #31).** The demo tables had device ids like `tablet-a1`, which a guest device (6 characters, upper case) can never enter. They are now six-character codes (`ALPHA2`, `BRAVE3`, ...), and an already-seeded database is moved over.
- **Walk hardening.** A page that had not rendered yet passed every check because it was blank, so the walk now waits for the page to settle and fails a blank page. Failed requests are judged by response (only the signed-out session check and a wrong PIN may be 401) instead of by Chrome's console noise, and the deliberate wrong-PIN name is unique per run (the sign-in throttle correctly locks a name after five failures, which showed up as HTTP 429).
- **Dev-server instability (not a product defect).** On the Vite dev server a few screens occasionally never rendered within 60 seconds, a different one each run. The same walk against the production build passed 114/114 first time, so this was the dev server loading modules on demand on a slow drive.

Observations that are not defects, for a decision:

- **Order items are in the guest's language.** The kitchen shows each order line as the guest ordered it, so a German kitchen sees English sizes ("Regular", "Half board") on an order placed in English. This is the "order lines snapshot the meal" rule; showing items in the restaurant's language would be a data-model change.
- **The meal form's category list follows the language tab being edited** (it opens on English), not the interface language. It is deliberate: the form edits one language at a time.
- **"Restaurant Orders Management"** is the same in all three languages. It is a placeholder name.
- **The guest pairing screen is not part of the walk** (the walk presets the paired device), so it is a Human row below.

## Per screen

### Guest (phone, 390 x 844)

| Check | How | DE | EN | AR |
|---|---|---|---|---|
| Pairing screen: a valid code connects, an unknown code is refused with a clear message | Human (the API side is tested) | ⬜ | ⬜ | ⬜ |
| Welcome: the language buttons switch the whole screen, and the choice carries on | Walk | ✅ | ✅ | ✅ |
| Menu: categories, meals, descriptions and prices in the chosen language | Walk (unavailable meals hidden: Test) | ✅ | ✅ | ✅ |
| Menu: every meal has its picture, and a meal the kitchen marks sold out vanishes from a menu that is already open (refreshes every 5 s) | Walk (pictures) + Rehearsal (`demo-rehearsal.mjs`, gone within 5.4 s) | ✅ | ✅ | ✅ |
| Meal detail: description, preparation, ingredients, sizes with prices, quantity, note | Walk | ✅ | ✅ | ✅ |
| Cart: lines, subtotal, tax and total (the figures are the server's) | Walk + Test (`OrderLifecycleEndToEndTest`) | ✅ | ✅ | ✅ |
| Cart: a line can be removed | Human | ⬜ | ⬜ | ⬜ |
| Payment: the enabled methods and the total | Walk | ✅ | ✅ | ✅ |
| Confirmation: three-digit order number, status timeline | Walk | ✅ | ✅ | ✅ |
| The timeline follows the kitchen step by step | Test (`OrderLifecycleEndToEndTest`) | ✅ | ✅ | ✅ |
| Back and forward arrows point the way the text reads | Walk + checked on the page in #27 | ✅ | ✅ | ✅ |
| Wording reads naturally, not like a translation | **Human** | ⬜ | ⬜ | ⬜ |
| Comfortable on a real tablet: touch targets, scrolling, keyboard not hiding the note field | **Human** | ⬜ | ⬜ | ⬜ |

### Kitchen (wall monitor, 1440 x 900)

| Check | How | DE | EN | AR |
|---|---|---|---|---|
| Sign-in screen, and a wrong PIN shows the error | Walk | ✅ | ✅ | ✅ |
| Five wrong PINs show the locked message | Test + browser check in #29 (English) | ⬜ | ✅ | ⬜ |
| Board: New, In preparation and Ready, each in its colour, with counts | Walk | ✅ | ✅ | ✅ |
| A card shows number, table, timer, items, sizes, notes and the one right action | Walk | ✅ | ✅ | ✅ |
| The cancelled-order banner shows for an admin cancellation | Walk | ✅ | ✅ | ✅ |
| Acknowledge clears the banner | Test (`OrderLifecycleEndToEndTest`) | ✅ | ✅ | ✅ |
| "Ran out?" chips show, and a toggle takes the meal off the guest menu | Walk + Test (`KitchenMealControllerTest`) | ✅ | ✅ | ✅ |
| Visual pass (#62): the page never scrolls, the header, the cancelled banner and the "Ran out?" chips stay on screen, each column scrolls by itself and its last card and Start button can be reached (busy evening, 1920 x 1080 and 1366 x 768) | Boards check (`frontend/scripts/boards-check.mjs`) | ✅ | ✅ | ✅ |
| Visual pass (#62): solid Start and Ready, timer chips (neutral, amber from 6 min, clay from 12), clay Acknowledge, tinted sold-out chip, columns with their header inside, as in `shots/kitchen.png`; text contrast 4.5:1 (3:1 for large text) in light and dark | Boards check (contrast) + screenshots looked at | ✅ | ✅ | ✅ |
| Arabic: columns mirrored (New on the right), quantity stays "2×" | Boards check + screenshots | ✅ | ✅ | ✅ |
| Readable across a kitchen: sizes, amber and green contrast on the real screen | **Human** | ⬜ | ⬜ | ⬜ |

### Hall board (dining-area screen, 1920 x 1080)

| Check | How | DE | EN | AR |
|---|---|---|---|---|
| Two columns, numbers stacked with their table, amber in preparation and green ready | Walk | ✅ | ✅ | ✅ |
| A number moves from In preparation to Ready and disappears once served | Test (`OrderLifecycleEndToEndTest`) | ✅ | ✅ | ✅ |
| Only numbers and tables: no names, prices or items | Walk + Test (`HallBoardControllerTest`) | ✅ | ✅ | ✅ |
| Visual pass (#62): In preparation tint panel, Ready forest panel with its ring (both themes), numbers 74 px and 86 px on a quiet board | Boards check + screenshots looked at | ✅ | ✅ | ✅ |
| Visual pass (#62): the In preparation dot blinks (2 s) and is the only continuous motion; a number that arrives, or moves to Ready, fades and rises in (0.4 s) and the others do not move; with "reduce motion" there is no animation at all | Boards check (live, with a new order) | ✅ | ✅ | ✅ |
| A busy evening fits the screen: 24 preparing and 7 ready orders at 1920 x 1080 and at 1366 x 768 (and a database with over 80 preparing), nothing cut off, numbers never below 28 px | Boards check | ✅ | ✅ | ✅ |
| Arabic: mirrored (In preparation on the right, numbers start at the right edge, table label to their left) | Boards check + screenshots | ✅ | ✅ | ✅ |
| Legible from the back of the dining room on the real screen | **Human** | ⬜ | ⬜ | ⬜ |

### Admin (office computer, 1440 x 900)

| Check | How | DE | EN | AR |
|---|---|---|---|---|
| Sign-in screen | Walk | ✅ | ✅ | ✅ |
| A non-admin account is told it has no access | Test (`AccessControlMatrixTest`) | ✅ | ✅ | ✅ |
| Audit history: each order's stages with time and actor | Walk + browser check in #30 | ✅ | ✅ | ✅ |
| Orders: every order newest first with items, table, time, payment, total and status; one button per legal step plus Cancel; finished orders say "no further steps" | Walk + browser check in #71 | ✅ | ✅ | ✅ |
| Orders: filter by status and by day (the day is the admin's own, tested in far-apart time zones), "no match" message, "Clear filters", survives the 5 s refresh | Test (`AdminOrderListControllerTest`) + browser check in #74 (English; the Walk renders the filter bar in all three languages) | ⬜ | ✅ | ⬜ |
| Orders: Cancel asks for confirmation first, and "Keep it" leaves the order alone | Walk (confirmation page) + browser check in #71 | ✅ | ✅ | ✅ |
| Orders: Start, Ready and Served move the order and the audit history names the admin; a clash with another screen shows the notice and refreshes; a failed refresh keeps the rows | Test (`AdminOrderControllerTest`) + browser check in #71 (English; the logic is the same in every language) | ⬜ | ✅ | ⬜ |
| Orders: cancelling shows the kitchen banner and removes the number from the hall board | Browser check in #71 (English) | ⬜ | ✅ | ⬜ |
| Dashboard (the landing page): categories, meal cards, order reports and the order panel render with real data, in both themes, and mirror in Arabic (rail on the right, panel on the left) | Walk | ✅ | ✅ | ✅ |
| Dashboard: Start moves a submitted order to preparing from the panel, Cancel asks first and "Keep it" leaves it, confirming cancels it; a cancelled order offers no further steps | Browser check (English; the logic is the same as the Orders page) | ⬜ | ✅ | ⬜ |
| Dashboard: a meal can be marked unavailable and back from its card; the search narrows meals and orders and shows the empty states; a category filters the meals and is announced as pressed | Browser check (English) | ⬜ | ✅ | ⬜ |
| Guest welcome screen: table badge shows the table from the pairing code, three equal warm-white language buttons (54px, 14px corners), hover beige and lifted, press sinks, the chosen language turns coral and the page switches to it before the menu opens, no scrolling or sideways overflow from 360 x 640 up, the Arabic label is its own right-to-left run, "reduce motion" stops the light, and the text keeps 4.5:1 against the moving light throughout the loop | Browser check (English, with a contrast sampler) + Walk (all pages) + eyes (German light, Arabic dark) | ✅ | ✅ | ✅ |
| Sliders (Dashboard and guest menu): the number of items per page follows the width, "View more" moves to the next items and the arrow goes back, both are hidden when everything fits, and it works right to left | Browser check (English desktop, Arabic phone) | ⬜ | ✅ | ✅ |
| Buttons: every button on the Dashboard, Orders, Audit history, Meals, Raw materials, Tables, Staff, Settings, the dialogs and forms, the kitchen and the guest has a surface, rounded corners and a height of 32 to 48px (the exceptions are listed in the script); hover changes how it looks without changing its size; a coloured button sinks when pressed; a keyboard-focused button shows the ring | `scripts/buttons-check.mjs` (English and Arabic, both themes) | ⬜ | ✅ | ✅ |
| Buttons: primary is coral, success green, warning orange, danger red and secondary gray, so Start preparing is coral, Ready and Served green, Delete and Cancel order red, Edit gray | Eyes (Orders, Meals, Tables, the kitchen) | ⬜ | ✅ | ⬜ |
| Sidebar: the neutral capsule sits behind the icon column, the current page has a coral icon and label and a dot on the capsule edge that slides when you navigate; Settings is the gear at the bottom; the rail mirrors in Arabic and is a drawer on a phone | Browser check (English) + Walk (all pages) + eyes (German, Arabic, light) | ✅ | ✅ | ✅ |
| Guest: the menu is the dashboard layout without the rail, the cart is a panel beside it on a wide screen, a meal added there shows in the panel and the plus raises the server total | Browser check (English) + Walk (phone) | ⬜ | ✅ | ✅ |
| Theme: the toggle switches the whole app, and the choice survives a reload | Browser check (English) | ⬜ | ✅ | ⬜ |
| Phone (390 x 844): the navigation is a drawer that closes on Escape and gives focus back to the menu button, the page behind it is inert, an order opens in a dialog, no sideways scroll | Walk (pages) + browser check (focus and inert, English and Arabic) | ✅ | ✅ | ✅ |
| Meals and categories: list and the edit form with all three translations | Walk | ✅ | ✅ | ✅ |
| Raw materials, tables and devices, staff accounts with their form, settings | Walk | ✅ | ✅ | ✅ |
| Meals and raw materials show their pictures and the demo data fills raw materials, units, stock, suppliers and a recipe for every meal size | Walk (pictures) + Test (`DemoDataSeederTest`) | ✅ | ✅ | ✅ |
| Pair and unpair a device, the staff PIN rules, own role locked | Test + browser check in #29 | ✅ | ✅ | ✅ |
| Who sees payment details: Admin only (kitchen, hall and the waiter and cashier roles do not; a guest sees only their own table's orders) | Test (`AccessControlMatrixTest`, `AdminOrderListControllerTest`) and `Documentation/access-control.md` | ✅ | ✅ | ✅ |
| Long labels fit the sidebar, buttons and forms | Walk (the German sidebar bug was found and fixed here) | ✅ | ✅ | ✅ |
| Wording reads naturally | **Human** | ⬜ | ⬜ | ⬜ |

### Installable app (#28)

| Check | How | Result |
|---|---|---|
| The manifest names the app, starts at `/guest`, is standalone, and has 192, 512 and maskable icons; the icons are served | Browser check of the production build | ✅ |
| The service worker takes control, caches the app shell and the build's scripts, and **never caches `/api`** | Browser check | ✅ |
| Offline: `/guest`, `/kitchen` and `/hall` still open (the app shell, not the browser's error page), and load normally again when back online | Browser check (headless Chromium, connection switched off) | ✅ |
| **"Add to Home Screen" on a real phone or tablet, and reopen it offline** | **Human**: needs the production build (`npm run build`, `npm run preview`) in a secure context: `http://localhost:4173` on the laptop, an Android phone over `adb reverse tcp:4173 tcp:4173`, or an HTTPS tunnel. A plain `http://<laptop address>:4173` (`--host`) is not a secure context: no service worker, no install offer | ⬜ |

The browser check also asks Chrome for its installability errors and got none, but in headless mode that report did not flag a manifest without icons either, so it is not relied on alone; the explicit manifest checks above are.

### The demo

| Check | How | Result |
|---|---|---|
| The demo flow of `demo-script.md` (order on the phone, kitchen, hall board, the guest's timeline, an admin cancel with the kitchen banner, a sold-out meal on an open menu, the audit trail), every hand-off within 10 s | Rehearsal (`frontend/scripts/demo-rehearsal.mjs`, throwaway stack, clean data): 22 of 22, hand-offs 3.6 to 6.8 s | ✅ |
| **A full rehearsal on the real devices, with the talking points** | **Human** | ⬜ |

### Dialogs (admin)

| Check | How | DE | EN | AR |
|---|---|---|---|---|
| The browser's own alert, confirm and prompt never appear; ESLint `no-alert` is on | Dialogs check (`frontend/scripts/dialogs-check.mjs`) + lint | ✅ | ✅ | ✅ |
| Each delete asks in a dialog that names the item, starts on Cancel, deletes on confirm; a failed delete (a staff account or table in an order's history, a category with meals, a raw material in a recipe) shows its reason inside the dialog with "Try again" | Dialogs check | ⬜ | ✅ | ⬜ |
| Reset PIN: label and hint, the error appears on leaving the field and goes when it is fixed, a server error stays in the dialog, the new PIN works and the old one does not | Dialogs check | ⬜ | ✅ | ⬜ |
| Pairing: the code shown large and left to right, Copy puts it on the clipboard and says "Copied"; a failed pairing is a toast | Dialogs check | ⬜ | ✅ | ⬜ |
| Every dialog takes focus, keeps Tab inside, closes on Escape and on a click outside, locks the page behind it and gives focus back; a toast is announced, can be dismissed and goes by itself; "reduce motion" means no animation | Dialogs check | ⬜ | ✅ | ⬜ |
| Dialogs in German and Arabic (right to left): inside the window, nothing cut off, the right direction, text contrast 4.5:1, light and dark | Dialogs check + Walk (three dialog pages per language and theme) | ✅ | ✅ | ✅ |
| Wording of the new dialog texts reads naturally; a screen reader announces the dialogs, errors and toasts; the PIN field shows the numeric keypad on a real tablet | **Human** | ⬜ | ⬜ | ⬜ |

## One order across all four screens

The same journey is verified automatically in `OrderLifecycleEndToEndTest`. Doing it once by hand per language, with the real devices if possible, is the human check:

- [ ] **DE**: place an order on the guest tablet; it appears in the kitchen's New column within about 5 seconds; Start puts it on the hall board under In preparation with its table; Ready moves it to Ready; Picked up removes it; the guest's timeline followed every step; the admin history shows each step with its actor.
- [ ] **EN**: the same.
- [ ] **AR**: the same.
- [ ] **Cancel path** (any language): an admin cancels a preparing order from the admin Orders screen (Cancel, then confirm); the kitchen shows the banner until acknowledged, the hall board drops it, the guest sees "cancelled".

## The non-negotiable rules

Where each rule from `CLAUDE.md` is verified. All of these run on every build.

| Rule | Verified by |
|---|---|
| One server-side state machine, no direct status writes; every change timestamped with who made it | `OrderStateMachineServiceTest`, `OrderLifecycleEndToEndTest` (skipped steps refused, trail with actors), `NonNegotiableRulesGuardTest` (only the state machine moves an order; no status setter) |
| Order numbers from a locked transaction, unique under concurrency | `OrderNumberConcurrencyTest` (state machine), `GuestSubmissionConcurrencyTest` (30 simultaneous guest submissions over real HTTP: distinct, consecutive, refused ones use none), `NonNegotiableRulesGuardTest` (only the state machine takes a number) |
| The displayed order number can be restarted at 001 by an admin, never while an order is open, never racing a submission, recorded with who and when; the internal number is never reset | `OrderNumberResetServiceTest` (restart, refusal while submitted / in preparation / ready, served and cancelled do not block, audit row, nothing recorded when there is nothing to restart, history search after a restart), `OrderNumberResetConcurrencyTest` (a restart racing 20 submissions, five rounds: no number twice, the series stays whole), `DisplayNumberMigrationTest` (existing orders keep their numbers), `OrderNumberControllerTest`, `AccessControlMatrixTest` (Admin only), and in a real browser `order-number-check.mjs` (66 checks in German, English and Arabic: the card, the confirmation, Escape and Cancel change nothing, the explanation with the count and no confirm button while an order is open, the restart, 001 for the guest, the kitchen and the hall board, the history search finding both series, nothing cut off, the number left to right in Arabic) |
| Prices and tax calculated on the server, never trusted from the client | `OrderPricingServiceTest`, `GuestCartQuoteControllerTest`, `OrderLifecycleEndToEndTest` (a client-sent total is ignored), `NonNegotiableRulesGuardTest` (requests cannot carry a price) |
| Order lines snapshot the meal, so later menu edits never rewrite orders | `OrderItemSnapshotTest`, `OrderLifecycleEndToEndTest` (price and name changed after the order, order unchanged), `NonNegotiableRulesGuardTest` (an order line cannot reference the menu) |
| German, English, Arabic: full right-to-left Arabic, labels and menu content translated | `npm run check:i18n` (same keys and placeholders in all three), `DemoDataSeederTest` (every meal fully translated), the walk (direction, digits, no clipped text) and the per-screen rows above |
| Role-based access per screen | `AccessControlMatrixTest` (every endpoint against the table), `SessionRevocationTest`, `Documentation/access-control.md` |
