# Issue #22: Guest: order confirmation and live status tracking

## What was done

After #21, the confirmation screen showed only the one-off submission response, so the guest had no way to see the kitchen's progress, and a reload lost the order number entirely. Now:
- A paired device can read the live status of **its own table's** orders through a new endpoint.
- The confirmation screen polls that endpoint every 5 s and draws a four-stage timeline (Submitted, In preparation, Ready, Served) with local times. Polling stops once the order is served or cancelled.
- The order id survives a reload in sessionStorage.

Real-time push stays out of scope, per the proposal. Polling is the accepted substitute.

## The other files

- **`guest/web/GuestOrderStatusResponse.java`**: the status, number, totals, payment method and a nested `HistoryEntry(status, changedAt)` list. `changedBy` is left out on purpose, so staff identities never reach a guest device. The totals are included so the screen can be rebuilt from the order id alone after a reload.
- **`guest/service/GuestOrderService.getStatus`**: `@Transactional(readOnly = true)`, because `Order.history` is lazy and open-in-view is off.
  - An unknown device gets 403.
  - A missing order **and** another table's order get the same 404 "Order not found" (shared `orderNotFound()` helper), so a device can't probe which ids exist elsewhere.
- **`guest/web/GuestOrderController`**: `GET /api/guest/orders/{orderId}` reads the device code from an `X-Device-Code` header rather than a query param, so it stays out of URLs and access logs.
- **`frontend/src/api/types.ts` / `guestApi.ts`**:
  - A new `OrderStatus` union, which also replaces `GuestOrderResponse.status: string`.
  - A new `GuestOrderStatusResponse` type.
  - A new `getOrderStatus()`.
- **`screens/guest/useOrderStatus.ts`**: chained `setTimeout` polling rather than `setInterval`, so a slow server never gets overlapping requests.
  - It stops at SERVED/CANCELLED and on 403/404.
  - It keeps the last good response and sets `connectionLost` while polls fail.
  - A `cancelled` flag and the effect cleanup stop it when the screen unmounts.
  - `setState` is called only in promise callbacks (`react-hooks/set-state-in-effect`).
- **`screens/guest/OrderStatusTimeline.tsx` / `.css`**:
  - Stage labels come from a `Record<OrderStatus, string>`, so a new status can't be forgotten.
  - Times use `Intl.DateTimeFormat` with the shared `LOCALE_BY_LANGUAGE`, now exported from `lib/formatMoney.ts`.
  - The connector line uses `inset-inline-start`, so it mirrors in Arabic.
  - CANCELLED shows a clay notice instead of the timeline.
  - The design's `—` placeholder became `-`, per the no-em-dash rule for UI copy.
- **`screens/guest/OrderConfirmationScreen.tsx`**:
  - Takes `orderId` + `deviceCode` instead of the submission response, and renders everything from the hook.
  - Shows "Connection lost, trying again..." while polls fail.
  - If the *first* poll fails, it shows an error plus "Start a new order" instead of an endless loading screen.
- **`screens/guest/orderStorage.ts` + `GuestScreen.tsx`**:
  - The placed order id is stored in sessionStorage (`rom-guest-order`), and `readInitialStep` returns `'confirmation'` when one is stored.
  - "Start a new order" clears it.
  - Only a positive integer is accepted back from storage.
- **`i18n/locales/{en,de,ar}.json`**: 10 new `guest.status.*` keys.

## Verification performed

1. `./mvnw test`: 116 tests, 0 failures. `GuestOrderControllerTest` has 6 new cases:
   - A fresh order reads SUBMITTED with one history entry and no `changedBy`.
   - After a real `OrderStateMachineService.transition` to PREPARING, it reads PREPARING with two entries in order.
   - Another table's device gets 404; an unpaired device gets 403; an unknown order gets 404; a missing `X-Device-Code` header gets 400.
2. `npx tsc -b`, `npx eslint src/screens/guest src/api src/lib` and `npm run build` all pass.
3. Manual run (dev profile + Vite), advancing orders in psql because there's no kitchen UI until #23:
   - Each stage appeared on the tablet within about 5 s, in local time.
   - Polling stopped after SERVED (Network tab).
   - A cancelled order showed the notice.
   - F5 kept the confirmation screen; a bogus stored id showed the error and the button.
   - DE and AR labels were correct, with the AR timeline mirrored.
   - Stopping the backend showed the reconnecting line and the timeline stayed visible; it recovered once the backend was back.
   - `curl` with a second table's code returned 404.

Issues caught in review before commit:
- **Double retry timer:** a leftover second `setTimeout` in the hook's `.catch` scheduled two retries per failure, so polling would have doubled on every error and ignored the 403/404 stop. Fixed by removing the duplicate line.
- **Poll interval:** `POLL_MS` was `500` (twice a second) instead of `5000`.
- **Test setup:** `unpairedDeviceCannotReadStatus` first failed on its *setup*, because the submit got 403 when no table was paired. Fixed by pairing the ordering table, so only the reading device is unpaired.
- **Endless "Loading…":** the confirmation screen would have shown "Loading…" forever if the first poll failed permanently. This became reachable once the id was restored from storage, and was fixed with the error-plus-button state.

Known gap: the status is polled, not pushed (out of scope this iteration).
