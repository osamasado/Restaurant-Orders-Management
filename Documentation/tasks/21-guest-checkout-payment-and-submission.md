# Issue #21: Guest: checkout, payment method choice, order submission

## What was done

Guests can now pick a payment method and place a real order. There was a blocker first: `Order.table` is NOT NULL, but guest devices had no table identity. So the admin-issued pairing code (`Table.pairedDeviceId`, from `TableService.pair`) became the device's credential:
- The device enters the code once and stores it.
- Every submit sends the code, and the server resolves the table itself.

Submission builds a DRAFT order from DB prices and snapshots, then calls the existing `OrderStateMachineService.transition(order, SUBMITTED, null)`. That call assigns the locked order number, applies server pricing and writes the history row, all in the same transaction as the validation. As a result, a rejected cart creates no order and uses up no number. The confirmation screen shows only what the submission returned.

## The other files

- **`guest/service/GuestDeviceService.java` + `GuestDeviceController`**:
  - `POST /api/guest/device/claim`: normalizes the code (trim, upper-case), returns 404 for an unknown code, and refreshes `lastSeenAt` so the admin tables view shows the device online.
  - `requirePairedTable` is the 403 variant used by submission.
  - `TableRepository.findByPairedDeviceId` is new.
- **`guest/service/GuestOrderService.java` + `GuestOrderController`**: `POST /api/guest/orders` (201).
  - The request carries only `deviceCode`, `language`, `paymentMethod` and `sizeId`/`quantity`/`note` per line. It has no prices or table id.
  - The payment method must be enabled in Config (400 if not). Quantity must be 1–20, reusing the quote service's now-public bounds. Notes are limited to 200 chars and trimmed. An unknown size is a 400.
  - Any unavailable meal gives a 409 listing its size ids.
- **`order/model/OrderItem.java`**: the snapshot now falls back from the requested language to EN, then to any language, the same way the guest menu does. Before this, an AR guest ordering a meal with only an EN name would have thrown `IllegalStateException`.
- **`frontend/src/screens/guest/PairingScreen.tsx` + `deviceStorage.ts`**:
  - Pairing is a gate before the welcome screen. The code lives in localStorage, because it belongs to the device rather than to one sit-down.
  - On start, a stored code is re-claimed. A 404 clears it; a network error keeps the device usable.
- **`PaymentScreen.tsx`**:
  - One row per enabled method, in a fixed order, each with a mono tag and a hint.
  - "Total to pay" comes from the quote.
  - The confirm button reads "Select a payment method" until a method is chosen, is disabled while submitting, and reads "Sending order..." during the request.
- **`OrderConfirmationScreen.tsx`**: the big order number and the server's subtotal, VAT and total, plus the payment method and "Start a new order". It is the hand-off point for #22's status timeline.
- **`GuestScreen.tsx`**:
  - Adds the `payment`/`confirmation` steps.
  - The cart is cleared only after the server accepts the order.
  - A 409 goes back to the cart with a notice and a `useCartQuote.refresh()`, since the pricing key doesn't change on its own. The fresh quote flags the affected lines.
  - A 403 forgets the device.
- **`CartScreen.tsx`**: "Choose payment" is enabled only when the quote is current, has no error, and flags no unavailable line.
- **`i18n/locales/*.json`**:
  - Adds `guest.pairing.*`, `guest.payment.*` (method names and hints) and `guest.confirmation.*`.
  - `guest.tableLabel` becomes "Table {{number}}", and the old text moves to `guest.tableUnknown`.

## Verification performed

1. `./mvnw test`: 110 tests, 0 failures or errors.
   - `GuestOrderControllerTest` (7):
     - Happy path: 201, SUBMITTED, number assigned, 21.10/4.01/25.11 from DB prices, snapshotted names and a trimmed note, one history row with a null staff.
     - Consecutive submits get consecutive numbers.
     - An unavailable meal gives 409 with the order count unchanged.
     - A payment method disabled via `SettingsService` gives 400, and the config is restored afterwards.
     - An unpaired device gives 403; an empty cart gives 400; a bad quantity or an unknown size gives 400.
   - `GuestDeviceControllerTest` (4): claim returns the table without the code and sets `lastSeenAt`; case and whitespace are normalized; an unknown or blank code gives 404.
   - `OrderItemSnapshotTest`: a new EN/any-language fallback case.
2. `npm run build` passes, and `eslint src/screens/guest src/api` is clean.

Known gaps, left out on purpose:
- No idempotency key. Double submits are prevented only by disabling the button while in flight.
- The guest can't unpair a device from the device itself.
