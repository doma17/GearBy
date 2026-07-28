CREATE TABLE correction_rules (
    id UUID PRIMARY KEY,
    normalized_source VARCHAR(120) NOT NULL UNIQUE,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('CATEGORY', 'STORE')),
    target VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE feedback (
    id UUID PRIMARY KEY,
    store_id UUID REFERENCES stores(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CORRECTION', 'GENERAL', 'NEW_STORE')),
    content VARCHAR(2000) NOT NULL,
    reply_email VARCHAR(320),
    contact_consent BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX feedback_store_idx ON feedback (store_id, submitted_at DESC);

INSERT INTO correction_rules (id, normalized_source, target_type, target)
VALUES ('33333333-3333-3333-3333-333333333333', '백패킨', 'CATEGORY', 'BACKPACKING');
