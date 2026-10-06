# Issue #31: End-to-end testing and bug-fixing pass

## What was done

The pieces of the order lifecycle were each tested on their own, but nothing checked that they hold together, and German had never had a visual pass. This ticket adds the checks that cross the boundaries, guards the rules that only held by convention, and runs a real browser through every screen in every language.

- **One order across every surface.** `OrderLifecycleEndToEndTest` drives a single order through the real HTTP API with real sign-in sessions: the guest sees the menu, gets a quote, pairs a device and submits; the kitchen sees it and advances it; the hall board and the guest's own status follow each step; the admin audit trail records each step with its actor. A second test walks the cancel path (banner until acknowledged, trail with who cancelled and who acknowledged).
- **The rules inside that flow:** a skipped step is refused with 409 and changes nothing; the kitchen cannot cancel by either door; a table cannot read another table's order; a client-sent price or total is ignored; an order keeps its price and item name after the menu is edited; a refused submission consumes no order number (a promise in `GuestOrderService` that was never tested).
- **Concurrency through the real guest path.** `GuestSubmissionConcurrencyTest` fires 30 simultaneous guest submissions at a real HTTP server with 12 refused ones mixed in. All 30 get distinct, consecutive numbers, are priced on the server and saved with their line, and the refused ones use no number up. The older `OrderNumberConcurrencyTest` only exercised the state machine.
- **Rules that held only by convention are now guarded.** `NonNegotiableRulesGuardTest` pins four `CLAUDE.md` rules so breaking one fails the build: only the state machine moves an order or takes an order number, `Order` has no status setter, an order line cannot reference the menu, and a guest request cannot carry a price, total, tax or table.
- **A browser walk and the QA checklist.** `frontend/scripts/qa-walk.mjs` runs a real Chromium over all four screens in German, English and Arabic and both themes, with orders in every state. `Documentation/qa-checklist.md` records, row by row, what was verified by the walk, by an automated test, or still needs a person.

Bugs found and fixed:
- **German admin sidebar overflow.** The language and theme buttons were wider than the sidebar, so "DUNKEL" was cut off. The row now wraps.
- **Seeded pairing codes could not be typed.** The demo tables had device ids like `tablet-a1`, which a guest device (6 characters, upper case) can never enter. They are now six-character codes (`ALPHA2`, `BRAVE3`, `GULF77`, `NAVY99`, `KAYAK2`), and a database seeded earlier is moved over (only an id that is exactly an old seed value, never one an admin generated).

## The other files

Backend tests (`backend/src/test/.../e2e/`): `OrderLifecycleEndToEndTest`, `GuestSubmissionConcurrencyTest`, `NonNegotiableRulesGuardTest`; and two new cases in `DemoDataSeederTest`.

Backend: `seed/DemoDataSeeder.java` has the new pairing codes and `replaceLegacyDeviceIds()`.

Frontend:
- **`scripts/qa-walk.mjs`**: for every page it checks no console error and no unexpected failed request, no missing-translation warning, no horizontal overflow, nothing wider than its container, the right `<html lang>` and `dir`, Latin digits only in Arabic, no clipped text, and that the page is not blank. It saves a screenshot per page. Options (languages, themes, retry) are at the top.
- **`screens/admin/AdminScreen.css`**: the sidebar controls row wraps.
- **`Documentation/qa-checklist.md`**: the checklist, the walk's result, the observations, and a table of where each non-negotiable rule is verified.

## Verification performed

1. Full backend suite (`./mvnw test`, Testcontainers Postgres): 215 tests, 0 failures (201 at the start of this ticket).
2. Every new guard was proved by breaking it and watching its test fail, then restoring the code:
   - the lifecycle test failed 4 of 6 when four rules were broken at once (skipped legality check, the kitchen allowed to cancel, an extra order number burned, the wrong orders on the hall board: `expected:<409> but was:<200>`, `expected: <5> but was: <6>`);
   - the concurrency test failed with a 500 when the order-number row lock was removed, so it really exercises the lock;
   - the rule guards failed on a second caller of `recordTransition`, a second caller of the number generator, and an order line referencing the menu;
   - the pairing tests failed with the old ids put back (`table 1 has an untypeable code: tablet-a1`).
3. **Browser walk: 114 of 114 pages pass** on the production build, on the first attempt, with retries off (3 languages x 2 themes x 19 pages: guest 6, kitchen 3, hall 1, admin 9 including the meal and staff forms). The screenshots were reviewed by eye as well, German for the first time: menu, meal detail with the translated content, cart, payment, confirmation, kitchen board, hall board, history, settings and staff.
4. `npm run check:i18n` (303 keys x 3 languages), `npx tsc -b`, `npm run build` and eslint on the new scripts: clean.

Issues found along the way (about the walk, not the product):
- **A blank page passed every check.** A German settings page that had not rendered yet had nothing to overflow or clip. The walk now waits for the page to settle and fails a blank page.
- **A check against the viewport cannot see a row wider than its box**, which is how the German sidebar bug slipped past. A "wider than its container" check was added.
- **The sign-in throttle tripped the walk.** It reused one made-up name for its wrong-PIN attempt, and the throttle correctly locked it after five failures across runs (HTTP 429). The name is now unique per run.
- **The Vite dev server stalled.** On the dev server a few screens occasionally never rendered within 60 seconds, a different one each run, and the load average was about 9. Most of that load was my own temporary dev server polling files across the Windows drive. The same walk on the production build passed 114/114, so this is a dev-server artifact. The script header and the checklist now advise walking a production build.

Not done, for a decision or a person:
- **Human rows of the checklist** are left unticked: the pairing screen, natural German and Arabic wording, tablet, kitchen-screen and hall-screen legibility on real devices, and the one-order-across-all-screens run by hand once per language.
- **Order items are in the guest's language**, so a German kitchen sees English sizes on an order placed in English. Showing items in the restaurant's language would be a data-model change.
- **The meal form's category list follows the language tab being edited** (it opens on English), by design.
- **"Restaurant Orders Management"** is the same in all three languages (a placeholder app name).
- **The walk does not cover the guest pairing screen** (it presets a paired device), and it is not wired into the build: it needs a running stack.
