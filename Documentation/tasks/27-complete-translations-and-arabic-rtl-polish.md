# Issue #27: Complete DE/EN/AR translations and real Arabic RTL polish

## What was done

The three locale files already had the same keys, so the work was finding what that check can't see: content that was never translated, text that renders in the wrong order in Arabic, and screens that only look right in English. An audit, then screenshots of every screen in Arabic (dark and light), turned up the following, now fixed.

- **Menu content was names only in German and Arabic.** The demo seed gave DE and AR a name per meal and nothing else, so guests saw no description, preparation method or ingredients. All 8 meals now have full content in all three languages. An already-seeded database is topped up on the next dev start, filling only empty translations and never touching what an admin wrote.
- **Arabic size labels used Latin units** (`200 g`, `0.3 l`), which also rendered reversed. They are now `200 غرام` and `0.3 لتر`.
- **Data text came out reversed inside Arabic lines.** `300 g` rendered as `g 300`, and the tables row ("TERRACE · 4 seats · code") mixed its parts. Sizes, notes, item names, room names and device codes are now isolated with `<bdi>`, and values inside translated sentences use a Unicode isolate.
- **Arabic uses Latin digits** (`25.11`, `10:41:08`, `001`), as decided for this ticket. Previously prices and times used Arabic-Indic digits while order numbers, timers and quantities stayed Latin. It is one change to the Arabic locale string, since everything else was already Latin.
- **Back and forward arrows were hard-coded** (`←`, `→`) and pointed the wrong way in Arabic. A `DirectionalArrow` component now picks the glyph from the reading direction.
- **Smaller gaps:** the modal's "Close" label was hard-coded English, the staff "last seen" date used the browser's locale, the admin sidebar showed the raw `ADMIN` role, three unused keys were removed, and the missing `USER` role label was added.
- **A guard so it stays true:** `npm run check:i18n` fails if the locale files have different keys or placeholders, or the code uses a key that does not exist.

Visual defects found by the screenshots and fixed along the way: the cancelled-order banner (from #25) was unreadable in light theme because of fixed dark-theme colours, and the admin sign-out button and modal close button were invisible in dark theme (no background reset).

## The other files

Backend:
- **`seed/DemoDataSeeder.java`**: the menu is now a list of `MealSeed` records (`MealText` per language), used both for first-time seeding and for `fillMissingMealContent()`, the top-up. `seedAll()` is `@Transactional`, so a failed seed rolls back whole, and an immutable `List.of()` is copied before it replaces a managed collection (Hibernate `clear()`s it on merge).

Frontend:
- **`lib/formatMoney.ts`**: `ar` is `ar-EG-u-nu-latn`, the single change for Latin digits.
- **`lib/bidi.ts`** (`isolate()`) and `<bdi>` in `KitchenOrderCard`, `MealDetailScreen`, `MealsView`, `TablesView`: data text keeps its own order inside Arabic lines. `CartScreen` isolates only the size, not the price, so prices keep the symbol on the same side as everywhere else.
- **`components/DirectionalArrow.tsx`**: used on the cart, payment and meal detail screens. The old `[dir='rtl'] .cart-screen__back span { transform: scaleX(-1) }` rule was removed, because it would have flipped the arrow back.
- **`components/Modal.tsx`/`.css`**, **`StaffView.tsx`**, **`AdminScreen.tsx`/`.css`**, **`CancelledOrderBanner.css`**: the smaller fixes above.
- **`scripts/check-i18n.mjs`** and the `check:i18n` npm script: no dependencies. It checks key sets, placeholders, empty values and the keys used in code, and warns about possibly unused keys.
- **`i18n/locales/{en,de,ar}.json`**: `common.close` and `admin.staff.roles.USER` added, three unused keys removed (283 keys each).

## Verification performed

1. `npm run check:i18n`: 283 keys x 3 languages, 255 keys used in code, 0 errors, 0 warnings. A deliberate break (a renamed key and a changed placeholder) made it exit 1 with all three problems listed, and the file was restored.
2. `npx tsc -b`, `npm run build`, and eslint on every touched folder: clean. The remaining lint errors are the existing `setState`-in-effect ones in the admin views.
3. Seeder tests (`DemoDataSeederTest`, 3 tests): every meal has full content in all three languages, Arabic size labels differ from English, and the top-up fills emptied translations and untranslated size labels while leaving an admin-edited description and size label alone. Putting the immutable list back made the top-up test fail with `UnsupportedOperationException`, which confirmed the copy is needed.
4. A real top-up: a backend started on a database seeded with the old code logged "Filled 4 empty DE/AR meal translations and size labels" (the Schnitzel and Radler Arabic sizes).
5. Visual review in headless Chromium against a separate throwaway stack (own Postgres container, backend on 18081, Vite on 15175, so the dev setup was untouched), seeded through the real APIs with orders in every state:
   - Arabic, dark and light: hall, kitchen (with banner and footer chips), the whole guest flow (welcome, menu, detail, cart, payment, confirmation) and all seven admin views plus the meal edit modal: 17 screens in each theme, plus zoomed close-ups of individual rows.
   - Zero `[i18n] Missing translation` console warnings on any of those screens, in either theme.
   - Re-checked after each fix: kitchen sizes, cart line, admin meals and tables rows, sidebar role, arrow glyphs (back `→` and forward `←` in Arabic, `←` and `→` in English, no CSS transform) and the light banner.
6. Full backend suite (`./mvnw test`, Testcontainers Postgres) on the final branch: 151 tests, 0 failures, 0 errors.

Issues found along the way:
- **Own regression:** `DirectionalArrow` plus the existing CSS flip double-flipped the cart and payment back arrows. Found in the first screenshots, fixed by removing the CSS rule.
- **Over-isolating:** isolating the price in the cart line forced it left-to-right and put the symbol on the wrong side. Only the size is isolated now.
- **Stale dev server:** a Vite instance on the `/mnt/d` drive kept serving old modules after edits (no file-watch events), so early screenshots used old code. The review server was restarted with polling. A dev server on the same drive may need a restart to pick up edits.

Not done, for a decision:
- **Order items are snapshotted in the guest's language**, so a kitchen reading Arabic or German sees items in whatever language the guest ordered in (English in the review data). Showing them in the restaurant's default language would be a data-model change.
- **"Restaurant Orders Management"** is the same in all three languages (welcome, hall, home). It is a placeholder app name.
- **Arabic text quality** has not been reviewed by a native speaker, including the new meal content.
- **Other unstyled buttons** in dark theme (for example the remove-size button in the meal form) still show the browser's default light background. That belongs to #62.
