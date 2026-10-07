# Handoff: Restaurant Orders System

## Overview
A table-side ordering system for a restaurant. Guests order from a device at their table; the kitchen and the dining hall see every order live; a management backend defines the menu and controls every order's status. Four screens, one shared order state.

Source of truth for requirements: `Documentation/Final Project Proposal - Restaurant Orders System.pdf` (included).

## About the design files
The HTML files in this bundle are **design references**, not production code. `Restaurant Orders System.dc.html` is an interactive prototype: all four screens in one page, sharing in-memory state so the flow can be demonstrated end to end. Recreate these designs in the target codebase using its own framework and patterns (React/TypeScript/Java Spring Boot/Lombok + server rendering — whatever the project uses). If no codebase exists yet, pick a stack suited to the requirements below (the proposal implies a server-authoritative web app installable as a PWA) and implement there.

Do not port the prototype's state handling. In the prototype everything lives in one client-side component; in production the order state, order-number generation, price calculation and audit log must all live on the server.

## Fidelity
**High fidelity.** Colors, typography, spacing and copy are final and should be matched. Dish photography is not included — the prototype uses empty photo slots; the real app needs an image upload per meal.

## Design tokens

Colors
- Ink / darkest ground: `#131211`
- Kitchen ground (slightly darker): `#0E0D0C`
- Paper (guest + admin ground): `#F5F0E8`
- Paper card / screen: `#FBF8F2`, pure white cards `#FFFFFF`
- Admin sidebar ground: `#EFE9DF`
- Forest (primary accent, confirm actions, "ready"): `#1F4D3A`; hover/lighter `#2C6B51`
- Forest tints on dark: `#7DBE9B` (labels), `#A8DCC0` (hall "ready")
- Amber (in-preparation, kitchen notes): `#D98F00`; on-dark label `#F3C77A`; note text on paper `#7A5200` / `#8A5C00`
- Clay (cancelled, destructive, "unavailable"): `#B3402C`; on-dark `#FF8E75` / `#FFC6B6`
- Hairlines: `rgba(19,18,17,.08–.14)` on paper, `rgba(245,240,232,.09–.14)` on dark
- Secondary text: `rgba(19,18,17,.45–.68)` on paper, `rgba(245,240,232,.4–.8)` on dark

Typography
- Display / headings: **DM Serif Display**, weight 400, letter-spacing −.01 to −.02em
- UI text: **Bricolage Grotesque**, 400/500/600
- Numbers, labels, order numbers, prices: **IBM Plex Mono**, 500/600; uppercase eyebrow labels use letter-spacing .12–.2em
- Guest device scale: body 12.5–14px, meal title 15.5px, screen title 20–27px, order number 76px
- Kitchen scale: order number 27px, item 14px, column label 12px
- Hall board: order numbers 74px (in preparation) and 86px (ready) — sized to be read from the back of the room
- Minimum touch target on the guest device: 44px

Radii
- 40px device bezel, 28px device screen, 16–20px cards and primary buttons, 13–15px inner cards and secondary buttons, 8–11px chips and small buttons, 20px pills

Spacing
- 4px base step. Common gaps: 6, 9, 10, 14, 16, 20, 26px. Screen padding: 16–24px inside the guest device, 20–28px on kitchen/hall, 22–26px in admin content.

Shadows
- Cards on paper: `inset 0 0 0 1px rgba(19,18,17,.08)` plus `0 1px 2px rgba(19,18,17,.07)`
- Card hover (guest menu): `0 6px 18px -8px rgba(19,18,17,.3), inset 0 0 0 1px rgba(31,77,58,.3)`
- Primary bar (guest cart): `0 16px 30px -14px rgba(31,77,58,.8)`
- Cards on dark: `inset 0 0 0 1px rgba(245,240,232,.1)`

## Screens / views

### 1. Guest ordering — tablet at the table, or the guest's own phone
Device frame 432px wide (phone variant 372px), screen 716px tall, min 560px. Seven states, one at a time, each filling the screen; the content region scrolls internally, action bars are pinned.

