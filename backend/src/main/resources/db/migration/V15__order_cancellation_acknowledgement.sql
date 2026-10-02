ALTER TABLE restaurant_order
    ADD COLUMN cancellation_acknowledged_at TIMESTAMPTZ,
    ADD COLUMN cancellation_acknowledged_by BIGINT REFERENCES staff_account (id);