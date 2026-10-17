-- Command log table, one row per command attempt: successes, refusals and errors (#142, R-DASH-06)
-- CommandInvoker (#68) saves every attempt here and the console feed (#140) reads them back.
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop.
-- Depends on 03 (users) and 04 (order_events) sql schemas

-- The columns are the fields of CommandLog (application/ord), plus id and created_at.
-- Only the latest 1,000 rows are kept. The deleting is done by CommandLogRepository when it saves, not here.
-- user_id and role come from the Session. They are empty when there was no valid session (401): we don't know who sent the command.
-- status and status_code are what the client got back (200 OK, 403 FORBIDDEN, 409 CONFLICT...).
-- status_message is TEXT because it can come from an error, so we don't control how long it is.
-- event_id is the order event the command made, only filled when it changed an order's status (R-ORD-05).
CREATE TABLE IF NOT EXISTS command_log (
    id              SERIAL PRIMARY KEY,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_name    VARCHAR(100) NOT NULL,
    user_id         INTEGER      REFERENCES users (id),
    role            VARCHAR(20)  CHECK (role IN ('CUSTOMER', 'RESTAURANT', 'COURIER', 'OPERATOR')),
    result          VARCHAR(10)  NOT NULL CHECK (result IN ('SUCCESS', 'REFUSED', 'ERROR')),
    status          INTEGER      NOT NULL,
    status_code     VARCHAR(50)  NOT NULL,
    status_message  TEXT,
    event_id        INTEGER      REFERENCES order_events (id)
);
