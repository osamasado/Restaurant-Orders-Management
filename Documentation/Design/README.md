# Handoff: Restaurant Orders System

## Overview
A table-side ordering system for a restaurant. Guests order from a device at their table; the kitchen and the dining hall see every order live; a management backend defines the menu and controls every order's status. Four screens, one shared order state.

Source of truth for requirements: `Documentation/Final Project Proposal - Restaurant Orders System.pdf` (included).

## About the design files
The HTML files in this bundle are **design references**, not production code. `Restaurant Orders System.dc.html` is an interactive prototype: all four screens in one page, sharing in-memory state so the flow can be demonstrated end to end. Recreate these designs in the target codebase using its own framework and patterns (React/TypeScript/Java Spring Boot/Lombok + server rendering — whatever the project uses). If no codebase exists yet, pick a stack suited to the requirements below (the proposal implies a server-authoritative web app installable as a PWA) and implement there.

Do not port the prototype's state handling. In the prototype everything lives in one client-side component; in production the order state, order-number generation, price calculation and audit log must all live on the server.

## Fidelity
**High fidelity.** Colors, typography, spacing and copy are final and should be matched. Dish photography is not included — the prototype uses empty photo slots; the real app needs an image upload per meal. The prototype's colours, fonts and radii are superseded by the restyle in issue #79 (see Design tokens); its layouts, copy and behaviour still hold.

## Design tokens

**Restyled in issue #79.** The look is now dark, rounded and coral-accented (reference: `Documentation/Design/new-style/home.png`), in a dark and a light theme that are one design system: same layout, spacing, type and shapes, only surfaces, ink and borders change. The tokens live in `frontend/src/styles/tokens.css` and nothing in a screen hard-codes a colour. The screen descriptions further down still use the old colour names; read them as: *forest* = the coral accent (primary actions, the active item), forest "ready" = success green, *clay* = danger red, *amber* = warning amber.

Colors (dark / light)
| Token | Dark | Light |
|---|---|---|
| `--bg` page | `#1D1D1D` | `#F6F6F3` |
| `--bg-sidebar` / `--bg-secondary` | `#242424` | `#FFFFFF` / `#F0F0ED` |
| `--bg-card` | `#303030` | `#FFFFFF` |
| `--bg-card-alt` (raised) | `#3A3A3A` | `#FAFAF8` |
| `--bg-panel` (order panel) | `#4A4A4A` | `#FFFFFF` |
| `--hairline` (border) | `#454545` | `#E3E3DE` |
| `--text` | `#FFFFFF` | `#1D1D1D` |
| `--text-secondary` | `#B5B5B5` | `#606060` |
| `--text-muted` | `#9A9A9A` | `#707070` |
| `--accent` (indicators, icons, rings) | `#FF5B55` | `#F45B55` |
| `--accent-hover` | `#FF6B65` | `#E94E48` |
| `--success` / `--warning` / `--danger` | `#4CCB62` / `#F2B84B` / `#E95B5B` | `#36B653` / `#D99A22` / `#CF3E3E` |

Where a specified colour cannot carry small text at WCAG AA (4.5:1), the token set adds a variant instead of changing the brand colour: `--accent-fill` (`#D93A2F`) is the coral for filled buttons with white text; `--accent-text` is the coral for small coral text; each status colour has an `-ink` (text on its fill) and a `-text` (coloured text on a surface or a tint). `--text-muted` is lightened a little from the first palette for the same reason, and the order panel lifts its secondary text (`--panel-text-*`) because it sits on a lighter grey. Tints (`--fill-*`, `--tint-*`) are mixed from the text and status colours, so they follow the theme.

