-- Orders, order lines, order events, order history and assignment tables
-- For Issue #23
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop. (Seeding at issue #25/#26)
-- Depends on 01, 02, 03 sql schemas

-- status is stored as one of the 9 OrderStatus names (domain model), even the ones Phase II does not use yet.
-- Technical Services only stores the current status. Which status can follow which is Domain (R-ORD-04).
-- courier, delivery address / coordinates and reject reason can be empty since Phase II does not always fill them.
-- There is no created_at here: when the order was placed is the time of its first event in order_events.
-- The total is not stored either, it is computed from the lines (it is only saved at the end, in order_history).
CREATE TABLE IF NOT EXISTS orders (
    id                    SERIAL PRIMARY KEY,
    customer_id           INTEGER      NOT NULL REFERENCES customers (user_id),
    restaurant_id         INTEGER      NOT NULL REFERENCES restaurants (id),
    courier_id            INTEGER      REFERENCES couriers (user_id),
    status                VARCHAR(20)  NOT NULL CHECK (status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY',
                              'PICKED_UP', 'DELIVERED', 'REJECTED', 'CANCELLED', 'REFUNDED')),
    special_instructions  VARCHAR(500),
    delivery_address      VARCHAR(255),
    delivery_latitude     DOUBLE PRECISION,
    delivery_longitude    DOUBLE PRECISION,
    reject_reason         VARCHAR(500)
);

-- One row per item in the order.
-- name, price and original_price are copied from the menu item when the order is placed,
-- so the order stays the same if the item is edited or removed later (#145).
CREATE TABLE IF NOT EXISTS order_lines (
    id              SERIAL PRIMARY KEY,
    order_id        INTEGER        NOT NULL REFERENCES orders (id),
    menu_item_id    INTEGER        NOT NULL REFERENCES menu_items (id),
    quantity        INTEGER        NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    price           DECIMAL(10, 2) NOT NULL,
    original_price  DECIMAL(10, 2)
);

-- Order State Transitions in the domain model, one row per status change (R-ORD-05).
-- id is the event id. previous_event_id points to the event before it, so the events of an order form a chain.
-- Placing the order is the first event: previous_event_id and previous_status are empty for it.
-- created_at is when the order entered new_status. The stage queues (placed, accepted, preparing, ready) are ordered by it (R-ORD-03).
CREATE TABLE IF NOT EXISTS order_events (
    id                 SERIAL PRIMARY KEY,
    order_id           INTEGER     NOT NULL REFERENCES orders (id),
    previous_event_id  INTEGER     REFERENCES order_events (id),
    previous_status    VARCHAR(20) CHECK (previous_status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY',
                           'PICKED_UP', 'DELIVERED', 'REJECTED', 'CANCELLED', 'REFUNDED')),
    new_status         VARCHAR(20) NOT NULL CHECK (new_status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY',
                           'PICKED_UP', 'DELIVERED', 'REJECTED', 'CANCELLED', 'REFUNDED')),
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Phase III. One row per order once it is finished, nothing writes to it in Phase II.
CREATE TABLE IF NOT EXISTS order_history (
    order_id       INTEGER PRIMARY KEY REFERENCES orders (id),
    final_status   VARCHAR(20) CHECK (final_status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY',
                       'PICKED_UP', 'DELIVERED', 'REJECTED', 'CANCELLED', 'REFUNDED')),
    total_charges  DECIMAL(10, 2)
);

-- Phase III. Links a courier to the order they deliver, nothing writes to it in Phase II.
-- An order can be offered to more than one courier, so it has its own id instead of using order_id.
CREATE TABLE IF NOT EXISTS assignments (
    id            SERIAL PRIMARY KEY,
    order_id      INTEGER NOT NULL REFERENCES orders (id),
    courier_id    INTEGER NOT NULL REFERENCES couriers (user_id),
    is_picked_up  BOOLEAN DEFAULT FALSE,
    is_delivered  BOOLEAN DEFAULT FALSE
);
