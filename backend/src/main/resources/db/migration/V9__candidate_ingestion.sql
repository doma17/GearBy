CREATE TABLE candidate_ingestion_provider_policy (
    id UUID PRIMARY KEY,
    provider_key VARCHAR(80) NOT NULL,
    approval_status VARCHAR(20) NOT NULL CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'BLOCKED')),
    approval_owner VARCHAR(200),
    reviewed_at TIMESTAMPTZ,
    approved_source_url VARCHAR(1000),
    allowed_fields TEXT NOT NULL,
    retention_rules TEXT NOT NULL,
    gate_version VARCHAR(120) NOT NULL,
    sample_precision_result_reference VARCHAR(500),
    sample_size INTEGER NOT NULL DEFAULT 0 CHECK (sample_size >= 0),
    region_count INTEGER NOT NULL DEFAULT 0 CHECK (region_count >= 0),
    precision_threshold NUMERIC(5, 2) NOT NULL CHECK (precision_threshold BETWEEN 0 AND 100),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(200) NOT NULL DEFAULT 'system',
    edited_by VARCHAR(200) NOT NULL DEFAULT 'system'
);

CREATE INDEX candidate_ingestion_provider_policy_active_idx
    ON candidate_ingestion_provider_policy (provider_key, active, gate_version);

CREATE TABLE candidate_ingestion_runs (
    id UUID PRIMARY KEY,
    provider_policy_id UUID NOT NULL REFERENCES candidate_ingestion_provider_policy(id),
    provider_key VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    requested_by VARCHAR(200) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'PARTIAL', 'FAILED', 'COMPLETED')),
    gate_version VARCHAR(120) NOT NULL,
    seen_count INTEGER NOT NULL DEFAULT 0 CHECK (seen_count >= 0),
    deduped_count INTEGER NOT NULL DEFAULT 0 CHECK (deduped_count >= 0),
    accepted_count INTEGER NOT NULL DEFAULT 0 CHECK (accepted_count >= 0),
    quarantined_count INTEGER NOT NULL DEFAULT 0 CHECK (quarantined_count >= 0),
    rejected_count INTEGER NOT NULL DEFAULT 0 CHECK (rejected_count >= 0),
    failed_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    error_code VARCHAR(120),
    error_summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(200) NOT NULL DEFAULT 'system',
    edited_by VARCHAR(200) NOT NULL DEFAULT 'system',
    UNIQUE (provider_key, idempotency_key)
);

CREATE INDEX candidate_ingestion_runs_status_idx
    ON candidate_ingestion_runs (status, requested_at DESC);

CREATE TABLE store_candidate_provenance (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES candidate_ingestion_runs(id),
    provider_key VARCHAR(80) NOT NULL,
    provider_record_id VARCHAR(200),
    dedup_key VARCHAR(500) NOT NULL,
    first_seen_run_id UUID NOT NULL REFERENCES candidate_ingestion_runs(id),
    last_seen_run_id UUID NOT NULL REFERENCES candidate_ingestion_runs(id),
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    normalized_name VARCHAR(300) NOT NULL,
    road_address VARCHAR(500),
    rounded_latitude NUMERIC(9, 6) CHECK (rounded_latitude BETWEEN -90 AND 90),
    rounded_longitude NUMERIC(9, 6) CHECK (rounded_longitude BETWEEN -180 AND 180),
    phone VARCHAR(40),
    industry_code VARCHAR(80),
    match_precedence VARCHAR(40) NOT NULL,
    match_status VARCHAR(40) NOT NULL CHECK (match_status IN (
        'NOT_EVALUATED',
        'NO_MATCH',
        'EXACT_PROVIDER_RECORD',
        'EXACT_NAME_ADDRESS',
        'EXACT_NAME_COORDINATES',
        'AMBIGUOUS',
        'RESOLVED_EXISTING',
        'RESOLVED_DRAFT'
    )),
    match_reason VARCHAR(1000),
    latest_item_outcome VARCHAR(40) NOT NULL CHECK (latest_item_outcome IN (
        'DRAFT_CREATED',
        'MATCHED_EXISTING',
        'DUPLICATE_SKIPPED',
        'QUARANTINED',
        'BLOCKED_BY_GATE',
        'REJECTED',
        'ITEM_FAILED',
        'RESOLVED'
    )),
    resolved_store_id UUID REFERENCES stores(id),
    payload_sha256_digest CHAR(64) NOT NULL CHECK (payload_sha256_digest ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(200) NOT NULL DEFAULT 'system',
    edited_by VARCHAR(200) NOT NULL DEFAULT 'system',
    CHECK (
        (match_status = 'NO_MATCH' AND latest_item_outcome IN ('DRAFT_CREATED', 'ITEM_FAILED')) OR
        (match_status = 'EXACT_PROVIDER_RECORD' AND latest_item_outcome = 'DUPLICATE_SKIPPED') OR
        (match_status IN ('EXACT_NAME_ADDRESS', 'EXACT_NAME_COORDINATES') AND latest_item_outcome = 'MATCHED_EXISTING') OR
        (match_status = 'AMBIGUOUS' AND latest_item_outcome = 'QUARANTINED') OR
        (match_status IN ('RESOLVED_EXISTING', 'RESOLVED_DRAFT') AND latest_item_outcome = 'RESOLVED') OR
        (match_status = 'NOT_EVALUATED' AND latest_item_outcome IN ('BLOCKED_BY_GATE', 'REJECTED'))
    )
);

CREATE UNIQUE INDEX store_candidate_provenance_provider_record_idx
    ON store_candidate_provenance (provider_key, provider_record_id)
    WHERE provider_record_id IS NOT NULL;

CREATE UNIQUE INDEX store_candidate_provenance_fallback_dedup_idx
    ON store_candidate_provenance (provider_key, dedup_key)
    WHERE provider_record_id IS NULL;

CREATE INDEX store_candidate_provenance_run_idx
    ON store_candidate_provenance (run_id, latest_item_outcome, match_status);
