# Issue #73: Admin: reset the displayed order number from Settings

## What was done

Orders now have two numbers. The **internal** `orderNumber` stays what it was: assigned in the locked transaction, unique for ever (its partial unique index is untouched) and never reset. The new **displayed** number, `displayNumber`, is what guests, the kitchen, the hall board and the admin screens show, and an admin can restart it at 001 from Settings, for example at the start of a day.

- **One locked counter row, two numbers.** `order_number_counter` gained `next_display_value`. `OrderNumberService.assignNextOrderNumber()` (still called only by `OrderStateMachineService`, which `NonNegotiableRulesGuardTest` enforces) hands out both from the same locked row in the same transaction as before, so "unique under concurrent submissions" holds as it did.
- **The reset takes the same row lock** (`OrderNumberResetService`), so it can never interleave with a submission: a submission in flight finishes first (its order is then open and refuses the reset), or the reset finishes first and the submission gets 001.
- **Refused while any order is open.** `POST /api/settings/order-number/reset` answers 409 while an order is submitted, in preparation or ready, so two open orders can never show the same number on the hall board or in the kitchen. Served, cancelled and draft orders do not block it.
- **Audited.** Every reset writes a row to the new `order_number_reset` table: who, when, and the number the next order would have got. A reset when the next number is already 1 changes and records nothing.
- **`GET /api/settings/order-number`** gives the Settings page the next number, how many orders are open and the last reset. Both endpoints are Admin only (in `Documentation/access-control.md` and `AccessControlMatrixTest`).
- **The API field keeps the name `orderNumber`** and now carries the displayed number (guest, kitchen, hall, admin list and history), so the frontend barely changed; the internal number is not exposed. The hall board still orders by the internal number.
- **The history search by number** matches the displayed number; after a reset it can match several orders, newest first, and every card already shows its date and time.
- **Settings page:** a new "Order numbers" card shows the next number (large, mono, left to right also in Arabic), who restarted it last and when, and the button "Restart at 001". The button asks the server how many orders are open at the moment it is pressed, then opens a confirmation (what will happen, who it is recorded for) or, when open orders refuse it, an explanation with the count and no confirm button. The button is disabled while the next number is already 001.
- **Migration `V16`:** existing orders get `display_number = order_number` and the series continues where it was, so a deployed installation shows exactly what it showed before.

Decisions worth knowing:
- **The reset is a Settings endpoint** (`/api/settings/order-number/...`), not an order endpoint, since it is a setting of how the numbers run.
- **The dialog's open-order count comes from the status request,** not from the 409 (the app's error responses carry no message body). A 409 that still arrives (an order placed between the check and the confirmation) re-reads the status and the dialog says so.
- **"Nothing to restart" is not an error:** a reset when the next number is 001 answers 200 and records nothing, and the button is disabled there.
- **Drafts take no number** and do not block a reset.

## The other files

- **Backend:** `V16__order_display_number.sql`; `Order`, `OrderNumberCounter`, new `OrderNumberReset` and `OrderNumberResetRepository`; `OrderNumberService`, `OrderStateMachineService`, new `OrderNumberResetService`, `OrderNumberController`, `OrderNumberStatusResponse`; the guest, kitchen, hall and admin responses and `OrderRepository` (displayed number, search, hall query).
- **Frontend:** `settings/OrderNumberCard.tsx`, `SettingsView.tsx` and `.css`, `api/settingsApi.ts`, `api/types.ts`, `lib/formatOrderNumber.ts` (its wording), and 15 strings in each of `i18n/locales/de.json`, `en.json`, `ar.json`.
- **Docs:** `CLAUDE.md`, `README.md`, `Documentation/Design/README.md`, `database-schema.md` (the Mermaid block and notes; the exported ERD images were not regenerated), `access-control.md`, `qa-checklist.md`.
- **Tests and scripts:** see below; `frontend/scripts/order-number-check.mjs` is new.

## Verification performed

1. **`./mvnw test`: 295 tests, 0 failures** (279 before, 16 new). The new ones: `OrderNumberResetServiceTest` (10: numbering, the reset restarts the displayed number but never the internal one, an old order keeps its number, refused while submitted, in preparation or ready, served and cancelled do not block, drafts take no number, audit row with admin, time and previous number, nothing recorded when there is nothing to restart, the status, the history search finding two orders after a reset), `OrderNumberControllerTest` (4: an admin sees the status and resets, 409 with an open order, kitchen, waiter and cashier get 403 and anonymous 401), `OrderNumberResetConcurrencyTest` (a reset racing 20 simultaneous submissions in five rounds, real separate transactions: no number twice, the displayed series is whole, either it restarts at 1 or carries on, never in the middle), `DisplayNumberMigrationTest` (V15 migrated, orders inserted, V16 applied: every order keeps its number, the counters carry on). `AccessControlMatrixTest` lists both endpoints as Admin only. Existing tests that read the number from the API were moved to the displayed number.
2. **`order-number-check.mjs` in a real browser, German, English and Arabic: 66 of 66 checks** (the card shows the server's next number; the confirmation says what happens; Escape and Cancel change nothing; with an open order the explanation shows the count and offers no confirm button and a restart sent anyway is 409; confirming restarts, the card shows 001 and who did it, the button is disabled; the next order is 001 for the guest, in the kitchen and on the hall board; the history search for 1 finds the old and the new 001; nothing is cut off; the page is right to left in Arabic and the number stays left to right). The screenshots were looked at.
3. **Regression checks against the same throwaway stack:** `qa-walk.mjs` 162 of 162 pages (the Settings page with the new card in 3 languages x 2 themes), `buttons-check.mjs` 564 buttons and 0 problems (544 before; the new card adds the restart button and the dialogs'), `dialogs-check.mjs` 79 of 79, `demo-rehearsal.mjs` 22 of 22.
4. **`npx tsc -b`, `npm run build`, `npm run check:i18n`** (413 keys x 3, 0 errors) clean. ESLint reports the same 5 `setState`-in-effect errors and 10 warnings as before; the new files add none.

Bugs the checks found along the way (root cause, fix):
- **The first concurrency test failed in its own cleanup:** it read a lazy collection outside a transaction; the cleanup now runs in one.
- **My first browser script ran into the sign-in:** it loaded the next page before the sign-in request finished; it now waits for the signed-in screen.
- **The dialog screenshots were taken mid fade-in** and looked washed out; the script waits for the animation, and the checks themselves were unaffected.

Not done, for a decision:
- **An automatic reset at midnight** (the issue's out of scope; it would need a restaurant time zone setting).
- **A person still has to read the new German and Arabic texts** on a real tablet and phone.
- **The exported ERD images** (`database-schema-erd.png` and `.svg`) were not regenerated; the Mermaid block and the notes in `database-schema.md` are current.
- **On the live Render demo** this arrives with the next release tag. The migration runs on its database at the first start and keeps every existing order's number.
