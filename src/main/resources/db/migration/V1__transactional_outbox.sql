CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    aggregate_id UUID NOT NULL,
    saga_id UUID NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(160) NULL,
    lease_until TIMESTAMPTZ NULL,
    published_at TIMESTAMPTZ NULL,
    last_error VARCHAR(1000) NULL
);

CREATE INDEX idx_outbox_claimable
    ON outbox_event (status, next_attempt_at, occurred_at);

CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
