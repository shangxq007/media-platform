-- RenderJob Lifecycle Events
-- Durable event history for diagnostics and operational visibility

CREATE TABLE render_job_lifecycle_events (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    render_job_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status_from VARCHAR(32),
    status_to VARCHAR(32),
    worker_id VARCHAR(128),
    attempt INT DEFAULT 0,
    retry_count INT DEFAULT 0,
    recovery_count INT DEFAULT 0,
    output_product_id VARCHAR(64),
    reason_code VARCHAR(64),
    reason VARCHAR(512),
    retryable BOOLEAN DEFAULT FALSE,
    next_retry_at TIMESTAMP,
    duration_ms BIGINT,
    event_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    payload_json TEXT,
    source VARCHAR(64) DEFAULT 'worker'
);

-- Index for job event history
CREATE INDEX idx_lifecycle_events_job ON render_job_lifecycle_events(render_job_id, event_time);

-- Index for project event queries
CREATE INDEX idx_lifecycle_events_project ON render_job_lifecycle_events(project_id, render_job_id, event_time);

-- Index for tenant queries
CREATE INDEX idx_lifecycle_events_tenant ON render_job_lifecycle_events(tenant_id, project_id, event_time);

-- Index for event type filtering
CREATE INDEX idx_lifecycle_events_type ON render_job_lifecycle_events(event_type, event_time);

-- Index for worker queries
CREATE INDEX idx_lifecycle_events_worker ON render_job_lifecycle_events(worker_id, event_time);
