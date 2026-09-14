# Restaurant Orders Management

## Project summary

A table-side restaurant ordering system with four connected surfaces: guest ordering (tablet/phone), kitchen display (wall monitor), hall status board (dining area screen), and a management backend (office). Goal: replace the traditional waiter hand-off chain — an order placed on a phone should appear on the kitchen screen within seconds and move across the hall board as the kitchen advances it.

## Current state

Green-field: no source code or build tooling exists yet. Only `README.md` (empty) is tracked in git; `Documentation/`, `.idea/`, and `Restaurant-Orders-Management.iml` are currently untracked.

## Documentation map

- `Documentation/Final Project Proposal - Restaurant Orders System.pdf` — **requirements/scope source of truth**.
- `Documentation/Design/README.md` — **UX/visual design source of truth** (design tokens, screen-by-screen states, state machine, data model sketch).
- `Documentation/Design/*.dc.html`, `shots/*.png` — **visual reference prototype only**. High-fidelity but client-side only; do not port its state handling — order state, order-number generation, price calculation, and the audit log must all live on the server.

## Tech stack

- **Backend:** Java, Spring Boot, Lombok
- **Database:** PostgreSQL
- **Frontend/client:** not yet decided. The design doc implies a server-authoritative web app installable as a PWA, with four distinct UIs (guest, kitchen, hall board, admin) — but the framework choice is open.

## Non-negotiable architecture rules

These come directly from the proposal and design doc — they're what makes this a real project rather than a CRUD toy, and must hold regardless of implementation details:

- **One server-side state machine, no direct status writes.** Legal transitions only:
  `draft → submitted, cancelled`
  `submitted → preparing, cancelled`
  `preparing → ready, cancelled`
  `ready → served, cancelled`
  `served` / `cancelled` are terminal.
  Every transition is timestamped and records which staff member made it (audit trail).
- **Order numbers via locked transaction**, not a naive counter — must stay unique under concurrent submissions from multiple tables.
- **Prices/tax calculated server-side only**, recalculated and verified before an order is accepted. Never trust a client-submitted total.
- **Order lines snapshot** the meal title/size/price at order time, so later menu edits never rewrite historical orders.
- **i18n: German, English, Arabic.** Arabic is full RTL layout (not mirrored English), not just translated strings — both UI labels and menu content are translated.
- **Role-based access** per screen: admin, kitchen, waiter, cashier roles; each of the four screens only exposes what its role permits.

## Key domain entities

- `Meal` — category, translations, sizes/prices, ingredients, prep method, availability
- `Order` — number, table, status, timestamps, payment method, items, audit history
- `OrderItem` — snapshotted name/size/price/quantity/note
- `RawMaterial` / `Recipe` — meal size ↔ raw materials
- `Table` — paired device
- `StaffAccount` — role (admin, kitchen, waiter, cashier)
- `Config` — currency code/symbol/position, tax rate, default language, enabled payment methods

## Out of scope for this iteration

- Real payment charging (flow only, no live merchant integration)
- Automatic stock deduction from recipes
- Kitchen ticket printing
- Reporting/analytics
- Real-time push updates (periodic refresh is acceptable instead)
