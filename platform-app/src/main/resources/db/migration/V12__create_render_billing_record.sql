-- Billing record table for render job cost tracking
CREATE TABLE render_billing_record (
    id VARCHAR(128) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    estimated_cost DOUBLE NOT NULL DEFAULT 0,
    actual_cost DOUBLE NOT NULL DEFAULT 0,
    usage_seconds BIGINT NOT NULL DEFAULT 0,
    provider_id VARCHAR(64),
    output_size_bytes BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ESTIMATED',
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    
    CONSTRAINT fk_billing_job FOREIGN KEY (job_id) REFERENCES render_job(id)
);

CREATE UNIQUE INDEX ix_billing_job_id ON render_billing_record(job_id);
CREATE INDEX ix_billing_tenant_id ON render_billing_record(tenant_id);
CREATE INDEX ix_billing_status ON render_billing_record(status);
