# Issue #93: Guest flow: choose eat in or take away after the language, Home links into the admin menu, role-aware logo

## What was done

The guest's start is now **language, then eat in or take away, then (eating in on a device that is not paired yet) pairing, then the menu**. Take-away orders need no table. The four Home buttons moved into the admin menu, `/` opens the guest start, and the logo is a link where it leads somewhere.

**Guest flow**
- **Order type screen** (new, `OrderTypeScreen`): two large choices, "Eat in the restaurant" and "Take away", asked in the language just chosen, on the same canvas and light as the language screen. A phone, a tablet and a desktop all fit; right to left in Arabic.
- **Eat in:** a device that is already paired with a table goes straight to the menu; an unpaired one sees the pairing screen, now **in the chosen language** (it used to come first and was always German), with a Back button. A wrong code is explained, a right one opens the menu with the table. If the stored code is still being re-checked when the guest chooses, the answer decides.
- **Take away:** no pairing, no table; the header reads "Take away" (Zum Mitnehmen, سفري). The confirmation tells the guest to collect at the counter when the number shows as ready.
- **Changing it:** an "Eat in / Take away" switch under the menu header; the cart survives. Switching to eating in on an unpaired device asks for the code, and Back returns to the choice.
- **`/` opens the guest start** (the dev-only Home page is deleted), and the service worker cache version was bumped so installed apps drop the old page.

**Take away on the server**
- **One reserved take-away table** (migration `V17`: `restaurant_table.take_away`, a partial unique index so there is at most one, internal number `__TAKE_AWAY__`). Every take-away order is placed against it, so everything that reads `order.table` keeps working. It is not listed in Tables & devices, and editing, pairing, unpairing or deleting it answers 404; no pairing code can claim it; a real table cannot take its number (409).
- **Placing an order:** `takeAway: true` with no device code places a take-away order; a device code and `takeAway` together is a 400; without `takeAway` the device code is required (403 as before). Prices, totals and the order number are as always computed on the server; the request carries no table id.
- **Every response that shows a table carries a `takeAway` flag** (kitchen, cancelled banner, hall board, admin Orders, history), and the screens show the translated "Take away" label instead of a table number: kitchen, hall board, admin Orders list, the Dashboard and its search, the order panel, the history and the cancelled-order banner.
- **An order secret** (the proposal in the issue). When an order is placed the server returns `accessToken`, a random 256-bit secret; only its SHA-256 is stored. The status call is `X-Order-Token`. Without it a take-away guest could read every other take-away order's status and total by counting ids. A wrong secret and another table's order are the same 404; sending nothing is 403. A dine-in order can also still be followed with its table's device code (so a guest screen opened before the update keeps working), and orders placed before the migration have no secret and keep using the device code. A take-away order has no device, so only its secret opens it.

**Kitchen** (your request during this issue): a take-away card is clearly marked. Beside the order number a **"where" chip**, styled like the preparation-time chip: neutral "TABLE 7" for a table order, **solid coral with a bag and "TAKE AWAY"** for a take-away order; the card also gets a coral edge (on the start side, so it mirrors in Arabic) and a faint coral wash, so a cook sees at a glance that it is packed to go.

**Admin menu and logo**
- **Guest, Kitchen and Hall** are three small round icons under a "Screens" caption at the bottom of the rail and in the phone drawer, each opening in a new tab (`noopener`), named and translated ("... (opens in a new tab)"). The rail still fits without scrolling.
- **The logo** in the admin rail and the phone top bar is a link to the Dashboard (named "Go to the dashboard"), with its own focus ring. On the guest welcome screen it is a plain image: there is nowhere else to go. The kitchen and the hall have no logo today, so nothing was added there.

