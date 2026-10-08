# Issue #76: Replace the browser's alert, confirm and prompt in the admin with proper dialogs

## What was done

Eighteen native `window.confirm`, `alert` and `prompt` calls remained in four admin views (staff, tables, meals and categories, raw materials): five generic delete confirmations ("Delete this table?"), the PIN reset (an unlabeled prompt followed by up to three alerts), the pairing code in an alert, and a few error alerts. They looked like nothing else in the product, could not name what they acted on, could not be read right to left reliably, and a failed delete needed a second alert to say why. They are all gone, replaced by the app's own dialogs, and ESLint's `no-alert` rule now keeps it that way.

- **The shared `Modal`** got what a dialog has to do: focus moves in when it opens (the first field, or the safe button for a question), Tab and Shift+Tab stay inside, Escape and a click outside close it, the page behind does not scroll, and focus returns to the opener (to the page content when the opener is gone, as after a delete). It has a small size and a 0.15 s fade that is switched off by "reduce motion". The five existing form dialogs get all of this too.
- **`ConfirmDialog`** (five deletes): names the item ("Delete table 7?"), says in a sentence what happens, verb-first buttons ("Cancel" / "Delete table"), Cancel has focus. The delete runs inside the dialog; if it fails, the reason ("it may still have orders", "it may still be used in a recipe") appears in the dialog and the button becomes "Try again".
- **`ResetPinDialog`**: visible label and rule ("4 to 8 digits"), numeric keypad, "Show PIN", the error under the field when you leave it and gone the moment it is fixed, a server error kept in the dialog; success closes it and a toast says "PIN updated for M. Behr".
- **`PairingCodeDialog`**: the code in 40 px mono, always left to right, a Copy button that says "Copied", and Done.
- **Toasts** for the small messages (a failed pairing or availability change, the PIN update): announced to screen readers (errors at once, successes politely), dismissible, gone after about 4 s (success) or 8 s (error).
- **Strings** in German, English and Arabic (346 keys each). Names inside sentences are isolated so they stay intact in right-to-left, and the obsolete strings were removed. The staff delete error now says why ("it may appear in the history of an order").

Decisions worth knowing:
- **No typing the name to confirm a delete.** The dialog names the item and defaults to the safe button, which is enough for an admin back office.
- **The action runs inside the dialog** (the dialog receives the promise), so a failure shows right there instead of closing the dialog and raising a second one. While it runs nothing can dismiss it.
- **Nothing about what the actions do changed.** No API or backend change.

## The other files

- **`components/Modal.tsx` + `.css`**, **`Dialog.css`** (message, error line, buttons), **`ConfirmDialog.tsx`**, **`Toast.tsx` + `.css`**, **`toast-context.ts`**.
- **`screens/admin/views/staff/ResetPinDialog.tsx` + `.css`**, **`tables/PairingCodeDialog.tsx` + `.css`**.
- **`StaffView`, `TablesView`, `MealsView`, `MaterialsView`**: the native calls replaced; **`AdminScreen.tsx`**: the toast provider, and the page content can take focus so it can receive it back after a delete.
- **`eslint.config.js`**: `no-alert: error`.
- **`scripts/dialogs-check.mjs`**: the browser check. **`scripts/qa-walk.mjs`**: three dialog pages per language and theme.
- **`Documentation/Design/README.md`** ("Dialogs and notices") and **`Documentation/qa-checklist.md`**.

## Verification performed

1. **`dialogs-check.mjs`, on the final build: 79 of 79.** It drives every flow in a real browser, with a throwaway stack: no native dialog appears (any one that does fails the run); each delete dialog names the item, starts on Cancel, locks the page, keeps Tab and Shift+Tab inside, closes on Escape and on a click outside and gives focus back, and confirming deletes (with the opener gone, focus lands on the page content); the failures (a staff account and a table in an order's history, a category with meals, a raw material in a recipe) show their reason inside the dialog and delete nothing; the PIN dialog (label and hint, focus in the field, no scolding while typing, the error on leaving the field and gone as soon as it is fixed, an empty submit returns focus to the field, "Show PIN", a forced server error stays in the dialog, and afterwards the new PIN logs in while the old one gets 401); the pairing dialog shows the code the server issued, large, monospace and left to right, Copy really fills the clipboard and "Copied" goes away, and a failed pairing is a toast; toasts are announced with the right role, dismiss, and disappear by themselves; "reduce motion" means no animation on dialogs or toasts; and German and Arabic, light and dark: the dialog inside the window, nothing cut off, the right direction, the code left to right, and text contrast of ten kinds of text at 4.5:1.
2. **Each guard was broken on purpose and the check failed**, then restored: no focus trap, no page lock, no focus return, the failure message not shown (the run stops at that step), focus on the dangerous button, the PIN error not clearing, Copy doing nothing, toasts never going away (the run stops there), and reduced motion ignored. The last one first passed wrongly (the check looked 300 ms after opening, when the 150 ms fade was already over); it now reads the animation setting directly, plus a positive check that the fade exists by default.
3. **Browser walk** (`qa-walk.mjs`, production build, throwaway stack, retries off): **138 of 138 pages**, including three new pages per language and theme (a delete confirmation, the PIN dialog, the pairing dialog). I looked at the dialogs in English, German (dark) and Arabic (light and dark).
4. `npx tsc -b`, `npm run build`, `npm run check:i18n` (346 keys x 3, 0 errors): clean. ESLint: no `no-alert` violation anywhere; a full `eslint .` still reports the 5 older `setState`-in-effect errors in the five admin views, which were there before and are unchanged by this ticket.

Issues found along the way:
- **My first lint run flagged the Modal** for updating a ref while rendering; the update now happens in an effect.
- **The check found nothing wrong in the product on its first full run,** so each guard was proven by breaking it instead (point 2). Two of my own script mistakes were fixed on the way (a table-row locator that could never match, and the reduced-motion timing above).

Not done, for a decision:
- **A person still has to read the new German and Arabic texts,** listen to a screen reader announce the dialogs, errors and toasts, and try the PIN field's numeric keypad on a real tablet (marked Human in the checklist).
- **The guest, kitchen and hall screens had no native dialogs,** so nothing changed there. The Orders view already used an in-row confirmation (#71).
- **The five older `setState`-in-effect lint errors** (a separate cleanup, listed in the earlier task summaries).
