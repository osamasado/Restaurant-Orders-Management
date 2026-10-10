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
- **Frontend/client:** React 19, TypeScript and Vite: a server-authoritative web app, installable as a PWA on phones and tablets
- **Packaging:** Docker images for the backend and the frontend (nginx), run together with Docker Compose and published on Docker Hub

## Run with Docker

With Docker and Docker Compose installed, nothing else is needed (no Java, Node or PostgreSQL):

```
cp .env.example .env        # then set POSTGRES_PASSWORD in .env
docker compose up --build
```

Open http://localhost:8088. The guest, kitchen, hall and admin screens are all on that one address (`/guest`, `/kitchen`, `/hall`, `/admin`).

The first start has an empty database and no staff account. For real use set `BOOTSTRAP_ADMIN_NAME` and `BOOTSTRAP_ADMIN_PIN` (and optionally `BOOTSTRAP_KITCHEN_NAME`/`BOOTSTRAP_KITCHEN_PIN`) in `.env` before the first start: they create the first administrator, and you remove the PINs afterwards (`Documentation/deployment.md`, "First administrator"). To try the app with the demo menu, tables and staff instead, set `APP_SEED_DEMO=true`; its accounts all have the public PIN `1234`, so turn it off before real use.

| Command | What it does |
|---|---|
| `docker compose up --build -d` | Build and start in the background |
| `docker compose logs -f backend` | Follow the backend's log |
| `docker compose down` | Stop; the database and the meal photos are kept (named volumes `pgdata` and `uploads`) |
| `docker compose down -v` | Stop and **delete** the database and the photos |

To run the ready-made images from Docker Hub instead of building (`osamasado2024/restaurant-orders-backend` and `-frontend`, for amd64 and arm64), use `docker compose -f docker-compose.hub.yml up -d`. Every setting, the volumes, backups, HTTPS and how a release is published are in `Documentation/deployment.md`.

## Project status

All four screens (guest ordering, kitchen display, hall board and management backend) are implemented, in German, English and Arabic, light and dark.

- `Documentation/Final Project Proposal - Restaurant Orders System.pdf` — requirements and scope
- `Documentation/Design/README.md` — visual design, screen-by-screen UX spec, and data model
- `Documentation/Design/*.dc.html` and `shots/*.png` — a high-fidelity click-through prototype (design reference only, not production code)

Not in scope for this iteration: live payment charging, automatic stock deduction, kitchen ticket printing, reporting/analytics, and real-time push updates (periodic refresh instead).

See `CLAUDE.md` for the full architecture notes and conventions used by AI coding assistants working on this repo.
