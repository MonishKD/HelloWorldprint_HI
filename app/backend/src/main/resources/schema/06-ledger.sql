-- Ledger table, one row per money entry of a finished order (#236, R-PRC-03, R-PRC-04, R-PRC-05)
-- LedgerService (#261) writes a row when an order ends: CHARGE (delivered), NO_CHARGE (rejected or cancelled).
-- A refund (#266) is a new REFUND row, the CHARGE row is never changed. That is why the key is id and not order_id:
-- the same order can have a CHARGE and a REFUND. Rows are only ever inserted, never updated or deleted.
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop.
-- Depends on 04 (orders) sql schema

-- The columns are the fields of LedgerEntry (domain.pricing), plus id and created_at.
-- Money columns are in cents, like LedgerEntry, so nothing is rounded. They are 0 for NO_CHARGE.
-- placed_at and finished_at come from the order's first and last events. created_at is when the row was saved.
-- payment_reference is the Stripe payment id (#259), empty when nothing was charged.
CREATE TABLE IF NOT EXISTS ledger_entries (
    id                 SERIAL PRIMARY KEY,
    order_id           INTEGER      NOT NULL REFERENCES orders (id),
    entry_type         VARCHAR(20)  NOT NULL CHECK (entry_type IN ('CHARGE', 'REFUND', 'NO_CHARGE')),
    final_status       VARCHAR(20)  NOT NULL CHECK (final_status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY',
                           'PICKED_UP', 'DELIVERED', 'REJECTED', 'CANCELLED', 'REFUNDED')),
    subtotal           INTEGER      NOT NULL,
    delivery_fee       INTEGER      NOT NULL,
    service_fee        INTEGER      NOT NULL,
    surcharge          INTEGER      NOT NULL,
    amount             INTEGER      NOT NULL,
    placed_at          TIMESTAMP    NOT NULL,
    finished_at        TIMESTAMP    NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_reference  VARCHAR(255)
);
