ALTER TABLE feedback
    ADD COLUMN resolution_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (resolution_status IN ('PENDING', 'RESOLVED', 'REJECTED')),
    ADD COLUMN resolution_summary VARCHAR(1000),
    ADD COLUMN notification_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED' CHECK (notification_status IN ('NOT_REQUESTED', 'QUEUED')),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by VARCHAR(200);

CREATE INDEX feedback_resolution_idx ON feedback (resolution_status, submitted_at DESC);
