-- In production this table is created by transaction-service. Tests of processing-service run alone,
-- so they create the same structure here.
CREATE TABLE IF NOT EXISTS public.accounts
(
    id         UUID PRIMARY KEY,
    number     VARCHAR(34)    NOT NULL UNIQUE,
    owner_id   UUID           NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    balance    NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL
);
