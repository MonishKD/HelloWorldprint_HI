-- Party mode tables (phase4 extra feature): friends order together from one restaurant, each pays their own part.
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop.
-- Depends on 01 (restaurants), 02 (menu_items), 03 (customers) and 04 (orders) sql schemas.

-- One row per party. code is what friends type, scan (QR) or open (share link) to join.
-- status: GATHERING (people add items and get ready) -> PAYING (host locked it) -> PLACED, or CANCELLED.
-- mode is set when the host locks: ORDINARY (each pays their items) or ROULETTE (one random member pays everything).
-- The fare columns are frozen when the host locks, so the amounts people pay can't move after that.
-- order_id is the real order placed under the host's name once everyone paid.
-- cancel_reason says why a cancelled party ended (who cancelled it, or why the order could not be placed).
CREATE TABLE IF NOT EXISTS parties (
    id                  SERIAL PRIMARY KEY,
    code                VARCHAR(6)   NOT NULL UNIQUE,
    restaurant_id       INTEGER      NOT NULL REFERENCES restaurants (id),
    host_id             INTEGER      NOT NULL REFERENCES customers (user_id),
    delivery_address    VARCHAR(255),
    instructions        VARCHAR(500),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('GATHERING', 'PAYING', 'PLACED', 'CANCELLED')),
    mode                VARCHAR(20)  CHECK (mode IN ('ORDINARY', 'ROULETTE')),
    roulette_payer_id   INTEGER      REFERENCES customers (user_id),
    subtotal_cents      BIGINT,
    delivery_fee_cents  BIGINT,
    service_fee_cents   BIGINT,
    surcharge_cents     BIGINT,
    order_id            INTEGER      REFERENCES orders (id),
    cancel_reason       VARCHAR(300),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One row per person in a party (the host too).
-- vote is what they want once they are ready: ORDINARY or ROULETTE.
-- share_cents is what they pay, set when the host locks. payment_intent_id is their own Stripe hold,
-- captured on delivery and released on cancel/reject like a normal order's (PaymentService).
CREATE TABLE IF NOT EXISTS party_members (
    party_id           INTEGER      NOT NULL REFERENCES parties (id),
    customer_id        INTEGER      NOT NULL REFERENCES customers (user_id),
    is_ready           BOOLEAN      NOT NULL DEFAULT FALSE,
    vote               VARCHAR(20)  CHECK (vote IN ('ORDINARY', 'ROULETTE')),
    share_cents        BIGINT       NOT NULL DEFAULT 0,
    payment_intent_id  VARCHAR(255),
    is_paid            BOOLEAN      NOT NULL DEFAULT FALSE,
    joined_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (party_id, customer_id)
);

-- What each member added: one row per member per menu item, so adding the same item again only changes the quantity.
-- The item's name and price are read from menu_items, the same way the cart shows them.
-- price_cents is copied from the menu when the host locks, so the locked order keeps the prices people pay.
CREATE TABLE IF NOT EXISTS party_lines (
    party_id      INTEGER  NOT NULL REFERENCES parties (id),
    customer_id   INTEGER  NOT NULL REFERENCES customers (user_id),
    menu_item_id  INTEGER  NOT NULL REFERENCES menu_items (id),
    quantity      INTEGER  NOT NULL CHECK (quantity > 0),
    price_cents   BIGINT,
    added_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (party_id, customer_id, menu_item_id)
);
