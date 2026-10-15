-- Restaurant table
-- For Issue #24
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop. This would be our default setting so that it saves what the local computers of devs have setup instead of restarting from scratch all the time. (Seeding at issue #25/#26)
-- SERIAL makes the id go up by itself (1, 2, 3...).
-- status is stored as OPEN, PAUSED or CLOSED, same as the Java enum names. The rules for them are Domain (R-ORD-02).
-- cuisine, address and coordinates can be empty since restaurant sign-up (#143) may not give them yet.

CREATE TABLE IF NOT EXISTS restaurants (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'PAUSED', 'CLOSED')),
    latitude   DOUBLE PRECISION,
    longitude  DOUBLE PRECISION,
    cuisine    VARCHAR(50),
    address    VARCHAR(255)
);
