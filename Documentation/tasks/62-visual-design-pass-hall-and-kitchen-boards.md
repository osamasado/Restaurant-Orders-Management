# Issue #62: Visual design pass: hall board and kitchen board polish

## What was done

#26 and #23 shipped the boards with only the sizing and colours they needed to work. Measured against `Documentation/Design/README.md` and the reference captures, the audit found: the hall board's two panels looked identical (no tint, no forest "Ready" panel, no ring), there was no blinking dot and no arrival animation, and a busy evening ran off the screen (with 25 preparing and 9 ready orders the hall page was **1734 px tall in a 1080 px window**, and the kitchen page **4429 px**, so on a wall display the header, the controls and many numbers were out of reach). The kitchen board had its column headers outside the cards, pale-green and pale-amber buttons instead of the design's solid forest and amber, an outlined Acknowledge button, a sold-out chip that was white text on salmon (about 2:1), and amber text that was nearly unreadable on paper in the light theme.

Both boards are now built from the design, on exactly one screen, in light and dark, and in Arabic mirrored.

**Hall board**
- Panels as designed: In preparation is a faint tint of its own amber, Ready is the forest panel with its soft ring, in both themes. Labels and numbers use the design's on-dark tints (`#F3C77A`, `#A8DCC0`).
- The In preparation dot blinks (2 s, opacity 1 to .25): the only continuous motion. A number that arrives, including one that moves from In preparation to Ready, fades and rises in over 0.4 s; the numbers already there do not move. With "reduce motion" switched on there is no animation at all.
- **A busy board stays on one screen.** The numbers flow top to bottom and then into further columns, and when they still do not fit they shrink in steps. A quiet board keeps the designed 74 and 86 px; in the tested busy evenings nothing went below 28 px.
- On paper the amber is the design's darker text amber (`#7A5200`), because the on-dark amber is about 2.5:1 there.

**Kitchen board**
- Each column is a card with its header (dot, label, count) inside and a list that scrolls by itself, so the header, the cancelled banner and the "Ran out?" footer always stay on screen. The banner scrolls inside itself above about a quarter of the height.
- Solid forest Start and solid amber Ready (both themes), timer chips solid amber from 6 minutes and solid clay from 12, a solid clay Acknowledge button, a larger banner number, and a sold-out chip as a clay tint with a clay ring.
- Title and station line on one baseline, as in the capture. In Arabic the columns are mirrored and the quantity stays "2×".

Decisions worth knowing:
- **The hall capture and the written spec disagree on the Ready numbers** (cream in `hall.png`, green in the README). The board follows the spec (`#A8DCC0` on the forest panel). The issue's acceptance line about matching the capture is met apart from that, and the table number #26 added.
- **No new colours or fonts.** Every colour is one the design doc lists (forest, the on-dark tints, amber and its text amber, clay, ink and paper, the ring `rgba(125,190,155,.35)`).
- **The ui-ux-pro-max skill was used for targeted guidance** (reduced motion, one or two animated elements per view, 4.5:1 contrast, overflow), not its design-system generator: that proposes a palette and fonts, which the issue forbids. The skill also advises against infinite animations; the blinking dot is the design's own requirement, so it stays as the single continuous animation and stops under "reduce motion".
- **Fitting is done by trying sizes,** not by guessing a size from the number of orders: a hook picks the largest step at which everything fits, and refits when the count, the window, the language or the web fonts change.

## The other files

- **`screens/hall/useFitScale.ts`**: the fit hook. **`HallScreen.tsx`**: a `HallPanel` component. **`HallScreen.css`**: rewritten from the design.
- **`screens/kitchen/KitchenScreen.css`, `KitchenOrderCard.css`, `CancelledOrderBanner.css`, `RanOutFooter.css`**: the kitchen board; two one-line markup changes (a wrapper class for the title, `dir="ltr"` on the quantity).
- **`frontend/scripts/boards-check.mjs`**: the check described below, kept for later changes.
- **`Documentation/Design/README.md`** ("as built" for both boards) and **`Documentation/qa-checklist.md`** (new rows and a note on the script).

## Verification performed

1. **`boards-check.mjs`, a fresh stack, the full matrix: 322 of 322.** Hall and kitchen, a typical and a busy evening (25 preparing, 9 ready, 6 new, 3 unacknowledged cancellations), 1920 x 1080 and 1366 x 768 (the busy hall also 1100 x 760), German, English and Arabic, light and dark. It checks: the page does not scroll and nothing runs off the screen; every number is on the screen and inside its panel; no number below 28 px and the designed 74 and 86 px on a quiet board; every table label on one line; the header, board and every "Ran out?" chip on screen; busy columns scroll by themselves, stay at least 200 px tall, and the last card and its Start button can be scrolled into view; text contrast of about 25 pairs (4.5:1, 3:1 for large text); Arabic mirrored; and, live with a new order, that the arriving number animates for 0.4 s, the others do not move, only the dot is left running, and with "reduce motion" there is no animation at all.
2. **Each guard was broken on purpose and the check failed:** no fitting (every busy overflow and hidden-number check failed), the kitchen page allowed to grow (overflow, off-screen chips, columns no longer scrolling), the light-theme amber text removed (8 contrast failures), the arrival animation and the dot removed (3 motion failures). The "reduce motion" rule was then removed on its own: both its checks failed (1 and 38 animations still running).
3. **Browser walk** (`qa-walk.mjs`, production build, throwaway stack): **120 of 120 pages**. **Demo rehearsal:** 22 of 22, hand-offs 4.0 to 6.6 s. `npx tsc -b`, eslint on the changed folders, `npm run build` and `npm run check:i18n`: clean.
4. I looked at the screenshots of both boards in every combination I had doubts about: quiet and busy, German and English, Arabic mirrored, light and dark, and at the small sizes.

Issues found along the way (the check found most of them):
- **Light-theme contrast:** the sold-out chip text was 4.0:1 and the neutral timer 4.4:1, and the amber note text, amber order numbers and the In preparation label were about 2.5:1 on paper. Fixed with the design's text amber and ink text.
- **A busy hall dropped to the smallest size once:** the fit ran before the web font had loaded. The hook now refits when the fonts are ready.
- **"Table 11" wrapped onto two lines** in a narrower window; the column width now allows for the label, which stays on one line (and a 1100 px window is part of the check).
- **Three cancellations squeezed the columns away** at 1366 x 768 until the banner got its own limited height.
- **A rerun on the same database is not a quiet board:** the script says to run it once per fresh stack, with `SEED=0` to look again at the same state.

Not done, for a decision:
- **Legibility from the back of a real dining room, on the real screen,** is still a human check (the sizes and contrast are verified, the distance is not).
- **The native-speaker review of the Arabic** wording is still open; this pass changed layout, not text.
- **At 25 or more open orders a small screen shows the numbers small** (about 28 to 40 px at 1366 x 768): everything fits, but those are not the 74 px of a quiet evening. A second display or paging would be a different ticket.
