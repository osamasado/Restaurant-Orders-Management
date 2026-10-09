# Issue #79: Restyle the frontend to the new dark, rounded, red-accent design

## What was done

The whole frontend now follows one design system taken from `Documentation/Design/new-style/home.png` and `slidbar.png`: a dark charcoal canvas (and a light theme built from the same tokens), rounded surfaces, a coral accent, one family of soft 3D buttons, and a narrow navigation rail. Nothing about what the screens do changed: no backend, API or routing change, and the order state machine, server-side prices and snapshotted order lines are untouched.

- **Tokens first.** `styles/tokens.css` holds the palette, radii, shadows and type for both themes, so no screen hard-codes a colour. Where a specified colour cannot carry small text at 4.5:1, the token set adds a variant instead of changing the brand colour (a deeper coral for button fills, a text coral, an ink and a text colour per status).
- **One button system** (`styles/buttons.css`): primary (coral), success (green), warning (orange), danger (crimson) and secondary (gray), three sizes, icon buttons, quantity steppers, segmented controls; hover, press, focus, disabled and loading on every one. Every button in the app was moved onto it and the per-screen button rules were deleted.
- **Admin:** a new Dashboard (the landing page: search, a categories slider, a meals slider, order reports, and an order panel for the chosen order), the navigation rail rebuilt to match the reference (a capsule behind small icons, coral current page, round settings and theme controls), the status badges used in the table and as the panel's progress steps, and a small round meal photo beside each order item.
- **Guest:** the ordering screen is the dashboard layout without the rail (search, the same two sliders, a cart panel beside the menu on a tablet or desktop and its own screen on a phone), and the welcome screen is a full-screen charcoal canvas with slowly drifting coral and amber light and three warm-white language buttons.
- **Kitchen, hall, login, dialogs** take the new tokens and buttons. The hall's "ready" label was lightened a little to keep its contrast.

Decisions worth knowing:
- **Sliders are native scroll tracks** with a per-page count computed from their width, so a swipe works as well as "View more"; they hide their buttons when everything fits and work right to left.
- **The Dashboard shows only data the app has.** No delivery address, charge, promotion code, ratings or order-type switch, and no subtotal and tax in the admin panel (the admin order list carries only the total); there is no notification bell, so the rail has settings and theme.
- **An order line stores a meal's name, not the meal,** so the admin panel finds its photo by name; a renamed or deleted meal shows a plate icon. Storing the meal id on the line would fix that and is a backend change.
- **Contrast beat the reference's exact values** in a few places (coral and green fills, orange with dark text, the rail capsule, muted labels); each is noted in `Documentation/Design/README.md`.

## The other files

- **`styles/tokens.css`**, **`styles/buttons.css`**, **`index.css`** (button reset, focus ring, reduced motion), **`index.html`** (Poppins, theme colour), **`public/`** (favicon, app icons, manifest, service worker version).
- **`components/Icon.tsx`** (inline icons, no library), **`Slider.tsx`**, **`MealThumb.tsx`**, **`components/admin/`** (`AdminSidebar`, `BrandMark`, `CategoryChips`, `EmptyState`, `MealCard`, `OrderItem`, `OrderPanel`, `OrderReports`, `SearchBar`, `StatusBadge`).
- **`lib/translations.ts`**, **`useMediaQuery.ts`**, **`orderActionVariant.ts`**; **`screens/admin/views/orders/useOrderActions.ts`** (the order actions shared by the Orders page and the Dashboard).
- **`screens/admin/views/dashboard/`**, **`AdminScreen`**, **`App.tsx`** (the `/admin/dashboard` route is the new admin landing page), the other admin views (buttons and colours only).
- **`screens/guest/`**: `CartPanel`, `MenuScreen`, `GuestScreen`, `WelcomeScreen` and the cart, detail and payment screens. **`screens/kitchen/`**, **`hall/`**, **`home/`**: tokens and buttons.
- **`i18n/locales/*`**: the strings for the Dashboard, the sliders, the rail and the guest menu in German, English and Arabic (398 keys each).
- **`scripts/buttons-check.mjs`** (new), and the walk, dialogs and demo scripts updated for the new markup.

## Verification performed

All against a throwaway stack (own database and ports), never the dev one.

1. **`qa-walk.mjs`: 162 of 162 pages** (3 languages x 2 themes x 27 pages, including the Dashboard, the phone drawer, the order dialog and the welcome screen), retries off.
2. **`buttons-check.mjs`: 544 distinct buttons, 0 problems**, in both themes in English and Arabic: every button has a surface, rounded corners and a height of 32 to 48 px; hover changes how it looks without changing its size; a coloured button sinks when pressed; a keyboard-focused button shows the ring.
3. **`boards-check.mjs`: 322 of 322**, **`dialogs-check.mjs`: 79 of 79**, **`demo-rehearsal.mjs`: 22 of 22** (the order reaches the kitchen in 4 s).
4. **Behaviour checks in a real browser:** Start moves an order to preparing from the panel, Cancel asks first and Keep leaves it, a meal can be marked unavailable and back, search and the category filter narrow the lists, the theme switch works and survives a reload, the phone drawer closes on Escape and returns focus, sliders show the right number per page and move on in both directions (including right to left), the guest cart panel totals come from the server, and the welcome screen's choice turns coral, switches the language and opens the menu.
5. **The welcome screen's text contrast was measured against the moving light** every 6 seconds over a loop, dark and light, phone and desktop: never below 4.7:1.
6. `npx tsc -b`, `npm run build`, `npm run check:i18n` (398 keys x 3, 0 errors): clean. ESLint reports the same 5 `setState`-in-effect errors and 10 warnings as before the ticket.

Bugs the checks found along the way (root cause, fix):
- **Old element-qualified selectors** (`.x__actions button`) outranked the new button classes, so some buttons kept their old look. The old rules were deleted, not overridden.
- **A closed drawer and a sideways-scrolling chip row were reported as overflow;** the audit now skips hidden and intentionally scrolled elements. A real overflow was fixed: the slider header on a German phone now wraps.
- **Letter-spacing split Arabic letters** on the theme toggle; it is removed in right-to-left.
- **With "reduce motion" the old buttons still eased;** transitions are now switched off globally.
- **The hall's "ready" label was 4.3:1;** lightened to pass.
- **The drawer stopped short of the screen bottom on a phone** and **a logo gradient disappeared when its twin was hidden**: an explicit height and a unique gradient id.

Not done, for a decision:
- **A person still has to read the new German and Arabic texts** and try the rail, sliders and welcome screen on real phones and a tablet.
- **The meal id on order lines** (so the admin panel never has to match a photo by name) is a backend change.
- **The five older `setState`-in-effect lint errors** remain, as in earlier tickets.
