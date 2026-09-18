# Database schema

Reflects the schema created by Flyway migrations `V1`–`V11` (`backend/src/main/resources/db/migration/`). This covers the menu domain modeled in issue #3, the `Table`/`StaffAccount`/`Config` entities modeled in issue #4, and the `Order`/`OrderItem`/`OrderStatusHistory` entities modeled in issue #9 — the order *state machine* itself (legal transitions, order-number generation, price/tax calculation) is issues #10–#12, not yet reflected here.

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
    CONFIG ||--o{ CONFIG_PAYMENT_METHOD : enables

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
    RESTAURANT_TABLE {
        bigint id PK
        varchar table_number
        varchar room
        int seats
        varchar paired_device_id
        timestamptz last_seen_at
    }
    STAFF_ACCOUNT {
        bigint id PK
        varchar name
        varchar role
        varchar pin_hash
        timestamptz last_seen_at
    }
    CONFIG {
        bigint id PK
        varchar currency_code
        varchar currency_symbol
        varchar symbol_position
        numeric tax_rate
        varchar default_language
    }
    CONFIG_PAYMENT_METHOD {
        bigint config_id FK
        varchar payment_method
    }
    RESTAURANT_TABLE ||--o{ RESTAURANT_ORDER : seats
    RESTAURANT_ORDER ||--o{ ORDER_ITEM : has
    RESTAURANT_ORDER ||--o{ ORDER_STATUS_HISTORY : has
    STAFF_ACCOUNT ||--o{ ORDER_STATUS_HISTORY : records

    RESTAURANT_ORDER {
        bigint id PK
        int order_number
        bigint table_id FK
        varchar status
        timestamptz placed_at
        varchar payment_method
    }
    ORDER_ITEM {
        bigint id PK
        bigint order_id FK
        varchar name
        varchar size
        int quantity
        numeric unit_price
        text note
    }
    ORDER_STATUS_HISTORY {
        bigint id PK
        bigint order_id FK
        varchar status
        timestamptz changed_at
        bigint staff_account_id FK
    }
```

## Notes

- **Translation tables** (`category_translation`, `meal_translation`, `meal_size_translation`) each carry a `language` column (`DE`/`EN`/`AR`) with a unique constraint on `(parent_id, language)`, per the project's i18n rule — content is stored per language rather than in nullable per-language columns, so a missing translation is a missing row, not a null.
- **`meal.available`** is a single flag (not per-size, not per-language).
- **`recipe`** links a `meal_size` (not `meal` directly) to a `raw_material`, per the proposal: *"Recipes link each meal size to its raw materials."*
- **`raw_material`** has no translation table — it's internal/back-of-house only, never shown to guests.
- **`meal_translation_ingredient`** is an `@ElementCollection` (an ordered list of plain strings per translation), not a shared/managed `Ingredient` entity like `raw_material` is.
- **`restaurant_table`**, not `table` — the `Table` entity name collides with the SQL reserved word `TABLE`, so it's explicitly mapped to a differently-named table.
- **`staff_account.role`** is enforced only via the Java enum (`EnumType.STRING`), no DB `CHECK` constraint — same precedent as `language`.
- **`config`** is a singleton table with no DB-level enforcement (e.g. no `CHECK (id = 1)`) — the `V9__config.sql` migration seeds exactly one row and callers are expected to fetch it by convention.
- **`config_payment_method`** is an `@ElementCollection` of an enum (`Set<PaymentMethod>`), keyed by `(config_id, payment_method)` — unordered, unlike `meal_translation_ingredient`'s ordered list.
- **`restaurant_order`**, not `order` — same reserved-SQL-keyword problem as `restaurant_table`.
- **`order_item`** has **no FK back to `meal`/`meal_size` at all** — `name`/`size`/`unit_price` are copied text/numbers at order time (see `OrderItem.snapshotFrom`), never referenced live, so a later menu edit can never rewrite a historical order.
- **`order_status_history`** is one row per status the order *reached* (not a from/to pair — the previous state is just the prior row, read back in chronological order via `@OrderBy("changedAt ASC")`). `staff_account_id` is nullable: a guest submitting their own draft has no staff member behind that transition.
- **`order_number`** is nullable — assigning it is issue #11's concurrency-safe generator, not modeled yet.
