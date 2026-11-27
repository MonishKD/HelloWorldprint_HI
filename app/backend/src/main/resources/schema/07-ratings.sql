-- Ratings table, one row per rated order (#324, R-RVW-01, R-RVW-02)
-- A customer rates the restaurant and the courier of their own delivered order, once.
-- The UNIQUE order_id is what stops a double click from saving two ratings.
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop.
-- Depends on 04 (orders) sql schema
CREATE TABLE IF NOT EXISTS ratings (
    id                SERIAL PRIMARY KEY,
    order_id          INTEGER   NOT NULL UNIQUE REFERENCES orders (id),
    customer_id       INTEGER   NOT NULL REFERENCES customers (user_id),
    restaurant_id     INTEGER   NOT NULL REFERENCES restaurants (id),
    courier_id        INTEGER   NOT NULL REFERENCES couriers (user_id),
    restaurant_stars  INTEGER   NOT NULL,
    courier_stars     INTEGER   NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