Buttons (one system, soft 3D)
- Every button is one of five variants of the same component (`styles/buttons.css`, tokens `--btn-*`): **primary** (coral: Add, Save, Confirm, Start preparing), **success** (green: Ready, Served, Complete, an enabled switch), **warning** (orange: Unpair, Mark unavailable), **danger** (deep red: Delete, Cancel order, Remove, a sold-out meal) and **secondary** (neutral gray: Edit, View, Back, Cancel). Sizes: large 46px (the one big action, kitchen and order panel buttons), normal 40px, small 34px (rows, cards, tables); radius 12px, 10px for small, pills only for chips and tabs; icon + text with a 7px gap; icon-only buttons are 38px squares or circles with an `aria-label`.
- Every button has a surface, never a bare outline. Raised look: a thin highlight along the top edge, a hard edge underneath and a soft shadow. Hover lightens the surface and lifts it 1px; pressing sinks it (2px for the coloured ones, 1px for gray); focus shows the coral ring; disabled keeps its colour at half strength, flat; loading (`aria-busy`) keeps the size and shows a spinner in place of the label. Hover and press only change colour, shadow and position, never the size. Nothing eases with "reduce motion".
- Order actions: starting preparation is primary; ready and served are success; cancelling is danger; the kitchen's "Picked up by waiter" is secondary. Quantity - and + are small raised squares (the + in coral); segmented controls (the symbol position, the language buttons) raise and colour the chosen option; the rail's round controls are raised circles.
- Contrast: white on the coral, green and red fills and dark ink on the orange keep 4.5:1 text, even when hovered, so the fills are a little deeper than the reference's lighter ones (white 14px on `#F45B55` reads at 3.2:1, on orange `#D99A22` at 2.4:1). The coral leans a little toward orange and the danger red toward crimson so the two never look alike. `--btn-primary-*` and the other variants change in one place.
- The few controls that are deliberately not raised buttons are listed in `scripts/buttons-check.mjs` with their reasons (the rail's page links, the order number that selects a row, category chips, a meal card's hit area, the welcome and choice rows).

