# Issue #20: Guest: cart and running total

## What was done

Before this, meals added from the detail screen went into a hidden `useRef` that nothing displayed. Now the guest has a real cart: a cart screen where lines can be edited or removed, a subtotal/VAT/total panel, and a "Review order" bar on the menu. The totals are not calculated in the browser. A new public quote endpoint prices the cart with the same `OrderPricingCalculator` that order submission uses, looking prices up in the DB by size id. That way the cart total can't drift from checkout, JS float rounding can't disagree with BigDecimal HALF_UP, and a tax-rate change in Settings applies right away. Line totals (`unitPrice × qty`) are still calculated on the client, for display only.

## The other files

- **`guest/web/GuestCartQuoteController.java`**: `POST /api/guest/cart/quote`. It is public, so `SecurityConfig`'s `anyRequest().permitAll()` already covers it.
- **`guest/service/GuestCartQuoteService.java`**:
  - Validates the request: `sizeId` is required, quantity must be 1–20, and an unknown size returns 400.
  - Builds transient (never persisted) `OrderItem`s to feed the calculator.
  - Reads the tax rate through `SettingsService`.
- **`guest/web/CartQuoteRequest.java` / `CartQuoteResponse.java`**: the request carries only `sizeId` + `quantity`, never a price. The response returns subtotal, taxRate, taxAmount, total, and per-line `available`, so the cart can flag a meal the kitchen marked unavailable after it was added.
- **`frontend/src/screens/guest/cartTypes.ts`**:
  - Adds `sizeId`.
  - `id` becomes a per-line key, since the same meal+size can be two lines with different notes.
  - Holds the shared `MIN_QUANTITY`/`MAX_QUANTITY` bounds.
- **`useCartQuote.ts`**: a debounced (250 ms) hook keyed on `sizeId×quantity`.
  - Stale responses are dropped.
  - `loading` is derived from state rather than set inside the effect, which keeps `react-hooks/set-state-in-effect` happy.
- **`CartScreen.tsx` / `.css`**:
  - Line cards with a quantity stepper, Remove, and a note strip.
  - A totals panel that shows only the server quote.
  - The "calculated and verified on the server" note and an "Add more items" button.
  - A "Choose payment" bar, disabled until #21.
- **`GuestScreen.tsx`**:
  - The cart becomes state and a `'cart'` step is added.
  - Adding the same meal+size+note merges quantities (capped at 20).
  - Removing the last line returns to the menu.
  - The menu shows the review-order bar (item count + quoted total) when the cart is non-empty.
- **`i18n/locales/{en,de,ar}.json`**: new `guest.cart.*` strings, without em dashes.

## Verification performed

1. `./mvnw test`: 98 tests, 0 failures/errors. The 5 new `GuestCartQuoteControllerTest` cases cover:
   - The exact rounding case 2 × 8.40 + 4.30 = 21.10, tax 4.009 → 4.01, total 25.11.
   - An unavailable meal flagged `available: false`.
   - An empty cart returning zeros.
   - An unknown size id returning 400.
   - Quantities 0 and 21 returning 400.
2. `npm run build` (`tsc -b` + Vite) passes, and `eslint` on `src/screens/guest` and `src/api` is clean. The 5 `set-state-in-effect` errors that `npm run lint` still reports in the admin views were already on master.

Issues hit along the way:
- The first version of `useCartQuote` reset state synchronously inside its effect, which tripped `react-hooks/set-state-in-effect`. Fixed by storing the result with the `pricingKey` it belongs to and deriving `loading` by comparing keys.
- The first test runs failed with "Could not find a valid Docker environment" because Docker Desktop wasn't running. The full suite passed once it was started.

Known gaps, left out on purpose: line names and sizes stay in the language they were added in, and the cart doesn't survive a page reload.
