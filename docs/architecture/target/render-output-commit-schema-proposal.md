# Render Output Commit — Schema Proposal

## V5 Migration Requirements

### New Table: `render_output`

```sql
CREATE TABLE render_output (
    id                    TEXT PRIMARY KEY,
    tenant_id             TEXT NOT NULL,
    project_id            TEXT NOT NULL,
    render_job_id         TEXT NOT NULL,
    output_type           TEXT NOT NULL DEFAULT 'FINAL_RENDER',
    status                TEXT NOT NULL DEFAULT 'PENDING',
    
    -- Storage
    staged_object_locator TEXT,
    committed_object_locator TEXT,
    content_checksum      TEXT,
    content_size          BIGINT,
    content_type          TEXT,
    
    -- References
    storage_reference_id  TEXT,
    artifact_id           TEXT,
    product_id            TEXT,
    
    -- Billing
    quota_operation_id    TEXT,
    billing_operation_id  TEXT,
    
    -- Failure tracking
    failure_code          TEXT,
    failure_summary       TEXT,
    
    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    committed_at          TIMESTAMPTZ,
    
    -- Versioning
    version               INTEGER NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT fk_render_output_job 
        FOREIGN KEY (render_job_id) REFERENCES render_job(id) ON DELETE RESTRICT,
    CONSTRAINT uq_render_output_job_type 
        UNIQUE(render_job_id, output_type),
    CONSTRAINT ck_render_output_status 
        CHECK (status IN ('PENDING', 'COMMITTED', 'FAILED'))
);

CREATE INDEX ix_render_output_tenant ON render_output(tenant_id);
CREATE INDEX ix_render_output_status ON render_output(status);
CREATE INDEX ix_render_output_job ON render_output(render_job_id);
```

### Modified Table: `render_job`

```sql
ALTER TABLE render_job ADD COLUMN idempotency_key TEXT;
CREATE UNIQUE INDEX uq_render_job_idempotency 
    ON render_job(idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
```

### Modified Table: `billing_ledger_entry`

```sql
-- Add unique constraint for idempotency
CREATE UNIQUE INDEX uq_billing_ledger_reference 
    ON billing_ledger_entry(reference_type, reference_id);
```

### Modified Table: `quota_usage`

```sql
-- Add unique constraint for idempotency
CREATE UNIQUE INDEX uq_quota_usage_tenant_feature 
    ON quota_usage(tenant_id, feature_code);
```

## Field Descriptions

### render_output

| Field | Type | Description |
|-------|------|-------------|
| id | TEXT PK | `ro-{uuid}` |
| tenant_id | TEXT | Tenant scope |
| project_id | TEXT | Project scope |
| render_job_id | TEXT FK | Parent RenderJob |
| output_type | TEXT | `FINAL_RENDER` (extensible) |
| status | TEXT | PENDING → COMMITTED or FAILED |
| staged_object_locator | TEXT | Pre-commit blob path |
| committed_object_locator | TEXT | Final blob path |
| content_checksum | TEXT | SHA-256 of actual bytes |
| content_size | BIGINT | Bytes |
| content_type | TEXT | MIME type |
| storage_reference_id | TEXT FK | Links to StorageReference |
| artifact_id | TEXT FK | Links to Artifact |
| product_id | TEXT FK | Links to Product |
| quota_operation_id | TEXT | Links to quota operation |
| billing_operation_id | TEXT | Links to billing ledger |
| failure_code | TEXT | Error classification |
| failure_summary | TEXT | Safe error message |
| committed_at | TIMESTAMPTZ | Atomic "ready" marker |
| version | INTEGER | Optimistic locking |

## Idempotency Keys

| Entity | Key | Constraint |
|--------|-----|-----------|
| RenderOutputCommit | `(render_job_id, output_type)` | UNIQUE |
| RenderBillingRecord | `bill-{jobId}` | UNIQUE(job_id) ✅ |
| BillingLedger | `(reference_type, reference_id)` | PROPOSED UNIQUE |
| QuotaUsage | `(tenant_id, feature_code)` | PROPOSED UNIQUE |
