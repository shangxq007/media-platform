-- Safe preflight report persistence (DEV_PREVIEW_EPHEMERAL_ONLY)
-- Runtime persistence is NOT_IMPLEMENTED. This schema is for future use only.
CREATE TABLE ingest_preflight_safe_report_records (
    -- Identity/scope
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    raw_media_product_id VARCHAR(255) NOT NULL,
    upload_attempt_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    lifecycle_state VARCHAR(50) NOT NULL DEFAULT 'RECORDED',

    -- Mode/access/retention
    persistence_mode VARCHAR(50) NOT NULL DEFAULT 'DEV_PREVIEW_EPHEMERAL_ONLY',
    access_scope VARCHAR(50) NOT NULL DEFAULT 'DEV_ONLY',
    retention_days INT NOT NULL DEFAULT 7,
    report_only_mode BOOLEAN NOT NULL DEFAULT true,
    fail_open BOOLEAN NOT NULL DEFAULT true,

    -- Safe report summary
    overall_decision VARCHAR(50) NOT NULL,
    warning_count INT NOT NULL DEFAULT 0,
    finding_count INT NOT NULL DEFAULT 0,
    reject_candidate_count INT NOT NULL DEFAULT 0,
    declared_mime VARCHAR(255),
    detected_mime VARCHAR(255),
    mime_mismatch BOOLEAN NOT NULL DEFAULT false,
    content_type_confidence DOUBLE PRECISION,
    duration_ms BIGINT,
    width INT,
    height INT,
    container_format VARCHAR(100),
    video_codec VARCHAR(100),
    audio_codec VARCHAR(100),
    has_video BOOLEAN NOT NULL DEFAULT false,
    has_audio BOOLEAN NOT NULL DEFAULT false,

    -- Safe detector summary
    tika_detector_success BOOLEAN NOT NULL DEFAULT false,
    ffprobe_detector_success BOOLEAN NOT NULL DEFAULT false,
    detector_warning_codes JSONB,

    -- Safe policy result
    policy_profile VARCHAR(100),
    policy_mode VARCHAR(50) NOT NULL DEFAULT 'REPORT_ONLY',
    policy_decision VARCHAR(50) NOT NULL,
    policy_finding_count INT NOT NULL DEFAULT 0,
    policy_reject_candidate_count INT NOT NULL DEFAULT 0,
    policy_user_safe_message_codes JSONB,
    policy_finding_codes JSONB,
    upload_continues BOOLEAN NOT NULL DEFAULT true,
    blocking BOOLEAN NOT NULL DEFAULT false,

    -- Lifecycle/audit
    redacted_at TIMESTAMP,
    expired_at TIMESTAMP,
    deleted_at TIMESTAMP,
    schema_version INT NOT NULL DEFAULT 1,

    -- Constraints
    CONSTRAINT chk_retention_days CHECK (retention_days BETWEEN 1 AND 7),
    CONSTRAINT chk_access_scope CHECK (access_scope = 'DEV_ONLY'),
    CONSTRAINT chk_persistence_mode CHECK (persistence_mode = 'DEV_PREVIEW_EPHEMERAL_ONLY'),
    CONSTRAINT chk_policy_decision CHECK (policy_decision <> 'REJECT'),
    CONSTRAINT chk_blocking CHECK (blocking = false),
    CONSTRAINT chk_upload_continues CHECK (upload_continues = true),
    CONSTRAINT chk_created_at CHECK (created_at IS NOT NULL),
    CONSTRAINT chk_expires_at CHECK (expires_at IS NOT NULL)
);

-- Indexes for DEV_ONLY diagnostics queries
CREATE INDEX idx_preflight_safe_tenant_created ON ingest_preflight_safe_report_records(tenant_id, project_id, created_at DESC);
CREATE INDEX idx_preflight_safe_tenant_product ON ingest_preflight_safe_report_records(tenant_id, project_id, raw_media_product_id);
CREATE INDEX idx_preflight_safe_expires ON ingest_preflight_safe_report_records(expires_at);
CREATE INDEX idx_preflight_safe_lifecycle ON ingest_preflight_safe_report_records(lifecycle_state, expires_at);
CREATE INDEX idx_preflight_safe_policy_decision ON ingest_preflight_safe_report_records(policy_decision);
CREATE INDEX idx_preflight_safe_overall_decision ON ingest_preflight_safe_report_records(overall_decision);