1. **Welcome / language** — full forest `#1F4D3A` panel. Eyebrow "Table 9 · Garden room" (IBM Plex Mono 10px, .18em, uppercase), restaurant name in DM Serif Display 44px on two lines, one line of body copy, then three language buttons stacked at the bottom (17px vertical padding, 14px radius): Deutsch, English (selected style: paper fill, forest text), العربية. Each button shows the language on the left and its code on the right.
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
Two-pane layout: 224px sidebar on `#EFE9DF` with a "MANAGEMENT" label and seven nav items (Orders `live`, Audit history `log`, Meals `8`, Raw materials `24`, Tables & devices `14`, Staff accounts `7`, Settings); the active item is an ink pill with paper text. A signed-in card at the bottom shows "O. Sado / ROLE: ADMIN". Content pane on `#F5F0E8` with a DM Serif Display 25px title plus a muted subtitle.

- **Orders** — one row card per order, newest first: `#104` in mono 19px; item summary ("1× Kaiserschmarrn, 1× Apfelstrudel"); a meta line "TABLE 7 · 10:19 · PAYPAL · 25,11 €"; a status pill (colors per stage: submitted forest-tint, in preparation amber-tint, ready solid forest, served neutral, cancelled clay-tint); and one button per **legal** next transition (ink fill) plus "Cancel" (clay outline). Terminal orders show "no transitions" instead.

  As built: the rows and buttons above, with these differences. The number is three digits (`014`). The item summary shows each line's size in brackets ("1x Wiener Schnitzel (200 g)") and a line under it carries any notes. The meta line shows the time only for today's orders, and date and time for older ones. The buttons come from the server (`nextStatuses`: Start, Ready, Served, one at a time), so the screen never decides what is legal; Cancel is a separate clay button that first asks "Cancel order 014?" in the row, with "Yes, cancel it" and "Keep it". The list refreshes every 5 s and straight after any action; a 409 (another screen moved the order first) shows a short notice and refreshes, and a failed refresh keeps the rows and says the connection was lost. Orders load 20 at a time ("Load more"), up to the newest 100; older ones are in the audit history. Drafts, which nobody has submitted yet, are not listed. A status dropdown and a date picker narrow the list ("Clear filters" resets them); the day is the admin's own, from local midnight to the next, so the server needs no time zone. Admin only.
- **Audit history** — per order, the full list of stage changes: time, stage label, and the staff member who made it.
  As built: one card per order, newest first, showing the order number, table and current stage, then one line per change (date and time, stage, who made it). A change with no staff member behind it, a guest submitting their own order, reads "Guest". A cancelled order also shows, under its entries, who acknowledged the cancellation on the kitchen screen and when (a read-receipt, not a status change). Orders load 20 at a time ("Load more"), and a search box finds one order by its number. Admin only.
- **Meals** — per meal: 46px photo, name, meta "MAINS · 200 g / 300 g · 6 raw materials", an availability toggle (forest-tint "Available" / clay-tint "Unavailable"), and "Edit · DE EN AR".
- **Raw materials / Tables & devices / Staff accounts** — plain tables with a tinted header row. Columns: raw material/unit/in stock/supplier; table/room/seats/paired device (including offline state); name/role/PIN set/last seen.
- **Settings** — two cards. *Currency & tax*: currency code + symbol, symbol position (two mono buttons showing "€ 9,80" and "9,80 €"), tax rate (7% / 19% / 0%), default language. *Payment methods offered*: four on/off rows (Cash, Card, PayPal, Cash desk). Changing tax or payment methods must change the guest flow immediately.

## Interactions & behavior
- Guest: welcome → menu → detail → cart → payment → confirm → status. Back buttons return one step; the cart survives navigation and the language switch.
- Confirming an order issues the order number, shown with at least three digits (`001`, `002`, ... `999`, then four digits so numbers never repeat), and moves the guest to the status view.
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
