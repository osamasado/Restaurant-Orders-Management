# Restaurant Orders Management

A table-side restaurant ordering system built to replace the traditional waiter hand-off chain. A guest orders from their table, the kitchen sees it live, a dining-hall board shows order status, and a management backend controls the menu and every order end to end.

> Final project for a Java Weiterbildung (upskilling) program.

## The four screens

| Screen | Where it runs | What it does |
|---|---|---|
| **Guest ordering** | Tablet at the table, or the guest's own phone | Browse the menu by category with photos and prices, view meal details, build a cart, choose a payment method, confirm and track the order live |
| **Kitchen display** | Wall monitor in the kitchen | New / In preparation / Ready columns, order cards with items, notes, and a countdown timer |
| **Hall status board** | Screen in the dining area | Order numbers only (no names, prices, or table numbers) across In preparation / Ready |
| **Management backend** | Office computer | Menu, raw materials, prices, tables, staff accounts, and full control over every order's status |

## How an order moves

```
draft → submitted → preparing → ready → served
                                       ↘ cancelled (from any stage before served)
```

Every transition is timestamped and records which staff member made it, so the full history of an order is auditable.

## What makes this more than a CRUD app

- **A single state machine, not a status field.** No part of the system may set an order's status directly — every change goes through one validated transition function.
- **Order numbers survive concurrency.** Generated inside a locked transaction so two tables confirming at the same instant can't collide.
- **Prices are calculated on the server, never on the device.** The guest-visible total is recalculated and verified server-side before an order is accepted.
- **Orders snapshot what was actually sold.** Meal title, size, and price are copied onto the order line, so later menu edits never rewrite past orders.
- **Real internationalization.** German, English, and Arabic — Arabic is fully right-to-left, not a mirrored layout — for both UI labels and menu content.
- **Role-based access.** Each screen (guest, kitchen, hall board, admin) only exposes what its role — admin, kitchen, waiter, cashier — is allowed to do.

## Tech stack

- **Backend:** Java, Spring Boot, Lombok
- **Database:** PostgreSQL
- **Frontend/client:** not yet decided — planned as a server-authoritative web app, installable as a PWA on phones/tablets

## Project status

Early stage: requirements and UX design are complete; implementation hasn't started yet.

- `Documentation/Final Project Proposal - Restaurant Orders System.pdf` — requirements and scope
- `Documentation/Design/README.md` — visual design, screen-by-screen UX spec, and data model
- `Documentation/Design/*.dc.html` and `shots/*.png` — a high-fidelity click-through prototype (design reference only, not production code)

Not in scope for this iteration: live payment charging, automatic stock deduction, kitchen ticket printing, reporting/analytics, and real-time push updates (periodic refresh instead).

See `CLAUDE.md` for the full architecture notes and conventions used by AI coding assistants working on this repo.