Decisions worth knowing (the issue's open questions, answered with its proposals):
- **Take-away payment** offers the same methods as eating in (the admin chooses them in Settings).
- **A paired tablet also shows the order type screen**; its "Eat in" goes straight to the menu, and a guest can still choose "Take away" on it.
- **Label:** "Zum Mitnehmen" / "Take away" / "سفري".
- **The secret for every guest order,** but a dine-in order may still use the device code (compatibility, see above).
- **The "change" control is a switch in its own row,** not a button in the header: a first version in the header pushed the language chip off the screen of a 390 px phone. The header also now lets its controls drop under a long title ("Zum Mitnehmen") instead of off the screen.

## The other files

- **Backend:** `V17__take_away.sql`; `Table`, `TableRepository`, `TableService`, `GuestDeviceService`; `Order.accessTokenHash`; `OrderAccessTokens` (new), `GuestOrderService`, `GuestOrderController`, `GuestOrderRequest`, `GuestOrderResponse`; `takeAway` on the kitchen, cancelled, hall, admin-list and history responses and the hall query.
- **Frontend:** `screens/guest/` (`GuestScreen`, `OrderTypeScreen`, `WelcomeBackdrop`, `PairingScreen`, `WelcomeScreen`, `OrderConfirmationScreen`, `orderStorage`, `useOrderStatus`), `lib/tableLabel.ts`, `components/Icon.tsx` (bag, external), `components/admin/AdminSidebar`, the table label in the kitchen card, hall board, Orders, history, Dashboard, order panel and banner, `App.tsx`; 16 new strings in each of the three language files and the removed `home.*` ones.
- **Scripts:** `guest-flow-check.mjs` (new); `qa-walk.mjs`, `buttons-check.mjs`, `demo-rehearsal.mjs`, `boards-check.mjs` follow the new flow and the renamed chip.
- **Docs:** `CLAUDE.md`, `README.md`, `Documentation/Design/README.md`, `access-control.md`, `database-schema.md`, `demo-script.md`, `qa-checklist.md`.

## Verification performed

1. **`./mvnw test`: 312 tests, 0 failures** (295 before, 17 new): `GuestTakeAwayOrderTest` (9: a take-away order needs no device code and is priced by the server; a device code with it is a 400; no device code without `takeAway` is a 403; the secret opens its order and nothing else does, 403 / 404; a real table's device code cannot read a take-away order; a dine-in order follows by secret or device code; a pre-migration order follows by device code only; the secret is stored only as a hash and never shown again; the kitchen, admin list and history carry the flag), `TakeAwayTableTest` (7: exactly one take-away table, a second is impossible, not listed, cannot be edited, paired, unpaired or deleted, a real table cannot take its number, no code claims it, the hall board marks it), `TakeAwayMigrationTest` (V17 on an installation with tables and orders: untouched, the take-away table appears once, old orders have no secret). Four existing tests moved to the new contract (the extra `takeAway` field, a status read with no proof is 403 instead of 400), and the demo seeder test counts real tables only.
2. **A new browser check in German, English and Arabic: `guest-flow-check.mjs`, 102 of 102.** `/` opens the guest start; the order type screen is asked in the chosen language and fits a phone; eat in on an unpaired device shows the translated pairing screen, explains a wrong code, Back returns, the right code opens the menu with the table; a paired device skips pairing; take away needs no pairing, the cart survives switching and back, the order is placed and the guest follows it by their secret; no proof 403, a wrong secret 404, a table's device code 404, their own 200; the label appears in the kitchen (a coral chip with a bag beside the number, a coral edge on the card), on the hall board, in the admin Orders list and the history; the admin menu opens Guest, Kitchen and Hall in new tabs, the logo goes to the Dashboard, and the phone drawer has them too. The screenshots were looked at (light and dark, German and Arabic).
3. **On a fresh stack, one after another:** `boards-check.mjs` 322 of 322 (also after the kitchen chip change), `qa-walk.mjs` **186 of 186 pages** (3 languages x 2 themes x 31, up from 27: the order type screen, the translated pairing screen, the take-away menu and the take-away confirmation are new), `buttons-check.mjs` 611 buttons and 0 problems, `dialogs-check.mjs` 79 of 79, `order-number-check.mjs` 66 of 66, `demo-rehearsal.mjs` 22 of 22 (twice).
4. **`npx tsc -b`, `npm run build`, `npm run check:i18n`** (424 keys x 3, 0 errors, 0 warnings): clean. ESLint reports the same 5 errors and 10 warnings as before; the new code adds none.

Not re-run after the last styling change (the kitchen chip), because it touches only the kitchen card: the walk, buttons, dialogs, order-number and rehearsal runs above. `guest-flow-check.mjs` and `boards-check.mjs`, which look at the kitchen, were.

Bugs the checks found along the way (root cause, fix):
- **The header "change" button pushed the language chip off a phone's screen** (the title column grew to fit it); replaced by a switch under the header, and the header lets its controls wrap under a long title.
- **A browser check missed capital letters:** kitchen, history and Orders show these labels in capitals by CSS, so the script now compares case-insensitively.
- **The boards check and the rehearsal are not independent:** the boards check leaves about 70 open orders, and a rehearsal after it (order 065) could not find its card on the crowded kitchen screen; run on a fresh stack they pass. They, and the walk (which re-pairs table 7), must not run at the same time as another script on the same stack; run one at a time on a fresh stack.
- **The demo seeder test counted every table,** including the reserved one; it now counts the real ones.

Not done, for a decision:
- **A distinct take-away look on the hall board** (it shows the label, not a colour): the proposal only asked for the kitchen.
- **A logo on the kitchen screen** (out of scope; the kitchen has none today).
- **A person still has to read the new German and Arabic texts** and try the flow on a real phone and tablet, including the pairing screen with a real printed code.
- **On the live Render demo** this arrives with the next release tag: `V17` runs at the first start, keeps every table and order, and orders placed before it keep following by the device code.
