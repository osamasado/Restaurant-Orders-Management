CREATE TABLE order_number_counter (
    id         BIGINT PRIMARY KEY,
    next_value INTEGER NOT NULL
);

INSERT INTO order_number_counter (id, next_value) VALUES (1, 1);

CREATE UNIQUE INDEX restaurant_order_order_number_unique
    ON restaurant_order (order_number)
    WHERE order_number IS NOT NULL;
