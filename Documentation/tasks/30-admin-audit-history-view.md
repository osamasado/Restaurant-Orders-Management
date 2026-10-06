# Issue #30: Admin: audit history view

## What was done

The audit trail was already being written: every status change an order goes through leaves an `order_status_history` row with the status reached, a timestamp and the staff member (or none, for a guest submitting their own order). But nothing could read it. `/admin/history` was the "Coming soon" placeholder and there was no endpoint that listed orders or their history. An admin can now see every order's full history on that screen.

- **Endpoint:** `GET /api/admin/orders/history?page=0&size=20&orderNumber=`, Admin only. It returns orders newest first, each with its table, current status and every status change (stage, time, actor). The actor is the staff member's name, or null for a guest.
- **Screen:** one card per order with its number (001 style), table and current stage, then one line per change. A change with no staff member behind it reads "Guest". An order-number search finds one order; orders load 20 at a time with "Load more".
- **Cancelled orders** also show who acknowledged the cancellation on the kitchen screen, and when. That is the kitchen's read-receipt, not a status change, so it is a separate line under the entries and not mixed into the trail.

Decisions worth knowing:
- **The actor is shown by its current name.** The history stores a link to the staff account, not a copy of the name, so renaming someone renames them in old entries too. Deleting an account that appears in a history is already refused (409), so an entry can never lose its actor. A frozen name and role per entry would need a migration and a backfill, and was left out.
- **Only the actor's name leaves the server.** The response has no staff id, role or PIN data, and a test checks that.
- **Orders are paged in the database, and the history rows of a page are fetched in one query.** Joining the history collection into a paged query would make Hibernate page in memory, so the page of orders (with its table and acknowledger) is read first, then all of their history rows together with the actor joined in.

## The other files

Backend:
- **`order/repository/OrderStatusHistoryRepository.java`**: `findByOrderIdInOrderByChangedAtAscIdAsc`, oldest first (the id breaks ties between two changes in the same instant), with `changedBy` fetched.
- **`order/repository/OrderRepository.java`**: `findAllBy(Pageable)` and `findByOrderNumber(...)`, with the table and acknowledger in the entity graph.
- **`order/service/OrderHistoryService.java`**: read-only (`@Transactional(readOnly = true)`), so it can never alter the trail it reports. Sorts by `placedAt` descending (nulls last), then id; page must be 0 or more and size between 1 and 100, otherwise 400.
- **`order/web/OrderHistoryResponse.java`**, **`OrderHistoryPageResponse.java`**: our own paging envelope (`orders`, `page`, `totalPages`, `totalOrders`), not Spring's `Page`, whose JSON shape is not a stable contract.
- **`order/web/AdminOrderController.java`**: the endpoint, `@PreAuthorize("hasRole('ADMIN')")`.
- **Six controller comments** that still described the old open-by-default security rules (left over from #29) were rewritten.

Frontend:
- **`api/orderHistoryApi.ts`**, **`api/types.ts`**: `getOrderHistory` and the history types.
- **`screens/admin/views/history/HistoryView.tsx` + `.css`**: the screen. A 300 ms delay while typing in the search, and only the newest request may change the screen, so a slow older answer cannot overwrite a newer one. The first page loads at once.
- **`screens/admin/views/history/OrderHistoryCard.tsx` + `.css`**: the card. Actor names use `<bdi>`, the table number inside the translated label is isolated, and stage dots use the same colours as the boards (amber preparing, green ready, clay cancelled).
- **`App.tsx`**: `/admin/history` now renders the view.
- **`i18n/locales/{en,de,ar}.json`**: `admin.history.*`, 303 keys each. The stage names reuse the guest screen's wording, so a status reads the same everywhere.
- **`Documentation/access-control.md`** (the endpoint is in the table, 51 rows) and **`Documentation/Design/README.md`** (the screen as built).

## Verification performed

1. Full backend suite (`./mvnw test`, Testcontainers Postgres): 201 tests, 0 failures (193 before this ticket). `AdminOrderHistoryControllerTest` has 8 tests, which move orders through the real state machine with real staff accounts:
   - every stage in order with the right actors, oldest first, and non-decreasing timestamps;
   - a cancelled order showing who cancelled it and who acknowledged it, and an unacknowledged one showing none;
   - no PIN data, staff role or staff id in the response;
   - order-number search, including an unknown number returning nothing;
   - newest-first paging across pages, and 400 for an invalid page or size;
   - 401 for anonymous and 403 for the kitchen role.
2. The access-control matrix test knows the new endpoint: it fails if the endpoint is missing from its table or carries different roles, and it sends anonymous and wrong-role requests to it.
3. SQL check with logging on: each request makes one history query for the whole page (`order_id in (...)`) with the actor joined, and the orders are paged by the database (`fetch first ? rows`, `offset ? rows fetch first ? rows`).
4. `npm run check:i18n` (303 keys x 3 languages, 0 errors), `npx tsc -b`, `npm run build` and eslint on the new files: clean.
5. A browser check in headless Chromium against a separate throwaway stack (own Postgres container, backend on 18081, Vite on 15175; the dev setup was untouched), seeded through the real APIs with 26 orders (one served by the kitchen, one cancelled and acknowledged, one cancelled and not, one only submitted, and 22 extras): 15 checks, all passing. They covered newest-first with 20 per page, "Load more" bringing the rest and then disappearing, a served order's four stages with actors Guest, M. Behr, M. Behr and M. Behr, the acknowledgement line, search finding one order and an unknown number saying so, and clearing the search. In Arabic the page is right-to-left with Arabic stage names, and there were no missing-translation warnings.

Issue found along the way:
- **Scrambled Arabic timestamps:** I had forced `dir="ltr"` on the `<time>` element. The Arabic date format carries right-to-left marks, so the forced box rendered "0517:16:01 ،2026/10/". The browser checks passed (they read the text), and only the screenshot showed it. Removing the forced direction and isolating the value with `<bdi>` fixed it, confirmed on a new screenshot.
- My first edit to the admin controller silently failed to apply, which the access-control matrix test caught as a missing endpoint. It was reapplied and verified on disk.

Not done, for a decision:
- **No status or date filter.** Only the order-number search, as agreed. Easy to add if the demo needs it.
- **The admin Orders view** (live orders with Start/Ready buttons and Cancel) is still "Coming soon", so cancelling an order is still API-only. That is separate work.
- **The history stays in the database indefinitely.** There is no archiving or retention, which is fine for now.
