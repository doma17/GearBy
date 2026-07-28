CREATE TABLE category_review_flags (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    source VARCHAR(32) NOT NULL CHECK (source IN ('FEEDBACK', 'REJECTION', 'VALIDATION', 'MANUAL')),
    source_feedback_id UUID REFERENCES feedback(id) ON DELETE SET NULL,
    reason VARCHAR(500) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'RESOLVED')),
    assignee VARCHAR(200),
    resolution VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(200)
);

CREATE UNIQUE INDEX category_review_flags_open_store_reason_idx
    ON category_review_flags (store_id, reason) WHERE state = 'OPEN';
CREATE INDEX category_review_flags_queue_idx
    ON category_review_flags (state, store_id, assignee, created_at DESC);
