-- Shelters table (#233)
-- Runs for every startup but uses CREATE TABLE IF NOT EXISTS so it doesnt drop. Filled by SeedLoader from seed-config.json.
-- A shelter is a place an order can be donated to (#241), so it needs an address and coordinates for the delivery map.
-- The coordinates were looked up once by hand on openstreetmap.org, the app never geocodes while it runs.
CREATE TABLE IF NOT EXISTS shelters (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100)     NOT NULL,
    address    VARCHAR(255)     NOT NULL,
    latitude   DOUBLE PRECISION NOT NULL,
    longitude  DOUBLE PRECISION NOT NULL
);
