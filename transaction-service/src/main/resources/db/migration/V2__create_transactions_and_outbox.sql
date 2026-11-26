CREATE TABLE transactions
(
    id               UUID PRIMARY KEY,
    initiator_id     UUID           NOT NULL REFERENCES users (id),
    idempotency_key  VARCHAR(100)   NOT NULL,
    request_hash     VARCHAR(64)    NOT NULL,
    source_account   VARCHAR(34)    NOT NULL,
    target_account   VARCHAR(34)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL,
    description      VARCHAR(255),
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED')),
    rejection_reason VARCHAR(40),
    rejection_detail VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    -- idempotency is enforced by the database, not only by application code
    CONSTRAINT ux_transactions_idempotency UNIQUE (initiator_id, idempotency_key)
);

CREATE INDEX ix_transactions_initiator_created ON transactions (initiator_id, created_at DESC);

CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    message_key    VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000)
);

-- the publisher only ever scans unpublished rows
CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
