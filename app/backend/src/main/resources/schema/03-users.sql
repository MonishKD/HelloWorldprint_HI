-- Users, cusotmers, couriers and operators tables
-- Filled in by #24
-- Depends on restaurant to exist for now.

-- #25-#26  seedings

-- Every account. The role is stored with the user (R-REG-01).
-- email is unique ignoring case, see the index right after this table.
-- password_hash only holds the hashed password, the hashing is done by Spring Security (#28).
-- phone_number can be empty since Phase II does not use it.
CREATE TABLE IF NOT EXISTS users (
    id             SERIAL PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    phone_number   VARCHAR(20),
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'RESTAURANT', 'COURIER', 'OPERATOR'))
);

-- Makes the database itself refuse the same email in a different case (Bob@mail.com when bob@mail.com exists),
-- even if a user is added without going through UserRepository.
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users (LOWER(email));

-- One table per kind of user (Customer, Courier, Restaurant Personnel, Platform Operator).
-- user_id is the primary key and points to users, so a user has at most one row in each table.
-- Everything Phase II does not use can be empty (names, customer address, coordinates, courier status).

CREATE TABLE IF NOT EXISTS customers (
    user_id    INTEGER PRIMARY KEY REFERENCES users (id),
    name       VARCHAR(100),
    address    VARCHAR(255),
    latitude   DOUBLE PRECISION,
    longitude  DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS couriers (
    user_id    INTEGER PRIMARY KEY REFERENCES users (id),
    name       VARCHAR(100),
    latitude   DOUBLE PRECISION,
    longitude  DOUBLE PRECISION,
    status     VARCHAR(20) CHECK (status IN ('OFFLINE', 'IDLE', 'ASSIGNED', 'CARRYING'))
);

-- A restaurant account only sees its own restaurant, this row is the link. is_owner marks the owner.
CREATE TABLE IF NOT EXISTS restaurant_personnel (
    user_id        INTEGER PRIMARY KEY REFERENCES users (id),
    restaurant_id  INTEGER NOT NULL REFERENCES restaurants (id),
    is_owner       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS operators (
    user_id  INTEGER PRIMARY KEY REFERENCES users (id)
);
