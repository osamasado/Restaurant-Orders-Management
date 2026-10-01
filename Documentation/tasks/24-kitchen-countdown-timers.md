# Issue #24: Kitchen: countdown timers with amber/red thresholds

## What was done

Kitchen cards showed no waiting time, so staff couldn't tell which orders were running late. Each card now has a timer chip that counts up (`m:ss`) from `placedAt`. It is neutral under 6 minutes, amber from 6 and clay from 12, per the design doc.

No backend change was needed. `KitchenOrderResponse` already carried `placedAt`, and `OrderStateMachineService.transition` sets it on the SUBMITTED transition, so it is the submission time. Because the timer counts from submission rather than from the current column, it keeps running when an order moves to In preparation or Ready.

The header clock was a hard-coded `--:--` placeholder from #23. It now shows the live time, using the same once-per-second tick.

## The other files

- **`frontend/src/screens/kitchen/useNow.ts`**: returns `Date.now()` and refreshes it every 1 s. `KitchenBoard` calls it once and passes `now` to every card, so all chips change colour together instead of 15 separate intervals drifting apart. It is kept separate from the 5 s `useKitchenOrders` poll: re-rendering doesn't trigger a network call.
- **`frontend/src/lib/orderTimer.ts`**: pure helpers for the timer chip.
  - `elapsedMs` clamps at 0, so a device clock a few seconds behind the server can't show a negative time.
  - `timerLevel` returns a `TimerLevel` union (`'neutral' | 'warning' | 'late'`), using `>=` on the `WARNING_MS` / `LATE_MS` constants.
  - `formatElapsed` shows `m:ss`, and minutes keep counting past 60 (`61:20`). It uses `Math.floor`, so `6:00` appears at the same moment the chip turns amber.
- **`screens/kitchen/KitchenOrderCard.tsx`**: new `now` prop. The card renders `<time dateTime={placedAt}>` with a `--{level}` modifier class and no logic of its own.
- **`screens/kitchen/KitchenOrderCard.css`**:
  - `margin-inline-start: auto` pushes the chip to the end of the header row: the right in DE/EN, the left in AR, with no RTL-specific CSS.
  - `tabular-nums` stops the chip changing width every second.
  - The tints use `color-mix()` on `--accent-amber` / `--accent-clay`, so they follow the light/dark tokens instead of a fixed rgba.
- **`screens/kitchen/KitchenScreen.tsx`**: the header clock uses `Intl.DateTimeFormat` with `LOCALE_BY_LANGUAGE[language]` and `hourCycle: 'h23'`. The design shows 24-hour `10:19`, and `en-US` would otherwise give `10:19 AM`. In AR (`ar-EG`) it uses Arabic-Indic digits, matching the guest timeline. No new i18n keys.

## Verification performed

1. `npx eslint src/screens/kitchen src/lib`: clean. The 15 lint problems from a full `npm run lint` are all in other files and were there before this change.
2. `npm run build`: passes.
3. `npx tsc -b` on the intermediate commit (timer chip without the clock): passes, so each commit builds on its own.

Issue caught in review before commit:
- **Overwritten CSS rules:** while adding the timer rules, `.kitchen-order-card__action--ready` and `--served` were overwritten, leaving a dangling `.kitchen-order-card__action--read` selector.
  - The result was still valid CSS, read as a descendant selector `…--read .kitchen-order-card__timer`, so the build passed.
  - In practice, the Ready and Picked-up buttons lost their styles, and the timer's base rule (position, mono font, padding) never matched.
  - Fixed by restoring both button rules and appending the timer rules after them.
