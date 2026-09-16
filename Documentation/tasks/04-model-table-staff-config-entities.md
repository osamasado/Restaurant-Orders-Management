# Issue #4: Model Table, StaffAccount (roles), and Config entities

## What was done

Added the three remaining M1: Foundations entities beyond the menu domain: `Table` (a physical table paired to a guest device), `StaffAccount` (staff login with a role), and `Config` (a single-row global settings table). Each got its own package (`table`, `staff`, `settings`), a Spring Data repository, and a Flyway migration, following the same pattern as issue #3's menu entities.

## The other files

- **`table/model/Table.java`** — `tableNumber` (unique), `room`, `seats`, plus `pairedDeviceId`/`lastSeenAt` to represent pairing and online/offline state. No separate `Device` entity exists anywhere in the requirements docs, so pairing is modeled as plain fields on `Table` itself rather than a new entity.
- **`V7__table.sql`** — creates `restaurant_table`, not `table`: the entity class is named `Table` (matching CLAUDE.md's domain entity list), which collides with the SQL reserved word `TABLE` and with the `jakarta.persistence.Table` annotation class. The annotation is used fully-qualified (`@jakarta.persistence.Table(name = "restaurant_table", ...)`) instead of imported, and the table is explicitly renamed so Hibernate never emits an unquoted `table` identifier against Postgres.
- **`staff/model/Role.java`** — bare enum (`ADMIN`, `KITCHEN`, `WAITER`, `CASHIER`, `USER`), matching `i18n/Language`'s enum-only style. `USER` was added on top of the four roles CLAUDE.md's "Role-based access" section names for the staff-facing screens — likely intended for a guest-facing account distinct from `StaffAccount`, which doesn't exist as its own entity yet.
- **`staff/model/StaffAccount.java`** — `name`, `role`, nullable `pinHash`, nullable `lastSeenAt`. No username/password anywhere in the design doc — staff sign in via a PIN. `pinHash` is nullable (an account can exist before its PIN is set) and stores a hash, never plaintext; the actual hashing/verification logic belongs to issue #5 (auth), not this ticket.
- **`settings/model/PaymentMethod.java`** / **`SymbolPosition.java`** — bare enums (`CASH`/`CARD`/`PAYPAL`/`CASH_DESK`, `PREFIX`/`SUFFIX`), pulled from the design doc's Settings screen (exactly those 4 payment method toggles and the currency-symbol-position toggle).
- **`settings/model/Config.java`** — `currencyCode`, `currencySymbol`, `symbolPosition`, `taxRate`, `defaultLanguage` (reuses the existing `i18n.Language` enum), and `enabledPaymentMethods` as a `Set<PaymentMethod>` via `@ElementCollection` (same annotation family as `MealTranslation.ingredients`, but a `Set` since payment methods aren't ordered). No DB-level singleton enforcement — the migration just seeds exactly one row; fetching "the" config by convention is left to the service layer built on top of this later.
- **`V9__config.sql`** — this repo's first seed-data migration (no `INSERT` existed in any prior migration): one default row (`EUR`, `€`, suffix position, 19% tax, `DE`, all four payment methods enabled).
- **`Role`/`StaffAccount.role`** — kept enum-only enforcement (plain `VARCHAR` column, `EnumType.STRING`, no DB `CHECK` constraint), matching `Language`'s existing precedent rather than introducing a new constraint style for this one column.

## Verification performed

1. `./mvnw compile test-compile` from `backend/` — all new entities, repositories, and tests compile cleanly against the existing Spring Boot 4.1 / Java 25 setup.
2. Repository tests (`TableRepositoryTest`, `StaffAccountRepositoryTest`, `ConfigRepositoryTest`) were written following the existing `@DataJpaTest` + Testcontainers pattern, but **could not be run in this session** — Docker isn't available in this environment (confirmed it's an environment gap, not a code issue: the pre-existing `CategoryRepositoryTest` fails with the identical "Previous attempts to find a Docker environment failed" error here). These need a real `./mvnw test` run against Docker (local machine or CI) before merging, to confirm the entity mappings match the new migrations under `ddl-auto=validate` and that `ConfigRepositoryTest.migrationSeedsSingleDefaultConfigRow` actually sees the seeded row.
3. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` against the real Docker Compose Postgres was **not run** for the same reason — still outstanding before this branch is considered verified end-to-end.
