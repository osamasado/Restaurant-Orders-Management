# Issue #71: Admin: live orders view with status actions and cancel

## What was done

`/admin/orders` was the "Coming soon" placeholder, so an admin could not see the live orders or move or cancel one without calling the API by hand. It is now the live Orders view from the design: one row per order, newest first, with the number (001 style), an item summary, table, time, payment method, total, a status pill, and one button per legal next step plus Cancel. A served or cancelled order says "No further steps". The list refreshes every 5 s and straight after every action.

- **List endpoint:** `GET /api/admin/orders?page=0&size=20`, Admin only. Placed orders newest first, each with its items, table, placed time, payment method, total and `nextStatuses`, which the server computes from `OrderTransitions` (every legal step except cancelling). The screen never decides what is legal.
- **Transition endpoint:** `POST /api/admin/orders/{id}/transition` with `{status}`, Admin only. It goes through `OrderStateMachineService` with the admin as the actor, so the audit history shows them. Cancel keeps its own endpoint.
- **Screen:** Start / Ready / Served buttons from `nextStatuses`, and Cancel, which first asks "Cancel order 014?" inside the row ("Yes, cancel it" / "Keep it"). A 409 (another screen moved the order first) shows a short notice and refreshes. A failed refresh keeps the rows on screen and says the connection was lost, then recovers by itself.

