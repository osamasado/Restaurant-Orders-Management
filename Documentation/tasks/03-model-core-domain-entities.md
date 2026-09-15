# Issue #3: Model core domain entities (Meal, Category, RawMaterial, Recipe)

## What was done

Introduced the backend's actual menu data model — until now the backend had exactly one class (`BackendApplication`) and no domain code. Added `Category`, `Meal` (with per-language translations and independently-priced sizes), `RawMaterial`, and `Recipe` (linking a meal size to its raw materials), each with a Spring Data repository and a Flyway migration, in a new `menu` package.

## The other files

- **`i18n/Language.java`** — `DE`/`EN`/`AR` enum shared by both translation tables here, reusable later by `Config.defaultLanguage`.
- **`menu/Category.java` / `CategoryTranslation.java`** — a menu section (STARTERS/MAINS/...) with a `sortOrder` and cascaded per-language translations; unique per `(category, language)`.
- **`menu/Meal.java` / `MealTranslation.java` / `MealSize.java`** — a menu item with a single `available` flag (not per-size/per-language — confirmed by the kitchen "mark unavailable" behavior), cascaded translations (name/description/preparation method/ingredients per language), and independently-priced sizes.
- **`menu/RawMaterial.java`** — internal/back-of-house only, never guest-facing, so unlike Category/Meal it has no translations.
- **`menu/Recipe.java`** — links a `MealSize` (not `Meal` directly — the proposal explicitly states *"Recipes link each meal size to its raw materials"*) to a `RawMaterial` with a `quantity`; no stock-deduction logic yet (out of scope for this iteration).
- **`menu/*Repository.java`** — `CategoryRepository`, `MealRepository`, `RawMaterialRepository`, `RecipeRepository` (matching the ticket's 4 named entities; `MealTranslation`/`MealSize` are managed purely via `Meal`'s cascade).
- **`V2__category.sql` .. `V5__recipe.sql`** — one migration per entity/commit rather than one big schema file, matching Flyway's own convention of many small versioned migrations.
- **`TestcontainersConfiguration.java`** — made `public` (was package-private) so the new tests in the `menu` sub-package can reuse the same Postgres container setup from issue #2.

All entities use Lombok `@Getter @Setter @NoArgsConstructor` only — deliberately no `@ToString`/`@EqualsAndHashCode`/`@Data`, since those generate code that walks lazy JPA associations and cause the classic Lombok+JPA infinite-recursion/N+1 pitfalls.

## Verification performed

1. `./mvnw test` — all 6 tests pass (the 4 new repository tests, each against a real Testcontainers Postgres, plus the pre-existing smoke tests). `BackendApplicationTests` booting successfully with `ddl-auto=validate` confirms every migration matches its entity mapping exactly — a mismatch would fail startup.
2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` — all 5 migrations (`V1`–`V5`) applied cleanly against the real Docker Compose Postgres and the app booted successfully.
3. Implemented and verified as 5 separate, individually-tested commits (one per entity/step) on a single branch/PR, rather than one large commit — each step compiled, migrated, and passed its own test before the next one started.

One real gap found and fixed along the way:
- Spring Boot 4.x splits JPA's test-slice support (`@DataJpaTest`) into its own `spring-boot-starter-data-jpa-test` module (transitively pulling in `spring-boot-jdbc-test` for `@AutoConfigureTestDatabase`) — same modularization pattern hit in issue #2 with Flyway. Both classes also live in new packages (`org.springframework.boot.data.jpa.test.autoconfigure` / `org.springframework.boot.jdbc.test.autoconfigure`), not the pre-Boot-4 `org.springframework.boot.test.autoconfigure.orm.jpa` location.
