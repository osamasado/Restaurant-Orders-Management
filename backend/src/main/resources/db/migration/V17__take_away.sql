-- Take-away orders are placed against one reserved table, so everything that reads order.table (kitchen, hall board,
-- admin Orders, history) keeps working. It is flagged, can never be paired, edited or deleted, and is not listed in
-- Tables & devices. Screens show a translated "Take away" label for it instead of a table number.
ALTER TABLE restaurant_table ADD COLUMN take_away BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one take-away table.
CREATE UNIQUE INDEX restaurant_table_one_take_away ON restaurant_table (take_away) WHERE take_away;

-- The number is internal (screens show the label, not this text); it is spelled so no real table gets it by accident.
INSERT INTO restaurant_table (table_number, room, seats, take_away) VALUES ('__TAKE_AWAY__', 'Take away', 0, TRUE);

-- A guest follows their own order with a secret handed out when it is placed (only its hash is stored). Orders placed
-- before this migration have none and keep following the paired device's code.
ALTER TABLE restaurant_order ADD COLUMN access_token_hash VARCHAR(64);
