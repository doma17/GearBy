ALTER TABLE feedback DROP CONSTRAINT feedback_notification_status_check;
ALTER TABLE feedback
    ADD CONSTRAINT feedback_notification_status_check
    CHECK (notification_status IN ('NOT_REQUESTED', 'QUEUED', 'SENT', 'FAILED'));

CREATE TABLE feedback_notification_attempts (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL UNIQUE REFERENCES feedback(id) ON DELETE CASCADE,
    recipient_email VARCHAR(320) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);
