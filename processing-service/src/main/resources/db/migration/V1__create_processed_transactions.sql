CREATE TABLE processed_transactions
(
    transaction_id   UUID PRIMARY KEY,
    source_account   VARCHAR(34)    NOT NULL,
    target_account   VARCHAR(34)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('COMPLETED', 'REJECTED')),
    rejection_reason VARCHAR(40),
    rejection_detail VARCHAR(255),
    processed_at     TIMESTAMPTZ    NOT NULL
);

-- supports the velocity and daily-limit fraud rules
CREATE INDEX ix_processed_source_time ON processed_transactions (source_account, processed_at);
