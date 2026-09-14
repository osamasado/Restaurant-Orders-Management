# Issue #2: PostgreSQL integration & base project configuration

## What was done

Set up the backend so it actually connects to a real database, tracks schema changes properly, and has separate configs for local development vs. production — none of that existed before (there was just an empty `application.properties` and an unused Postgres driver).

## Why each dependency

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-jpa` | Brings in Hibernate/JPA + the `DataSource`/transaction machinery. Needed before anything can talk to a database via entities/repositories (used starting with issue #3). |
| `postgresql` *(already present)* | The JDBC driver — the actual code that speaks Postgres's wire protocol. |
| `spring-boot-starter-flyway` | Wires Flyway into Spring Boot's startup (auto-runs migrations before the app is ready). In Spring Boot 4.x this Spring-integration glue was split out from plain Flyway into its own module — just having `flyway-core` alone (the first attempt) silently did nothing. |
| `flyway-database-postgresql` | Flyway 10+ split out per-database support; without this, Flyway doesn't know how to talk to Postgres specifically. |
| `spring-boot-docker-compose` | Lets the app auto-start/stop the Postgres container defined in `compose.yaml` when run locally — no manual `docker compose up` needed. Dev convenience only (disabled in `prod`). |
| `spring-boot-testcontainers` + `testcontainers-junit-jupiter` + `testcontainers-postgresql` | Lets tests boot against a real, throwaway Postgres container instead of a fake/in-memory database — so tests catch real Postgres-specific bugs (and actually exercise the Flyway migrations). |

## The other files

- **`compose.yaml`** — describes the local Postgres container (name, credentials); Spring Boot reads this to start it automatically.
- **`application.properties` / `-dev` / `-prod`** — settings common to both environments, then environment-specific overrides. `prod` requires `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD` as env vars with no fallback, so a misconfigured deploy fails loudly instead of quietly using dev settings.
- **`V1__init.sql`** — the first (empty) Flyway migration; proves the migration pipeline works without pretending to model the real schema yet (that's issue #3's job).
- **`TestcontainersConfiguration.java`** — tells tests to spin up that throwaway Postgres container.
- **`LombokSmokeTest.java`** — a tiny test proving Lombok's code generation (`@Data` → getters/equals/etc.) actually compiles and works, since nothing in the codebase had exercised it yet.
- **`backend/README.md`** — how to run/test/deploy, so this isn't tribal knowledge.

## Verification performed

1. `./mvnw test` — passes; Flyway creates `flyway_schema_history` and applies `V1__init.sql` against a Testcontainers-managed Postgres.
2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` — Docker Compose auto-starts Postgres, Flyway migrates it, app boots successfully.
3. `prod` profile fails fast with a clear error when `DATABASE_URL`/etc. aren't set, instead of silently falling back to dev settings.

Two real bugs were found and fixed along the way:
- A hardcoded host port in `compose.yaml` (`5432:5432`) broke Spring Boot's docker-compose port introspection — fixed by letting Docker assign the host port dynamically (`5432`) and letting Spring Boot derive the connection details automatically.
- Testcontainers 2.x renamed its module artifacts with a `testcontainers-` prefix (`org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`, etc.).
- Testcontainers 2.x also deprecated `org.testcontainers.containers.PostgreSQLContainer` in favor of `org.testcontainers.postgresql.PostgreSQLContainer`, which dropped the old generic `<SELF>` type parameter — `TestcontainersConfiguration` was updated to use the new, non-deprecated class.
