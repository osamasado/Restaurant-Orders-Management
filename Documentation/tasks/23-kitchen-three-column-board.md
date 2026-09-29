# Issue #23: Kitchen: 3-column board (new / preparing / ready)

## What was done

The kitchen wall monitor was an empty placeholder, open to anyone. Orders could only be moved forward by hand in psql. Now `/kitchen` requires a KITCHEN or ADMIN sign-in and shows every active order in three columns (New = SUBMITTED, In preparation = PREPARING, Ready = READY). It refreshes every 5 s.

Each card has at most one button: **Start**, **Ready** or **Picked up by waiter**. The **server** decides which, via a `nextStatus` field derived from the state machine's own transition graph, so the UI can never offer an illegal step. Every press goes through `OrderStateMachineService` and records the signed-in staff member in the order's history.

"Picked up by waiter" (READY → SERVED) was included although the issue names only Start and Ready. The design shows it, and without it READY orders would pile up, since there's no admin Orders view yet. The timer chips and clock (#24), the cancelled banner, and the "Ran out?" toggles (#25) are left out.

## The other files

- **`order/service/OrderTransitions.legalTargets(from)`**: exposes the legal-transition graph as a set, so the kitchen's next step is derived from the same single source as `isLegal`.
- **`order/repository/OrderRepository.findByStatusInOrderByPlacedAtAsc`**: returns active orders oldest first. `@EntityGraph(items, table)` loads each card's data in one query, avoiding N+1 lazy loads.
- **`kitchen/web/KitchenOrderResponse`**: the card DTO, with no prices (the kitchen doesn't need them). `nextStatus` is `legalTargets(status)` minus CANCELLED, so exactly one value, or `null` once SERVED.
- **`kitchen/service/KitchenOrderService`**:
  - `listActive()` is `readOnly`.
  - `advance()` checks, in order: a missing status gives 400; CANCELLED gives 403 (cancel is admin-only); an unknown order gives 404; `IllegalOrderTransitionException` gives **409** (e.g. another screen already pressed Start).
  - The actor comes from `staffAccountRepository.getReferenceById(...)`, a fresh reference rather than the detached session entity.
- **`kitchen/web/KitchenOrderController`**: `GET /api/kitchen/orders` and `POST /api/kitchen/orders/{id}/transition` with the body `{status}`.
  - `@PreAuthorize("hasAnyRole('KITCHEN','ADMIN')")` is on each method, because `SecurityConfig`'s URL rules end in `anyRequest().permitAll()`.
  - The actor comes from `@AuthenticationPrincipal StaffPrincipal`, never from the request.
  - The client sends the target it saw rather than a bare "next", so a stale press becomes a 409 instead of silently skipping a step.
- **`frontend/src/api/kitchenApi.ts` + `types.ts`**: `getKitchenOrders()`, `advanceKitchenOrder()` and `KitchenOrderResponse`.
- **`screens/kitchen/KitchenScreen.tsx`**:
  - An `AuthProvider` guard in the same style as `AdminScreen`, but the denied state also has a sign-out button, so a waiter signed in on the kitchen tablet isn't stuck.
  - The board lives in a separate `KitchenBoard` component, rendered only after the guard, so `useKitchenOrders` never runs after an early return (Rules of Hooks) and never polls before sign-in.
  - A per-card `busyOrderId` blocks double taps.
  - Every press ends with `refresh()`, so cards move columns immediately.
  - A 409 shows an "already changed on another screen" notice.
- **`screens/kitchen/useKitchenOrders.ts`**: 5 s chained-`setTimeout` polling that never stops on its own.
  - `refresh()` bumps a counter in the effect's dependencies, which restarts the cycle with an immediate poll.
  - A 401 stops polling and sets `sessionExpired`, and the board then signs out, so a backend restart brings back the login form instead of an endless "connection lost".
- **`screens/kitchen/KitchenOrderCard.tsx` / `.css`**: a pure display component.
  - The button is chosen from `nextStatus` through a `Partial<Record<OrderStatus, …>>`.
  - Notes show as amber strips.
  - The design sizes are used (number 27px, item 14px/600, size 11.5px). Pixel polish is left for the later styling pass.
- **`i18n/locales/{en,de,ar}.json`**: 12 new `kitchen.*` keys. German uses the formal "Ihr", matching the other staff texts.

## Verification performed

1. `./mvnw test`: 125 tests, 0 failures.
   - `KitchenOrderControllerTest` (7):
     - Anonymous gets 401; WAITER gets 403.
     - The board lists only active orders with the right `nextStatus`. An order walked PREPARING → READY → SERVED through the real endpoint drops off.
     - Start returns PREPARING/READY, and the history's last entry has `changedBy` = the signed-in KITCHEN account. The test uses `user(new StaffPrincipal(realAccount))`, because a plain `user()` has no StaffPrincipal.
     - Skipping a step gives 409; CANCELLED gives 403; an unknown order gives 404.
   - `OrderTransitionsTest` has 2 new `legalTargets` cases.
2. `npx tsc -b`, eslint on `src/screens/kitchen src/api`, and `npm run build` all pass.
3. Manual run (dev profile + Vite), signed in as the seeded M. Behr:
   - A guest order appeared under New within about 5 s, with its note.
   - Start, Ready and Picked up moved the card immediately and then removed it.
   - The guest timeline (#22) followed every step. This was the first full order lifecycle without psql.
   - psql showed SUBMITTED with no staff member (the guest), then PREPARING/READY/SERVED by M. Behr.
   - Two tabs pressing Start showed the conflict notice in the second.
   - L. Adler (WAITER) was denied and could sign out; O. Sado (ADMIN) saw the board.
   - `curl` without a session got 401.
   - A backend restart brought back the login form.
   - DE and AR texts were correct, with the AR board mirrored.

Issues caught in review before commit:
- **Read-only write method:** `advance()` briefly had `@Transactional(readOnly = true)`. Postgres would reject its UPDATE/INSERT ("cannot execute … in a read-only transaction"), and the joined state-machine call inherits the flag, so every button would have returned 500. Fixed by making it a plain `@Transactional`; only `listActive()` is read-only.
- **Wrong status code:** a missing `status` was mapped to 403 instead of 400. It's a malformed request, not a permissions problem.
- **Missing URL slash:** the test helper built `/api/kitchen/orders42/transition`, so 4 tests failed with 404, and `unknownOrderIsNotFound` passed for the wrong reason. Fixed with `"/api/kitchen/orders/" + orderId`.
- **Empty record:** `KitchenTransitionRequest` was first declared with an empty component list (`record KitchenTransitionRequest()`), so it had no `status()` accessor. An unrelated `org.antlr…Transition` auto-import was also removed from the controller.
