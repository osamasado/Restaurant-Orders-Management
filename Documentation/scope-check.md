# Scope check against the proposal

The final check of the build against `Final Project Proposal - Restaurant Orders System.pdf` (issue #32): the scope table (section 7) first, then every feature, stage and journey the proposal names. Anything that does not match is called out in "Gaps and deviations" instead of being left silent.

Evidence: **Test** is an automated test that runs on every build; **Walk** is `frontend/scripts/qa-walk.mjs` (every screen in German, English and Arabic, both themes: 120 of 120 pages pass); **Rehearsal** is `frontend/scripts/demo-rehearsal.mjs` (the demo flow in a browser: 22 of 22); **Human** means a person still has to look. Details per screen are in `qa-checklist.md`.

## 1. The scope table (section 7)

### Built in this project

| Proposal item | Status | Evidence |
|---|---|---|
| All four screens, complete | Built | Guest, kitchen, hall board, management backend. Walk, Rehearsal, `OrderLifecycleEndToEndTest` |
| Three languages with right-to-left support | Built | `npm run check:i18n` (325 keys x 3, same placeholders), Walk (right direction, Latin digits, no clipped text). Native-speaker wording review is still **Human** |
| Full menu and raw-material management | Built | Categories, meals (3 languages, sizes, prices, picture), raw materials (unit, stock, supplier, picture), recipes per meal size. `CategoryControllerTest`, `MealControllerTest`, `RawMaterialControllerTest`, `RecipeControllerTest`. The demo data now fills all of it, with pictures |
| Order lifecycle with audit history | Built | `OrderStateMachineServiceTest`, `OrderTransitionsTest`, `NonNegotiableRulesGuardTest`, `AdminOrderHistoryControllerTest`, the Audit history screen |
| Configurable currency, tax and payment methods | Built | `SettingsControllerTest`, the Settings screen; the guest payment step only offers the enabled methods |
| Role-based staff accounts | Built, with one deviation | Admin, kitchen, waiter, cashier accounts and an access table for every endpoint (`AccessControlMatrixTest`, `access-control.md`). Waiter and cashier have no screen, see Gaps |
| Installable on phones and tablets as an app | **Built in this iteration (#28)** | Web app manifest, icons (192, 512, maskable), service worker that keeps the app shell available offline and never caches `/api`. A browser check of the production build: the worker takes control, the manifest passes, and `/guest`, `/kitchen` and `/hall` still open with no connection. The real "Add to Home Screen" on a phone or tablet is **Human**, and needs `localhost` or HTTPS (a phone on plain `http://<laptop address>` cannot install it) |

### Deliberately left for a later version

| Proposal item | State in the build |
|---|---|
| Live card and PayPal charging | As proposed: the flow is built (choose a method, confirm, order submitted), no money is charged |
| Automatic stock deduction from recipes | As proposed: recipes and stock levels exist and are shown, nothing is deducted when an order is placed |
| Kitchen ticket printing | Not built, as proposed |
| Reporting and analytics | Not built, as proposed |
| Instant push updates in place of periodic refresh | As proposed: every screen refreshes every 5 s |

## 2. Features (section 3)

| Feature | Status |
|---|---|
| Browse the menu by category, with photos and prices | Built. The demo menu now has an illustration for every meal (drawn, not photographs) |
| Meal detail: description, preparation method, ingredients | Built |
| Several sizes per meal, each with its own price | Built |
| Quantity control and a free-text note for the kitchen | Built; the note shows on the kitchen card |
| Running total, then subtotal, tax and total in the cart | Built; calculated and verified on the server (`GuestCartQuoteControllerTest`, `OrderPricingServiceTest`) |
| Choice of payment method before confirmation | Built |
| An order number, shown large, with a live status timeline | Built; three digits (`001`); the timeline follows the kitchen (Rehearsal) |
| Add, edit and delete raw materials, categories and meals | Built |
| Each meal: title, description, preparation, ingredients, sizes, prices | Built, in all three languages |
| Recipes link each meal size to its raw materials | Built (meal form), seeded for the demo |
| Mark a meal unavailable: it disappears from every table at once | Built. Fixed in this iteration: a table device that was already showing the menu kept the meal until it was reloaded; the open menu now refreshes every 5 s (Rehearsal: gone within 5.4 s) |
| Register tables and pair each one to its device | Built (six-character pairing codes) |
| Staff accounts with roles: admin, kitchen, waiter, cashier | Built; see Gaps for waiter and cashier |
| Currency code, symbol and position; tax rate; default language; payment methods | Built |
| German, English, Arabic; interface labels and menu content translated; Arabic right-to-left; numbers and currency per language | Built |

## 3. The order process (section 4)

| Stage | Proposal | Build |
|---|---|---|
| Draft | The guest is still adding items; not visible to the kitchen | The cart lives on the guest's device until the guest confirms; the server never stores a draft (see Gaps) |
| Submitted | Confirmed, payment method chosen, number issued | As proposed |
| In preparation | Kitchen screen | As proposed |
| Ready | Kitchen screen; number appears on the hall board | As proposed |
| Served | Waiter or admin | Kitchen screen ("Picked up by waiter") or admin; see Gaps |
| Cancelled | Admin; flagged in red on the kitchen screen | As proposed (banner until acknowledged); not from Served, see Gaps |

Every change is timestamped with the staff member who made it, and no code but the state machine can set a status (`NonNegotiableRulesGuardTest`).

## 4. The three journeys (section 5)

- **Guest:** welcome, language, menu, meal detail with size, quantity and note, cart and total, payment, confirm, number and live status. Built; Walk and Rehearsal run it.
- **Kitchen staff:** new order appears, Start, Ready (number appears on the hall board), a cancelled order is flagged in red until acknowledged, a meal that ran out vanishes from every table device. Built; Rehearsal runs it.
- **Administrator:** raw materials, categories, meals with translations, sizes, prices and recipes, tables and staff accounts, currency, tax and payment methods, then **monitor all orders, filter by date and status, and move any order to the correct status**. Built. The Orders list and its **status and date filter** are new in this iteration (#74); the order can be moved forward or cancelled, see Gaps for moving back.

## 5. Gaps and deviations, stated explicitly

These are where the build differs from the proposal's wording. None is a hidden gap; each is a decision or a known limit.

1. **Waiter and cashier have no screen.** The accounts exist and can sign in, but every staff screen answers "access denied" for them until a screen is built for those roles (`access-control.md`). So "Served: waiter or admin" is done today from the kitchen screen's "Picked up by waiter" button, or by the admin. Not built, by decision: a waiter or cashier screen is outside the four screens the proposal lists.
2. **No moving an order backwards.** The admin can move an order forward or cancel it. The proposal says "move any order to the correct status when something goes wrong on the floor"; the state machine, which the proposal itself calls the core of the project, has no backward transitions, so a wrong step is corrected by cancelling and re-ordering.
3. **A served order cannot be cancelled.** The proposal draws Cancelled "from any stage"; Served and Cancelled are terminal in the build.
4. **Draft is not a stored stage.** The guest's cart is held on the device and the order is created already submitted. The state machine still has the Draft step and the cancelled-draft case, but nothing is saved before the guest confirms, so the kitchen can never see one (which is the point of the stage).
5. **The kitchen screen has three buttons, not two:** Start, Ready, and "Picked up by waiter".
6. **The hall board shows the table number beside the order number.** The proposal says "order numbers only"; the table number was added at the presenter's request (#26). It still shows no names, no prices and no items.
7. **Pictures are drawings, not photographs.** The demo data's meal and raw-material pictures are flat illustrations made for this project (`backend/seed-art`), so the demo is self-contained and licence-free. The upload of real photos works the same way as before.
8. **Raw-material names are not translated.** Meals and categories are translated into all three languages; a raw material has one name, shown as is in the back office.
9. **Fonts are loaded from Google Fonts.** Offline, the browser falls back to its own fonts; the app itself still loads.
10. **The sold-out meal reaches table devices within about 5 s, not instantly:** the menu refreshes on the same 5 s cycle as the boards (the proposal accepts periodic refresh).

## 6. Still to be done by a person

- The human rehearsal of `demo-script.md` on the real devices.
- "Add to Home Screen" on a real phone or tablet over `localhost` (adb reverse) or HTTPS, and a reopen offline.
- A native-speaker pass over the German and Arabic wording, including the Arabic meal content written for this project.
- The manual rows still open in `qa-checklist.md`.