Typography
- Everything: **Poppins** 400/500/600/700; titles are 600 (`--weight-display`). Numbers, order numbers and small data lines: **IBM Plex Mono**.
- Sizes: page title 26px, section title 18px, card title 16px, body 13-14px, small 11-12px, order total 24px bold. Guest, kitchen and hall keep their own scales (the hall board's numbers are still 74px / 86px).
- Minimum touch target 44px (buttons, drawer links, the menu button).

Radii
- 10px small controls and chips, 14px images, 16px cards, 20px large panels, fully round pills and avatars; no element is rounder than its role needs.

Spacing
- 4px base step (4, 8, 10, 14, 16, 20, 26, 32). Admin content padding 26px / 32px; 16px on a phone.

Shadows and motion
- Cards: a soft shadow with a 1px edge; hover lifts a meal card 3px with a coral ring. Dark mode uses lighter surfaces and borders for depth rather than heavy shadows.
- 180ms ease-out for hover, selection and the drawer; with "reduce motion" the transitions are switched off.

Icons
- A small set of inline SVG line icons (`components/Icon.tsx`), coloured by the text they sit in; there is no icon library.

## Screens / views

### 1. Guest ordering — tablet at the table, or the guest's own phone
**Layout (issue #79).** The ordering screen is the admin Dashboard without the navigation rail: a header with the table ("Table 7"), a one-line prompt and the language and theme controls; a search field; a *Categories* slider of round pictures; a *Meals* slider of meal cards (photo, name, description, "from" price and a raised coral "Add" pill; the whole card opens the meal); and on a tablet or desktop (1100px and wider) the cart as a panel beside the menu, the same receipt-style panel as the admin's order panel, with the lines (a small round meal photo beside each name), 3D quantity controls, the server's subtotal, VAT and total, and "Choose payment". On a phone the cart is its own screen with the same panel in it, and a raised "Review order" button floats above the menu once something is in the cart. The guest opens in the dark theme like the admin. The seven states below keep their order and behaviour; read their colours and shapes through the Design tokens.

Device frame 432px wide (phone variant 372px), screen 716px tall, min 560px. Seven states, one at a time, each filling the screen; the content region scrolls internally, action bars are pinned.

1. **Welcome / language** (restyled in issue #79) — a full-screen charcoal canvas (warm paper `#F6F4F0` in the light theme) with slow coral, amber, terracotta and blush light drifting behind a centred column: the cloche logo, a small pill badge with a coral dot and the table ("TABLE 7", the table the app resolved from the device's pairing code), the restaurant name in Poppins 600 (clamp from 28 to 42px), one line of welcome in the secondary text colour, a fixed trilingual label ("Sprache · Language · اللغة", not translated on purpose), and three warm-white language buttons (Deutsch, English, العربية: 54px tall, 14px corners, the soft 3D key look; stacked on a phone, one row from 640px). Hover turns a button warm beige and lifts it 2px, pressing sinks it 1px, and the language you pick turns coral with white text at once, the page switches to that language under the guest's finger, the other two are disabled, and the menu opens 380ms later. The light is four radial gradients moved only with `transform` (28 to 35 second ease-in-out loops that go out and back, so they never jump; no blur filters, images or video), with a soft veil behind the text and a faint grain layer against banding; the contrast of the title, line and label against the moving light was measured every 6 seconds over a loop and never falls below 4.7:1. "Reduce motion" freezes the light. The colours are the `--welcome-*` tokens and follow the guest's theme (dark by default). The selected button uses the app's button coral, a little deeper than `#F45B55`, so white text keeps 4.5:1. The first version was:
   Full forest `#1F4D3A` panel. Eyebrow "Table 9 · Garden room" (IBM Plex Mono 10px, .18em, uppercase), restaurant name in DM Serif Display 44px on two lines, one line of body copy, then three language buttons stacked at the bottom (17px vertical padding, 14px radius): Deutsch, English (selected style: paper fill, forest text), العربية. Each button shows the language on the left and its code on the right.
2. **Menu** — header with "Menu" (DM Serif Display 20px) + "TABLE 9" eyebrow, and a language pill (`EN ▾`) on the right. Body is category sections ("STARTERS", "MAINS", "DESSERTS", "DRINKS" as 11px mono uppercase .16em labels) each holding meal cards: 78px square photo slot (11px radius), then meal name 15.5px/600, an "Unavailable" badge in clay when not available, a 2-line clamped description 12.5px, and "from 6,50 €" in mono 13px forest. Unavailable cards are dimmed to 45% opacity and not tappable. When the cart is non-empty, a pinned forest bar appears at the bottom: item-count bubble + "Review order" on the left, running total on the right.
3. **Meal detail** — 196px photo hero with a circular back button top-left; title in DM Serif Display 27px; description; a forest-tinted "PREPARATION" panel; ingredients as 20px-radius chips; size list (one row per size: label left, price right; selected row gets a forest 1.5px ring and 10% forest fill); quantity stepper with 44px buttons; a note textarea for the kitchen. Pinned bottom bar: "Add to order" with the computed price for size × quantity.
4. **Cart** — back button + "Your order" title. One card per line: name, "size · price each", line total, an amber note strip if a note was entered, a quantity stepper and a Remove button. Below: a totals panel with Subtotal, "VAT 19%", a hairline, then Total in mono 19px forest, and the line "Calculated and verified on the server before the order is accepted." Then "Add more items" (outline) and a pinned "Choose payment →" bar.
5. **Payment** — one row per enabled method (Cash, Card, PayPal, Cash desk), each with a 34px mono tag tile, name, and a one-line explanation. Selected row inverts to forest. A "Total to pay" panel below. The pinned confirm button is disabled-looking (`rgba(19,18,17,.25)`) and reads "Select a payment method" until a method is chosen, then becomes forest and reads "Confirm order · 25,11 €".
6. **Order number / live status** — forest header block, centered: "YOUR ORDER NUMBER" eyebrow, the number in mono 76px, and one line of guidance. Below on paper: a "STATUS" timeline with four rows (Submitted, In preparation, Ready, Served), each a 26px dot (filled forest once reached, `rgba(19,18,17,.14)` otherwise), a connector line, the stage label and the timestamp (`—` when not yet reached).
7. **Language switch** — reachable from the menu header pill; returns to state 1 without losing the cart.

Hover/active: menu cards lift on hover (see shadow token); every button darkens or gains a ring. Nothing relies on hover for the tablet.

### 2. Kitchen display — wall monitor
Ground `#0E0D0C`. Header: "Kitchen display" (DM Serif Display 23px) + "WALL MONITOR · STATION 1 · M. BEHR ON SHIFT" eyebrow + a live clock, right-aligned, mono.

A cancelled-order banner appears above the columns when any cancelled order is unacknowledged: clay 1.5px ring, 16% clay fill, order number in `#FF8E75`, text "Cancelled by admin · table 4 · stop work", and an "Acknowledge" button.

Three equal columns in a responsive grid (`minmax(270px, 1fr)`), each with a header (colored dot + uppercase mono label + count) and a scrolling card list:
- **New** — neutral dot (muted text colour); card action: full-width forest "Start"
- **In preparation** — amber dot and label (`#F3C77A` on dark), card order number in amber; action: full-width amber "Ready" with ink text
- **Ready** — green dot and label (`#7DBE9B` on dark), card order number in green; action: outlined "Picked up by waiter"

Stage colours are shared with the hall board: amber means in preparation, green means ready. New is neutral because green is reserved for Ready.

Order card: number in mono 27px, "TABLE 7" eyebrow, and a timer chip on the right — neutral under 6 minutes, amber `#D98F00` from 6, clay `#B3402C` from 12. Then one row per item: quantity in forest-tint mono ("1×"), item name 14px/600, size in muted 11.5px, and — if present — the guest's note as an amber-tinted strip. Empty columns read "Nothing here."

As built (#62): each column is a card with its header (dot, label, count) inside it and a list that scrolls by itself, so the header, the cancelled banner and the "Ran out?" footer always stay on screen however many orders there are (the banner scrolls inside itself above about a quarter of the height). Start is solid forest and Ready solid amber in both themes, as in the capture; the timer chip is solid amber from 6 minutes and solid clay from 12, and the waiting time is shown as minutes and seconds (`8:34`). The Acknowledge button is solid clay with white text; a sold-out chip is a clay tint with a clay ring (the capture's red fill with white text was not readable on a dark ground). On a paper ground the amber text (notes, In preparation) uses the darker text amber. In Arabic the columns are mirrored (New on the right) and the quantity stays "2×".

Footer strip: "RAN OUT?" label followed by one toggle chip per main/dessert reading "Wiener Schnitzel · available" / "· sold out" (clay fill when sold out), and the note "Marking a meal unavailable removes it from every table device at once."

### 3. Hall status board — screen in the dining area
Ground `#131211`. Header: restaurant name in DM Serif Display 26px, live clock in mono 22px right. Two panels in a responsive grid:
- **In preparation** — subtle paper-tint panel, label in `#F3C77A` with a blinking dot (2s, opacity 1→.25), order numbers in mono 74px in amber
- **Ready — please collect** — forest `#1F4D3A` panel with a `rgba(125,190,155,.35)` ring, label `#A8DCC0`, order numbers in mono 86px in green; each number fades/rises in when it arrives (0.4s ease)

Each panel lists its orders stacked vertically, one entry per row: the order number with the table number beside it in amber mono (about 15px, "TABLE 7"). Order numbers are shown with at least three digits (`001`, `002`). Nothing else identifying appears: no names, no prices, no items.

Footer: "Order numbers and table numbers only, no names, no prices on this screen."

`shots/hall.png` is the original prototype capture: it predates the table numbers and the stacked, per-column-coloured layout, so it still shows numbers only in a single colour.

As built (#62): the two panels are the ones above: In preparation a faint tint of its own amber, Ready the forest panel with the soft ring, in both themes. The In preparation dot blinks (2 s, 1 to .25); that is the only continuous motion. A number that arrives, including one that moves from In preparation to Ready, fades and rises in over 0.4 s; the numbers already on the board do not move. With "reduce motion" switched on there is no animation at all (the dot stays lit, numbers simply appear). On a paper ground the amber is the darker text amber (`#7A5200`) because the on-dark amber is too pale to read from across a room (about 2.5:1). The prototype capture shows Ready numbers in cream; the written spec says green, and the board follows the spec (`#A8DCC0` on the forest panel). **Many orders:** a wall display cannot be scrolled, so the board is exactly one screen tall. Each panel's numbers flow top to bottom and then into further columns, and when they still do not fit they shrink in steps (never below about 28 px in the tested busy evenings: 24 preparing and 7 ready orders at 1366 x 768, and a database with over 80 preparing orders). A quiet board keeps the designed 74 and 86 px. In Arabic the board is mirrored: In preparation on the right, numbers start at the right edge and the table label sits to their left.

### 4. Management backend — office computer
**Layout (issue #79).** A narrow navigation rail (120px, floating 12px in from the window edge with 24px rounded corners) on the left, the page in the middle, and on the Dashboard an order panel (340px) on the right, in a CSS grid. The rail follows the reference (`Documentation/Design/new-style/slidbar.png`): it is one tone lighter than the page with no border; the colourful cloche logo sits straight on it near the top, then a stretch of empty space, then the pages and Sign out as small 18px icons (Dashboard, Orders, Audit history, Meals, Raw materials, Tables & devices, Staff accounts) on one tall neutral capsule that reaches in from the rail's edge and ends in a large curve, each with a tiny 9.5px label beside it on the dark. Inactive icons and labels are muted grey; the current page has a coral icon, a coral label and a dot on the capsule's edge that slides to it when you navigate (200 to 300ms). Sign out is muted like the rest and turns soft red only under the pointer. Two round raised controls with a muted red-brown surface are pushed to the bottom: Settings (a gear that opens the Settings page) and the theme switch; the three language buttons sit above them. The app has no notifications, so there is no bell. Light uses the same geometry (capsule `#E3E3DF` on white, coral kept). In right-to-left languages the rail goes to the right edge and the panel to the left (a true mirror, not mirrored English). Below 900px the rail becomes a drawer with the same capsule, labels and bottom controls, opened from a bar at the top: the page behind is inert while it is open, Escape closes it, and focus returns to the menu button. Below 1200px the order panel opens as a dialog when an order is chosen. The capsule is `#404040` instead of the reference's lighter `#555` so the muted icons on it keep 3:1.

- **Dashboard** (the landing page, `/admin/dashboard`) — built only from real data. A search field filters the meals and the loaded orders (it finds a meal in any language, an order by number, table or dish). *Categories* and *Meals* are sliders: each shows as many items as fit its width (a desktop four or five categories and three meals, a phone three and one), a native swipe works, and "View more" moves on to the next items with an arrow back (both hidden when everything fits). *Categories*: round pictures (a meal of that category; a neutral icon when there is none) with name and meal count, the chosen one ringed in coral. *Meals*: cards with photo, name, availability pill, category, description, "from" price (the cheapest size, in the configured currency) and one action, the same sold-out switch the Meals page has. *Order reports*: a table (order, table, items, amount, status, time) of the newest orders, turning into small cards on a phone. *Order panel*: the chosen order's table, time and payment method, the four status steps as the same badges the table uses, its items (each with a small round photo of the meal beside its name, found by name because an order line stores the name, not the meal, with a plate icon when the menu no longer has it) with notes, the server's total, one button per legal next step (the first is the coral primary) and Cancel, which asks first. The panel shows the server's `nextStatuses` and total; it never works out legality or prices. Not shown, because the app has no such thing: delivery address, delivery charge, promotion code, ratings, a Delivery / Dine in / Takeaway switch (the app is dine-in only), and subtotal and tax lines (the admin order list carries only the total). Status colours are the same everywhere: coral = submitted, amber = in preparation, solid green = ready, quiet green = served, red = cancelled, always with the word.
- **Orders** — one row card per order, newest first: `#104` in mono 19px; item summary ("1× Kaiserschmarrn, 1× Apfelstrudel"); a meta line "TABLE 7 · 10:19 · PAYPAL · 25,11 €"; a status pill (colors per stage as in the Dashboard: submitted coral-tint, in preparation amber-tint, ready solid green, served green-tint, cancelled red-tint); and one button per **legal** next transition (coral fill) plus "Cancel" (red outline). Terminal orders show "no transitions" instead.

  As built: the rows and buttons above, with these differences. The number is three digits (`014`). The item summary shows each line's size in brackets ("1x Wiener Schnitzel (200 g)") and a line under it carries any notes. The meta line shows the time only for today's orders, and date and time for older ones. The buttons come from the server (`nextStatuses`: Start, Ready, Served, one at a time), so the screen never decides what is legal; Cancel is a separate clay button that first asks "Cancel order 014?" in the row, with "Yes, cancel it" and "Keep it". The list refreshes every 5 s and straight after any action; a 409 (another screen moved the order first) shows a short notice and refreshes, and a failed refresh keeps the rows and says the connection was lost. Orders load 20 at a time ("Load more"), up to the newest 100; older ones are in the audit history. Drafts, which nobody has submitted yet, are not listed. A status dropdown and a date picker narrow the list ("Clear filters" resets them); the day is the admin's own, from local midnight to the next, so the server needs no time zone. Admin only.
- **Audit history** — per order, the full list of stage changes: time, stage label, and the staff member who made it.
  As built: one card per order, newest first, showing the order number, table and current stage, then one line per change (date and time, stage, who made it). A change with no staff member behind it, a guest submitting their own order, reads "Guest". A cancelled order also shows, under its entries, who acknowledged the cancellation on the kitchen screen and when (a read-receipt, not a status change). Orders load 20 at a time ("Load more"), and a search box finds one order by its number. Admin only.
- **Meals** — per meal: 46px photo, name, meta "MAINS · 200 g / 300 g · 6 raw materials", an availability toggle (forest-tint "Available" / clay-tint "Unavailable"), and "Edit · DE EN AR".
- **Raw materials / Tables & devices / Staff accounts** — plain tables with a tinted header row. Columns: raw material/unit/in stock/supplier; table/room/seats/paired device (including offline state); name/role/PIN set/last seen.
- **Settings** — two cards. *Currency & tax*: currency code + symbol, symbol position (two mono buttons showing "€ 9,80" and "9,80 €"), tax rate (7% / 19% / 0%), default language. *Payment methods offered*: four on/off rows (Cash, Card, PayPal, Cash desk). Changing tax or payment methods must change the guest flow immediately.

## Interactions & behavior
- Guest: welcome → menu → detail → cart → payment → confirm → status. Back buttons return one step; the cart survives navigation and the language switch.
- Confirming an order issues the order number, shown with at least three digits (`001`, `002`, ... `999`, then four digits). The shown number restarts at `001` when an admin restarts the series from Settings (for example at the start of a day), so it can repeat across series but never among open orders: the restart is refused while any order is open. The server keeps a second, internal number that never repeats, and moves the guest to the status view.
- Kitchen "Start" → in preparation; "Ready" → ready (the number appears on the hall board); "Picked up by waiter" → served (leaves the boards).
- Cancel is admin-only and possible from any stage before served; the kitchen shows the red banner until acknowledged.
- Marking a meal unavailable removes it from every table device; it must not be orderable.
- Boards refresh periodically (the proposal defers push updates to a later version); the prototype ticks once per second for its timers and clocks.
- Transitions: 0.25s ease rise for the guest cart bar, 0.4s ease rise for new hall numbers, 1.8–2s blink on live dots. Nothing else animates.

## State machine (must be server-side, one place)
Stages and legal transitions:
```
draft      → submitted, cancelled
submitted  → preparing, cancelled
preparing  → ready,     cancelled
ready      → served,    cancelled
served     → (terminal)
cancelled  → (terminal)
```
Rules from the proposal that the implementation must honor:
1. Every status change goes through one function that validates the transition, sets the timestamp and writes an audit record. No other code may set a status. Illegal transitions are refused, not silently ignored.
2. Order numbers must be unique under concurrent confirmation — generate inside a locked transaction, not from an in-memory counter.
3. Prices, tax and totals are computed on the server and re-verified before an order is accepted. Never trust a total sent by the device.
4. Order lines store a snapshot of meal title, size and price, so later menu edits cannot rewrite past receipts.
5. Translations are stored per language (interface labels and menu content), so a missing translation is findable. Arabic is a real RTL layout.
6. Role-based access: admin, kitchen, waiter, cashier — each of the four screens exposes only what its role allows.

## Data model (as used by the prototype; expand for production)
- **Meal**: id, category, name, description, preparation method, ingredients[], sizes[{label, price}], available
- **Order**: number, table, status, placedAt, paymentMethod, items[], history[{stage, timestamp, staff}]
- **OrderItem**: name, size, quantity, unitPrice, note (snapshot values)
- **Config**: currency code, symbol, symbol position, tax rate, default language, enabled payment methods
- Also required by the proposal but only represented as static tables in the prototype: raw materials, recipes (meal size → raw materials), tables + paired devices, staff accounts with roles

## Dialogs and notices (admin)
The browser's own `alert`, `confirm` and `prompt` are not used anywhere (ESLint `no-alert` keeps it so). The management backend has its own, all built on one shared `Modal`:

- **The shared dialog:** focus moves into it when it opens (the first field, or the safe button for a question), Tab and Shift+Tab stay inside, Escape and a click outside close it, the page behind does not scroll, and focus goes back to the button that opened it (to the page content when that button is gone, as after a delete). A short fade (0.15 s) on open; none with "reduce motion". Forms use the 560 px card, questions and notices a 440 px one. Buttons: plain (hairline), forest for the main action, clay for a destructive one.
- **Delete (staff, tables, meals, categories, raw materials):** a question that names the item ("Delete table 7?"), one sentence on what happens, and verb-first buttons ("Cancel" / "Delete table"). Cancel has focus. The delete runs inside the dialog; if it fails, the reason ("it may still have orders") appears in the dialog and the button reads "Try again".
- **Reset PIN:** a field with a visible label ("New PIN"), its rule ("4 to 8 digits") and a numeric keypad on a tablet. The problem is shown under the field when you leave it with a bad PIN, and goes the moment it is fixed; "Show PIN" reveals the digits (there is no second field to catch a typing mistake). A server error stays in the dialog. Success closes it and a toast says "PIN updated for M. Behr".
- **Pairing a device:** the code in 40 px mono, always left to right (also in Arabic), a Copy button that says "Copied", and Done.
- **Toasts** for small messages (a failed pairing or availability change, a PIN update): bottom corner, a dismiss button, a success goes after about 4 s and an error after about 8 s; errors are announced at once, successes politely.

## Localisation
German, English, Arabic. Interface labels and menu content are both translated: every meal has a name, description, preparation method and ingredient list in all three languages. Arabic is fully right-to-left (not mirrored English) — **this is not built in the prototype**; only the language choice exists. Numbers and currency format follow the language (the prototype uses German-style decimals: `25,11 €`).

Decisions made while building it:
- **Arabic uses Latin digits** (`25.11`, `10:41:08`, `001`), not Arabic-Indic digits. Order numbers, table numbers, timers and quantities are Latin on every screen, so prices and times match them instead of switching digit style mid-screen. Decimal separators, currency position and date order still follow the language.
- **Direction-aware icons:** back and forward arrows swap sides in Arabic (back points right), so they always point the way the text reads.
- **Data text is isolated:** sizes (`300 g`), device codes, room names and guests' notes are wrapped in `<bdi>` (or a Unicode isolate inside translated sentences), so they keep their own order inside an Arabic line instead of rendering as `g 300`. Size labels in the Arabic menu use Arabic units (`300 غرام`, `0.3 لتر`).
- **Missing translations are caught early:** `npm run check:i18n` (in `frontend/`) fails if the three locale files have different keys or placeholders, or if the code uses a key that does not exist.

## Assets
- No photography included. Meal photos are empty drop slots in the prototype; production needs an image upload per meal (and per size, if useful). As built: upload works per meal and per raw material, and the demo data ships with a flat illustration for each of the 8 meals and 31 raw materials (sources and renderer in `backend/seed-art`, the PNGs in `backend/src/main/resources/seed-images`), attached by the dev seeder.
- Fonts are Google Fonts: DM Serif Display, Bricolage Grotesque, IBM Plex Mono.
- No icon set is used — icons are deliberately absent; text labels and mono tags do the work.
- App icon (favicon and PWA): a serving cloche in paper on forest green, `frontend/public/favicon.svg`; the PNG sizes are rendered by `frontend/scripts/render-icons.mjs`. The app is installable (manifest, service worker for the app shell; `/api` is never cached).

## Files in this bundle
- `Documentation/Design/Restaurant Orders System.dc.html` — the interactive prototype, all four screens (open in a browser; use the tabs in the header to switch screens)
- `Documentation/Design/Restaurant Orders System Deck.dc.html` + `deck-stage.js` — the 13-slide presentation of the system
- `Documentation/Design/image-slot.js` — the photo-slot component the prototype uses for empty meal images
- `Documentation/Final Project Proposal - Restaurant Orders System.pdf` — the original requirements
- `Documentation/Design/shots/` — PNG captures of the four screens (guest menu, meal detail, order status, kitchen, hall board, management)
