# Database schema

Reflects the schema created by Flyway migrations `V1`–`V6` (`backend/src/main/resources/db/migration/`). This covers the menu domain modeled in issue #3 — `Order`/`Table`/`StaffAccount`/`Config` will be added here as later tickets introduce them.

```mermaid
erDiagram
    CATEGORY ||--o{ CATEGORY_TRANSLATION : has
    CATEGORY ||--o{ MEAL : contains
    MEAL ||--o{ MEAL_TRANSLATION : has
    MEAL ||--o{ MEAL_SIZE : has
    MEAL_TRANSLATION ||--o{ MEAL_TRANSLATION_INGREDIENT : lists
    MEAL_SIZE ||--o{ MEAL_SIZE_TRANSLATION : has
    MEAL_SIZE ||--o{ RECIPE : "used via"
    RAW_MATERIAL ||--o{ RECIPE : "used in"

    CATEGORY {
        bigint id PK
        int sort_order
    }
    CATEGORY_TRANSLATION {
        bigint id PK
        bigint category_id FK
        varchar language
        varchar name
    }
    MEAL {
        bigint id PK
        bigint category_id FK
        boolean available
    }
    MEAL_TRANSLATION {
        bigint id PK
        bigint meal_id FK
        varchar language
        varchar name
        text description
        text preparation_method
    }
    MEAL_TRANSLATION_INGREDIENT {
        bigint meal_translation_id FK
        int position
        varchar ingredient
    }
    MEAL_SIZE {
        bigint id PK
        bigint meal_id FK
        numeric price
    }
    MEAL_SIZE_TRANSLATION {
        bigint id PK
        bigint meal_size_id FK
        varchar language
        varchar label
    }
    RAW_MATERIAL {
        bigint id PK
        varchar name
        varchar unit
        numeric in_stock_quantity
        varchar supplier
    }
    RECIPE {
        bigint id PK
        bigint meal_size_id FK
        bigint raw_material_id FK
        numeric quantity
    }
```

## Notes

- **Translation tables** (`category_translation`, `meal_translation`, `meal_size_translation`) each carry a `language` column (`DE`/`EN`/`AR`) with a unique constraint on `(parent_id, language)`, per the project's i18n rule — content is stored per language rather than in nullable per-language columns, so a missing translation is a missing row, not a null.
- **`meal.available`** is a single flag (not per-size, not per-language).
- **`recipe`** links a `meal_size` (not `meal` directly) to a `raw_material`, per the proposal: *"Recipes link each meal size to its raw materials."*
- **`raw_material`** has no translation table — it's internal/back-of-house only, never shown to guests.
- **`meal_translation_ingredient`** is an `@ElementCollection` (an ordered list of plain strings per translation), not a shared/managed `Ingredient` entity like `raw_material` is.
