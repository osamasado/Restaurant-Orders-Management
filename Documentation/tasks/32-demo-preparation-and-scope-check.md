# Issue #32: Demo preparation and final check against the proposal's scope

Also closes #28 (PWA installability) and #74 (Orders filter by status and date), which the scope check found to be missing from what the proposal calls built.

## What was done

The proposal's scope table lists "installable on phones and tablets" and, in the administrator journey, "filter by date and status" as built, and the demo shows menu photos. None of those was true yet: there was no manifest or service worker, the Orders list had no filter, and the demo data had no pictures, no raw materials and no recipes. All three are now built, the demo has a script that was rehearsed end to end by machine, and the build is checked item by item against the proposal in `Documentation/scope-check.md`, with every gap and deviation stated.

- **Demo data:** the dev seeder now also creates 31 raw materials (unit, stock, supplier), a recipe for every size of every meal (quantities scaled per size), and attaches a picture to each of the 8 meals and 31 raw materials. All of it is driven by one catalog (`seed-images/catalog.json`) and is idempotent: nothing is duplicated, a picture or recipe an admin changed is left alone.
- **Pictures:** 39 flat SVG illustrations in one style, drawn for this project (`backend/seed-art`, with a style guide and a renderer), rendered to PNG and committed as seed assets. They are drawings, not photographs.
- **Installable app (#28):** web app manifest (starts at `/guest`, standalone, shortcuts to kitchen, hall and admin), app icon and PWA icons, and a service worker that keeps the app shell available on a flaky or missing connection and never touches `/api`. The default Vite favicon was replaced by the app icon.
- **Orders filter (#74):** `GET /api/admin/orders` takes optional `status`, `from` (inclusive) and `to` (exclusive); the Orders view has a status dropdown, a date picker and "Clear filters". The browser turns the day into local midnight to local midnight, so the server needs no time zone.
- **Menu refresh (found by the scope check):** the guest menu was fetched once, so a meal marked sold out stayed on a device that was already showing the menu. The open menu now refreshes every 5 s.
- **Demo script and rehearsal:** `Documentation/demo-script.md` (preflight and clean-data reset, desk layout, the steps with talking points tied to proposal section 6, what to do if something goes wrong) and `frontend/scripts/demo-rehearsal.mjs`, which runs the flow with three screens side by side, times every hand-off and saves a side-by-side screenshot per step.

Decisions worth knowing:
- **Seed recipes only for sizes with no recipe yet, and images only for meals and raw materials without one.** A recipe or picture an admin changed is never replaced. An image an admin removed comes back on the next dev start.
- **Units are grams, millilitres, pieces and bunches.** Recipe quantities and stock have two decimals, so 5 ml of oil or 1 g of nutmeg only fit in the small unit.
- **The filter is built on a JPA Specification, not a query with `:param is null`.** PostgreSQL cannot work out the type of a null parameter in that form and the request failed; a filter that is not set now adds no condition at all.
- **The service worker registers 3 s after load.** A page left sooner never starts the registration, which removed an "interrupted fetch of the worker script" console error the QA walk caught.
- **`npm run preview` now proxies `/api` and `/images`,** so the production build (the only place the app is installable) can be tried against a local backend.

## The other files

Backend:
- **`seed/SeedCatalog.java`**: reads `seed-images/catalog.json` (raw materials, recipes with size factors, image names by slug).
- **`seed/DemoDataSeeder.java`**: `seedRawMaterials`, `seedRecipes`, `seedImages`, copying the PNGs into the upload folder like `ImageStorageService` stores them.
- **`order/repository/OrderSpecifications.java`**, **`OrderRepository.java`**, **`order/service/AdminOrderListService.java`**, **`order/web/AdminOrderController.java`**: the filter (400 when `from` is not before `to`, 400 for an unknown status).
- **`resources/seed-images/`**: the catalog and the 39 PNGs. **`backend/seed-art/`**: the SVG sources, `STYLE.md` and `render.mjs`.

Frontend:
- **`public/manifest.webmanifest`, `public/sw.js`, `public/icons/`, `public/favicon.svg`, `index.html`, `src/main.tsx`, `scripts/render-icons.mjs`**: the installable app.
- **`screens/admin/views/orders/useAdminOrders.ts`, `OrdersView.tsx` + `.css`, `api/adminOrdersApi.ts`**: the filter; a list fetched for another filter is never shown. **`i18n/locales/*.json`**: 325 keys each.
- **`screens/guest/MenuScreen.tsx`**: the 5 s menu refresh.
- **`scripts/qa-walk.mjs`**: now fails on a picture that did not load. **`scripts/demo-rehearsal.mjs`**: the rehearsal. **`vite.config.ts`**: preview proxy.

Documentation: `demo-script.md`, `scope-check.md`, `qa-checklist.md` (new rows and sections), `Design/README.md` (filter, pictures, icon).

## Verification performed

1. Full backend suite (`./mvnw test`, Testcontainers Postgres): **244 tests, 0 failures** (233 before).
   - `AdminOrderListControllerTest` (14, six new): filter by status, an empty result and an unknown status, the time range with from included and to excluded, a range that ends before it starts, and status plus range together with the count following the filter.
   - `DemoDataSeederTest` (10, five new): the catalog is consistent (every recipe ingredient exists, every raw material is used, every meal has a recipe, every picture exists as a PNG of the right size under 5 MB); raw materials are seeded with unit, stock and supplier and a second seed adds nothing; every meal size has a recipe and a bigger size needs more of the same raw materials; every meal and raw material gets a valid PNG in the upload folder; an image or a recipe an admin changed (and an ingredient they removed) is left alone.
2. **Each guard was broken on purpose and its test failed**, then restored: the picture attach condition, the "size already has a recipe" check and the "raw material already exists" check (six tests failed, the recipe one with a unique-key violation), and a deleted picture (needed a clean copy of the build resources, because Maven had kept a stale one). For the filter, the first query version failed on PostgreSQL (see below).
3. `npm run check:i18n` (325 keys x 3, 0 errors), `npx tsc -b` and `npm run build`: clean. eslint on the files of this ticket is clean; a full `eslint .` still reports 5 `setState`-in-effect errors in five older admin views (materials, meals, settings, staff, tables) that this ticket did not touch.
4. **Browser walk** (`qa-walk.mjs`, production build, own throwaway Postgres, backend and preview, retries off): **120 of 120 pages** in German, English and Arabic, both themes, now including the picture check. I looked at the guest menu, the Arabic meal detail and the raw-material list with the pictures in place.
5. **Orders filter, browser check** (13 of 13): status filter equals what the server counts; Cancelled shows only "No further steps" rows; today's date keeps today's orders, another day shows "No orders match these filters"; status and day combine and survive a refresh; "Clear filters" resets; and in `Pacific/Kiritimati` (UTC+14) and `Pacific/Pago_Pago` (UTC-11), where "today" is a different date, each finds the orders on its own today and none on its tomorrow.
6. **PWA browser check** (16 of 16): the service worker takes control; the manifest has no errors, starts at `/guest`, is standalone and has 192, 512 and maskable icons; the icons are served; the shell, the build's scripts and the manifest are cached and no API response is; with the connection switched off `/guest`, `/kitchen` and `/hall` still open; back online it loads normally. I broke the built manifest (no icons) and the service worker (cache the API) on purpose: two checks failed, then the build was restored.
7. **Demo rehearsal** (`demo-rehearsal.mjs`, clean data): **22 of 22**, hand-offs 3.6 to 6.8 s. For the sold-out meal, a second phone sits on the open menu: the meal vanished in 5.4 s. With the refresh slowed to once an hour the same check failed, so the check does test the fix.

Issues found along the way:
- **Open menus never refreshed (fixed above):** found by the scope check against the proposal's "disappears from every table at once".
- **The filter query failed on PostgreSQL** ("could not determine data type of parameter $3", ten test errors) because of `:param is null or ...` with a null timestamp. Replaced by a Specification.
- **A rehearsal against a database full of earlier test orders** buried the demo order under 18 "New" cards, which is why the script documents a clean-data reset.
- **My own render script produced blank images** (Chromium blocks `file://` images inside `setContent`); a drawing agent found it, fixed by opening the SVG itself.
- **Mistakes in my check scripts only** (a wrong browser path twice, a case-sensitive "Table 7" against capitals set by styling, navigating before sign-in finished); none touched the product.
- **Chrome's installability report is weak in headless mode:** it did not flag a manifest without icons, so the explicit manifest checks carry that proof.

Not done, for a decision:
- **A real device test of "Add to Home Screen" and of the human demo rehearsal** are left to the presenter (unticked in `demo-script.md` and `qa-checklist.md`).
- **Waiter and cashier have no screen,** no way to move an order backwards, and a served order cannot be cancelled. All three are listed with their reasons in `scope-check.md` section 5 instead of being built.
- **The pictures are drawings.** A visual polish pass (and real photos) belongs to #62.
- **`CLAUDE.md` still says "Green-field: no source code exists yet"** under "Current state", which is long out of date. I left the project instructions alone; it is worth a one-line fix by you.
