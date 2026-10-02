# Issue #25: Kitchen: cancelled-order banner and 'ran out?' availability toggle

## What was done

The kitchen had no way to learn that an order had been cancelled, and no way to tell the guests a meal had run out. Both now work end to end.

- **Cancellation:** an admin can cancel an order (`POST /api/admin/orders/{id}/cancel`), and every kitchen screen shows a banner for it until someone acknowledges it. Acknowledging only records who saw it and when. It never touches `status`, so the state machine stays the only thing that changes an order's status.
- **Availability:** the kitchen footer now has one toggle chip per meal. Toggling uses the same `MealService.setAvailability` as the admin's toggle, so the meal leaves every guest menu on the next load, and the server already refused to accept an order containing an unavailable meal.

The guest side needed no change. `GuestMenuService` already hid unavailable meals, `GuestOrderService.submit` already returned 409 for them, and tests for both already existed.

Two decisions worth knowing:
- The kitchen got its own endpoints (`/api/kitchen/meals`) instead of widening the admin `PATCH /api/meals/{id}/availability` to the KITCHEN role. The kitchen sees only names and availability, not prices, images or edit rights, and the admin API is unchanged for #29's role hardening.
- The design doc says one chip per "main/dessert". Categories have no such type, so the footer lists every meal. It can be narrowed later if the list gets long.

## The other files

Backend:
- **`V15__order_cancellation_acknowledgement.sql`**: adds nullable `cancellation_acknowledged_at` and `cancellation_acknowledged_by` (FK to `staff_account`) to `restaurant_order`.
- **`order/model/Order.java`**: the two matching fields. Both stay null until acknowledged, so the `@ManyToOne` is optional.
- **`order/repository/OrderRepository.java`**: `findByStatusAndPlacedAtIsNotNullAndCancellationAcknowledgedAtIsNullOrderByPlacedAtAsc`. `placedAt IS NOT NULL` leaves out drafts cancelled before the kitchen ever saw them.
- **`order/service/AdminOrderService.java`, `order/web/AdminOrderController.java`, `AdminOrderResponse.java`**: the admin-only cancel endpoint. It goes through `OrderStateMachineService.transition` with the admin as actor. 404 for an unknown order, 409 for an illegal transition (already cancelled, served).
- **`kitchen/service/KitchenOrderService.java`, `kitchen/web/KitchenOrderController.java`, `CancelledOrderResponse.java`**:
  - `GET /api/kitchen/orders/cancelled` lists unacknowledged cancellations, oldest first.
  - `POST /api/kitchen/orders/{id}/acknowledge-cancellation` returns 204. It returns 409 if the order isn't cancelled, and acknowledging twice keeps the first receipt, so two screens pressing at once is harmless.
- **`kitchen/service/KitchenMealService.java`, `kitchen/web/KitchenMealController.java`, `KitchenMealResponse.java`**: `GET /api/kitchen/meals` (ordered by category, then id, so chips don't move between polls) and `PATCH /api/kitchen/meals/{id}/availability`. The response carries every translated name, so the screen picks its own language.

Frontend:
- **`api/kitchenApi.ts`, `api/types.ts`**: the four new calls and the `CancelledOrderResponse` / `KitchenMealResponse` types.
- **`screens/kitchen/useKitchenOrders.ts`**: one 5 s cycle now fetches orders, cancellations and meals together with `Promise.all`, so the three parts of the screen never disagree. If any request fails, the board shows "connection lost" and keeps its last data.
- **`screens/kitchen/CancelledOrderBanner.tsx` + `.css`**: one row per cancelled order (number, "Cancelled by admin · table N · stop work", Acknowledge button) with `role="alert"`. It renders nothing when the list is empty.
- **`screens/kitchen/RanOutFooter.tsx` + `.css`**: the toggle chips. The name follows the screen's language, falling back to English, then to whatever exists. A sold-out chip is filled clay and has `aria-pressed`. Each chip is disabled while its own request is in flight.
- **`screens/kitchen/KitchenScreen.tsx`**: wires both components in, with a busy id per action and the existing notice line for errors.
- **`i18n/locales/{en,de,ar}.json`**: `kitchen.cancelled.*` and `kitchen.availability.*`. Commas and middots only, no em dashes.

## Verification performed

1. `./mvnw test -Dtest='KitchenMealControllerTest,KitchenOrderControllerTest,AdminOrderControllerTest,OrderRepositoryTest'` (Testcontainers Postgres): 26 tests, 0 failures, 0 errors. New tests cover:
   - 401 for anonymous and 403 for waiter/kitchen on every new endpoint;
   - cancel recording the admin in the audit history;
   - 409 for cancelling an already cancelled or served order, and 404 for unknown orders;
   - a cancelled order appearing on the banner list until acknowledged, then recording the kitchen staff member while `status` stays `CANCELLED`;
   - a double acknowledge keeping the first timestamp;
   - the repository query excluding acknowledged cancellations, cancelled drafts and active orders;
   - a kitchen toggle taking a meal off `/api/guest/menu` and back on straight away.
2. `npx tsc -b` and `npm run build`: pass. `npx eslint src/screens/kitchen src/api src/i18n`: clean. The 5 errors a full `eslint src` reports are all in admin views and were there before this change.
3. Manual run (dev profile + Vite), guest in a normal window and kitchen (`M. Behr`) in a private window:
   - A guest order appeared in the kitchen's New column within one poll.
   - After an admin cancel via `curl`, the banner appeared on its own, the order left the board, and the guest's status screen showed it as cancelled.
   - The banner survived a page reload, and Acknowledge removed it for good.
   - Toggling a chip to sold out removed the meal from the guest menu on reload.
   - With a meal already in the cart, toggling it to sold out made Confirm order show "no longer available", and no order was created.
   - Toggling back restored the meal.

Issues caught before the first commit:
- **Uncompilable cancel endpoint:** the first draft of `AdminOrderController` used an undefined `order`, passed a `StaffPrincipal` where `transition` takes a `StaffAccount`, and returned an `Order` as a `KitchenOrderResponse`. Rewritten as `AdminOrderService.cancel` plus a thin controller.
- **Wrong repository query:** the derived query took a `Collection<OrderStatus>` for an equality match (`Status`, not `StatusIn`) and had no ordering. It now takes a single `OrderStatus` and sorts by `placedAt`.
- **Entity mapping vs migration:** the acknowledged-by `@ManyToOne` was `optional = false` while the column is nullable, which would have failed every order insert.

Found but not fixed (out of scope):
- The seeded pairing ids (`tablet-a1` etc.) can't be used on the guest pairing screen, which allows 6 characters and uppercases the input. Pairing from the admin Tables view generates a usable code.
