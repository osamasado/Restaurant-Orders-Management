# Issue #8: Seed script for demo data (menu, tables, staff)

## What was done

Added a demo-data seed: 4 menu categories with 8 meals (sizes, prices, DE/EN/AR name/label translations), 6 demo tables, and one staff account per role (admin/kitchen/waiter/cashier) — so the app always starts with a realistic demo dataset. No seeding convention existed in this codebase before this ticket; the only precedent (`V9__config.sql`) was a one-shot Flyway `INSERT`, which isn't naturally re-runnable the way "idempotent or easily re-runnable for demo resets" needs. This introduces both the `@Profile`-gated `CommandLineRunner` convention and the `seed` package fresh.

All the seed content — dish names, prices, sizes, table numbers/rooms/device states, staff names — is reused verbatim from the project's own design prototype (`Documentation/Design/Restaurant Orders System.dc.html`) rather than invented, since it's already the canonical example data for this project's demos.

## The other files

- **`table/repository/TableRepository.java`** — added `findByTableNumber`, the one new repository method needed for idempotent per-row table seeding.
- **`seed/DemoDataSeeder.java`** — the actual seeding logic, plain `@Component` (always registered, does nothing until `seedAll()` is called). `seedMenu()` is coarsely idempotent (skips entirely if any category already exists — the menu is one atomic dataset with no natural per-category key); `seedTables()`/`seedStaff()` are idempotent per-row via `findByTableNumber`/the existing `findByName`. Category/meal names and size labels get full DE/EN/AR translations; `description`/`preparationMethod`/`ingredients` stay English-only (the prototype has no DE/AR content anywhere to draw from, and those fields aren't guest-menu-*list*-visible — name + price is, per the design doc). Rinderroulade is seeded `available=false`, matching the prototype's own sold-out flag.
- **`seed/DemoDataStartupRunner.java`** — tiny `@Profile("dev") CommandLineRunner` that just calls `seedAll()`. Kept separate from `DemoDataSeeder` specifically so the seeding logic is testable without activating the `dev` profile (which would also flip on `spring.docker.compose.enabled` and could conflict with a test's own Testcontainers datasource).
- **`seed/DemoDataSeederTest.java`** — Testcontainers-backed, asserts post-seed counts and idempotency.

## Verification performed

1. **Docker became available in this sandbox partway through this ticket** — a real change from every prior backend ticket this session, where the exact same kind of Testcontainers-backed test could only be compiled, never actually run. Re-ran the **entire backend suite** for the first time all session: `./mvnw test` — 24 tests across 12 classes, 0 failures, 0 errors, including every previously-compile-only-verified test from issues #4 and #5.
2. `DemoDataSeederTest` specifically: `seedAll()` produces 4 categories / 8 meals / 6 tables / 4 staff accounts; calling it a second time changes nothing — every seed step logged "already seeded, skipping" and the counts stayed identical.
3. Full manual end-to-end run against the real dev Postgres (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`): startup log showed `Seeded demo menu: 4 categories, 8 meals`; `curl POST /api/staff/login {"name":"O. Sado","pin":"1234"}` returned `200` with the correct role and set `lastSeenAt`; the resulting session cookie worked on `GET /api/staff/me`; `GET /api/staff/accounts` (admin-only) listed all 4 seeded accounts with their roles, `lastSeenAt` still `null` for the three that haven't logged in, and `pinHash` never present in the response.
   - Hit a stale leftover `java` process from earlier in the session still holding port 8080 (started that morning, running pre-seed-change compiled classes) — killed it and restarted cleanly before this check, otherwise it would have silently tested old code.

**Demo login**: all four seeded staff accounts use PIN `1234` (bcrypt-hashed, never stored in plaintext) — O. Sado (Admin), M. Behr (Kitchen), L. Adler (Waiter), T. Nowak (Cashier).