Decisions worth knowing:
- **Admin only.** The checklist and `access-control.md` record that waiter and cashier do not get this view, and that among staff only Admin sees payment details. They can be given a screen later, together with its endpoints and matrix rows.
- **Drafts are not listed** (`placedAt IS NOT NULL`): nobody has submitted them, and Start would refuse them anyway.
- **The transition endpoint accepts only Start, Ready and Served.** `CANCELLED` is refused with 403 (use the cancel endpoint) and so is `SUBMITTED`/`DRAFT`: submitting is the guest's own step, which prices the order and takes its number. An illegal step from the current status is a 409.
- **"Load more" asks for a longer first page** (20, 40, ... up to the server's limit of 100) instead of fetching further pages. Every poll then returns one consistent list, so an order that moves cannot show twice or vanish between two pages. Older orders are in the audit history, and the screen says so at the limit.
- **Orders are paged in the database and the items of a page are read in one query,** the same pattern as the audit history.

## The other files

Backend:
- **`order/service/AdminOrderListService.java`**: read-only (`@Transactional(readOnly = true)`); page must be 0 or more and size 1 to 100, otherwise 400.
- **`order/service/AdminOrderService.java`**: `advance(...)` next to the existing `cancel(...)`.
- **`order/repository/OrderItemRepository.java`**: `findByOrderIdInOrderByIdAsc`, the lines of a whole page in one query.
- **`order/repository/OrderRepository.java`**: `findByPlacedAtIsNotNull(Pageable)` with the table in the entity graph.
- **`order/web/AdminOrderRowResponse.java`, `AdminOrderPageResponse.java`, `AdminTransitionRequest.java`**: the response rows, our own paging envelope (not Spring's `Page`), and the request. A row carries nothing beyond what the screen shows: no staff names, ids or PIN data.
- **`order/web/AdminOrderController.java`**: the two endpoints, both `@PreAuthorize("hasRole('ADMIN')")`.

Frontend:
- **`api/adminOrdersApi.ts`, `api/types.ts`**: the list, transition and cancel calls and their types.
- **`screens/admin/views/orders/useAdminOrders.ts`**: the 5 s polling hook; a failed poll keeps the last list.
- **`screens/admin/views/orders/OrdersView.tsx`, `OrderRow.tsx` + `.css`**: the screen and the row. Item names, sizes and the table number are isolated (`<bdi>`/`isolate`) so they stay intact inside Arabic text. The currency comes from the settings.
- **`App.tsx`**: the route now renders the view. The unused `ComingSoonView` component, its styles and the `admin.comingSoon` string are removed.
- **`i18n/locales/{en,de,ar}.json`**: `admin.orders.*`, 320 keys each. Status names reuse `admin.history.stages`, payment names reuse `admin.settings.paymentMethodNames`.
- **`scripts/qa-walk.mjs`**: the Orders page now waits for real rows before it is audited, and a new page walks the cancel confirmation (pressing "Keep it", so the seeded orders stay as they are).
- **`Documentation/access-control.md`** (the two endpoints, 53 rows, and the payment-details rule), **`Documentation/Design/README.md`** (the screen as built) and **`Documentation/qa-checklist.md`**.

## Verification performed

1. Full backend suite (`./mvnw test`, Testcontainers Postgres): **233 tests, 0 failures** (216 before this ticket).
   - `AdminOrderListControllerTest` (8): 401 anonymous and 403 for kitchen, waiter and cashier; newest first with items, payment and total; `nextStatuses` per status (empty for served and cancelled, never `CANCELLED`); drafts and never-submitted cancelled drafts not listed; the row has exactly the expected fields and no staff name or PIN data; paging and the envelope; 400 for a bad page or size; and a statement count with Hibernate statistics on (at most 3 for a page of 6 orders with 3 lines each).
   - `AdminOrderControllerTest` (15, nine new for the transition): the same roles refused; Start, Ready and Served each succeed and record the admin as the actor; skipping a step, repeating one, and moving a finished order are all 409; `CANCELLED`, `SUBMITTED` and `DRAFT` are 403 and leave the order untouched (a draft keeps no number); a missing or unknown status is 400; an unknown order is 404.
2. **Each guard was broken on purpose and its test failed**, then restored: the `CANCELLED` filter on `nextStatuses`, the placed-orders filter, the one-query items load (an N+1 version made 14 statements instead of 3), the refusal of `CANCELLED`, the refusal of submitting, recording the actor, and the 409 mapping. Nine tests failed in that run.
3. `AccessControlMatrixTest` (the new endpoints are in its table; it checks every endpoint's annotation, plus anonymous and wrong-role requests): green.
4. `npm run check:i18n` (320 keys x 3 languages, 0 errors), `npx tsc -b`, eslint on the new files and `npm run build`: clean.
5. **Browser walk** (`qa-walk.mjs`, production build, own throwaway Postgres, backend and preview; retries off): **120 of 120 pages pass** in German, English and Arabic, light and dark, including the new Orders page and the cancel confirmation (no console error, no unexpected failed request, no missing translation, no overflow or clipped text, right `lang` and `dir`, Latin digits in Arabic). The screenshots were looked at: English, German (dark) and Arabic, light and dark, including a zoom on the Arabic confirmation sentence, where the question mark ends the sentence on the left of the isolated number as it should.
6. **Functional browser check** against the same throwaway stack (English; the logic does not depend on the language), 22 of 22 checks: first page 20 rows and "Load more" reaching 40; a new order appearing within one refresh at the top; items, table, payment and total in the row; Start moving the order, the hall board listing it, and the audit history naming the admin (`[SUBMITTED, null]`, `[PREPARING, O. Sado]`); a race with the kitchen ending in a legal state; a forced 409 (the list frozen, the kitchen moves the order, the stale Start pressed) showing the "already changed" notice and then refreshing; a blocked refresh showing "connection lost" while keeping the rows and recovering by itself; Cancel asking first, "Keep it" leaving the order alone, confirming cancelling it, "No further steps" afterwards, the order on the kitchen's cancelled list and gone from the hall board.

Issues found along the way:
- **A mutation that did not compile:** my first "unfiltered list" mutation added a duplicate `findAllBy`, which broke the build and hid the real test results. Redone with `findAll`, it was caught by the right test.
- **Two flaws in my own check scripts:** a typo in the browser path, and navigating before the sign-in had finished. Neither touched the product.
- **A pattern that missed the table number:** the table number is wrapped in invisible bidi marks, so a plain-text match failed on a row that looks right on screen. Fixed in the check, not the page.

Not done, for a decision:
- **The item line in Arabic** reads "quantity, then name" left to right when the item name is Arabic, and in the opposite order for a Latin name, because the quantity and name share one isolated run. Item names stay in the language the guest ordered in (the snapshot rule), so this mixed case only shows when an Arabic order is read in an Arabic admin. Left for the visual design pass (#62).
- **The kitchen transition endpoint does not refuse `SUBMITTED`** the way the new admin one does. It is theoretical today, because the guest path never saves a draft before submitting, so no draft exists to submit. Easy to add the same refusal if wanted.
- **Waiter and cashier have no screen yet.** That is the decision recorded above, not a gap.
