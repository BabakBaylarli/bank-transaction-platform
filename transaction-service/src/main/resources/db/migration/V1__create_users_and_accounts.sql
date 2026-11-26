CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ux_users_username ON users (LOWER(username));

CREATE TABLE accounts
(
    id         UUID PRIMARY KEY,
    number     VARCHAR(34)    NOT NULL UNIQUE,
    owner_id   UUID           NOT NULL REFERENCES users (id),
    currency   VARCHAR(3)     NOT NULL CHECK (currency IN ('AZN', 'USD', 'EUR')),
    -- the database is the last line of defence: a balance can never become negative
    balance    NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_accounts_owner ON accounts (owner_id);
