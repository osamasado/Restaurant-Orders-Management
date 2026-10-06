# QA checklist

The QA pass for issue #31: all four screens (guest, kitchen, hall, admin) in all three languages (German, English, Arabic). Each row says how it was verified:

- **Walk**: `frontend/scripts/qa-walk.mjs` drove a real browser through the screen in each language and both themes, with orders in every state, and the page was also looked at in its screenshot. This proves the screen renders correctly with real data in that language.
- **Test**: an automated test verifies the behaviour. The logic is the same in every language, so one test covers all three.
- **Human**: needs a person. These boxes are left for whoever does it.

✅ = verified, ⬜ = still to do.

Run the walk against a throwaway stack (own database), never your dev database: it places orders. Prefer a production build (`npm run build`, then `vite preview`) over the dev server: the dev server loads modules on demand and, on a slow machine, sometimes had not rendered a page when the walk looked at it.

## The automated walk

It checks, for every page: no console error and no unexpected failed request; no missing-translation warning; no horizontal overflow; nothing wider than the box it sits in; the right `<html lang>` and `dir`; Latin digits only in Arabic; no text clipped by an ellipsis or hidden overflow; and that the page is not blank. It saves a screenshot per page. Options (languages, themes, retry) are at the top of the script.

### Result

**114 of 114 pages pass** on the production build, on the first attempt, with retries turned off: 3 languages x 2 themes x 19 pages (guest 6, kitchen 3, hall 1, admin 9: seven views plus the meal form and the staff form). The German, English and Arabic screenshots were reviewed by eye as well; German was reviewed for the first time.

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
| Readable across a kitchen: sizes, amber and green contrast on the real screen | **Human** | ⬜ | ⬜ | ⬜ |

### Hall board (dining-area screen, 1920 x 1080)

| Check | How | DE | EN | AR |
|---|---|---|---|---|
| Two columns, numbers stacked with their table, amber in preparation and green ready | Walk | ✅ | ✅ | ✅ |
| A number moves from In preparation to Ready and disappears once served | Test (`OrderLifecycleEndToEndTest`) | ✅ | ✅ | ✅ |
| Only numbers and tables: no names, prices or items | Walk + Test (`HallBoardControllerTest`) | ✅ | ✅ | ✅ |
| Legible from the back of the dining room on the real screen | **Human** | ⬜ | ⬜ | ⬜ |

### Admin (office computer, 1440 x 900)

| Check | How | DE | EN | AR |
|---|---|---|---|---|
| Sign-in screen | Walk | ✅ | ✅ | ✅ |
| A non-admin account is told it has no access | Test (`AccessControlMatrixTest`) | ✅ | ✅ | ✅ |
| Audit history: each order's stages with time and actor | Walk + browser check in #30 | ✅ | ✅ | ✅ |
| Meals and categories: list and the edit form with all three translations | Walk | ✅ | ✅ | ✅ |
| Raw materials, tables and devices, staff accounts with their form, settings | Walk | ✅ | ✅ | ✅ |
| Pair and unpair a device, the staff PIN rules, own role locked | Test + browser check in #29 | ✅ | ✅ | ✅ |
| Long labels fit the sidebar, buttons and forms | Walk (the German sidebar bug was found and fixed here) | ✅ | ✅ | ✅ |
| Wording reads naturally | **Human** | ⬜ | ⬜ | ⬜ |

## One order across all four screens

The same journey is verified automatically in `OrderLifecycleEndToEndTest`. Doing it once by hand per language, with the real devices if possible, is the human check:

- [ ] **DE**: place an order on the guest tablet; it appears in the kitchen's New column within about 5 seconds; Start puts it on the hall board under In preparation with its table; Ready moves it to Ready; Picked up removes it; the guest's timeline followed every step; the admin history shows each step with its actor.
- [ ] **EN**: the same.
- [ ] **AR**: the same.
- [ ] **Cancel path** (any language): an admin cancels a preparing order (API for now); the kitchen shows the banner until acknowledged, the hall board drops it, the guest sees "cancelled".

## The non-negotiable rules

Where each rule from `CLAUDE.md` is verified. All of these run on every build.

| Rule | Verified by |
|---|---|
| One server-side state machine, no direct status writes; every change timestamped with who made it | `OrderStateMachineServiceTest`, `OrderLifecycleEndToEndTest` (skipped steps refused, trail with actors), `NonNegotiableRulesGuardTest` (only the state machine moves an order; no status setter) |
| Order numbers from a locked transaction, unique under concurrency | `OrderNumberConcurrencyTest` (state machine), `GuestSubmissionConcurrencyTest` (30 simultaneous guest submissions over real HTTP: distinct, consecutive, refused ones use none), `NonNegotiableRulesGuardTest` (only the state machine takes a number) |
| Prices and tax calculated on the server, never trusted from the client | `OrderPricingServiceTest`, `GuestCartQuoteControllerTest`, `OrderLifecycleEndToEndTest` (a client-sent total is ignored), `NonNegotiableRulesGuardTest` (requests cannot carry a price) |
| Order lines snapshot the meal, so later menu edits never rewrite orders | `OrderItemSnapshotTest`, `OrderLifecycleEndToEndTest` (price and name changed after the order, order unchanged), `NonNegotiableRulesGuardTest` (an order line cannot reference the menu) |
| German, English, Arabic: full right-to-left Arabic, labels and menu content translated | `npm run check:i18n` (same keys and placeholders in all three), `DemoDataSeederTest` (every meal fully translated), the walk (direction, digits, no clipped text) and the per-screen rows above |
| Role-based access per screen | `AccessControlMatrixTest` (every endpoint against the table), `SessionRevocationTest`, `Documentation/access-control.md` |
