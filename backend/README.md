# Backend

Spring Boot + Lombok backend, PostgreSQL database, Flyway migrations.

## Running locally (dev profile)

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

This uses Spring Boot's Docker Compose support to automatically start the Postgres container defined in `compose.yaml` and connects to it — no manual setup needed. Flyway applies migrations from `src/main/resources/db/migration` on startup.

## Running tests

```
./mvnw test
```

Tests run against a real, ephemeral PostgreSQL container via Testcontainers (see `TestcontainersConfiguration`), not an in-memory substitute — this keeps test behavior faithful to the Postgres-specific Flyway migrations.

## Production profile

The `prod` profile has no hardcoded database connection — it requires these environment variables, and fails to start if any are missing:

- `DATABASE_URL` (JDBC URL, e.g. `jdbc:postgresql://host:5432/dbname`)
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

```
SPRING_PROFILES_ACTIVE=prod DATABASE_URL=... DATABASE_USERNAME=... DATABASE_PASSWORD=... java -jar target/backend-0.0.1-SNAPSHOT.jar
```
