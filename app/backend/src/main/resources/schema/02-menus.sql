-- Restaurant Menu and Menu Items
-- For Issue #24
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop. This would be our default setting so that it saves what the local computers of devs have setup instead of restarting from scratch all the time. (Seeding at issue #25/#26)
-- Depends on 01 (restaurants).

-- One menu per restaurant, so restaurant_id is UNIQUE.
CREATE TABLE IF NOT EXISTS menus (
    id             SERIAL PRIMARY KEY,
    restaurant_id  INTEGER NOT NULL UNIQUE REFERENCES restaurants (id)
);

-- removed = TRUE hides the item from the menu without deleting it, so old order lines still point to it (#145).
-- original_price can be empty, it is only set when the item is on discount.
CREATE TABLE IF NOT EXISTS menu_items (
    id              SERIAL PRIMARY KEY,
    menu_id         INTEGER       NOT NULL REFERENCES menus (id),
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(500),
    price           DECIMAL(10, 2) NOT NULL,
    original_price  DECIMAL(10, 2),
    status          VARCHAR(20)   NOT NULL CHECK (status IN ('AVAILABLE', 'SOLD_OUT')),
    removed         BOOLEAN       NOT NULL DEFAULT FALSE
);
