> [!CAUTION]
> **Status:** Quarantined and not accepted.
> This material MUST NOT be used as implementation authority.
> V5 remains blocked until document governance .1-.7 closes.
> Current canonical semantics are defined by the render-output contract candidate.
> See: [Canonical Contracts](../../governance/canonical-contracts/)

# Render Output Commit — Schema Proposal

## V5 Migration Requirements

### New Table: `render_output_commit`

```sql
CREATE TABLE render_output_commit (
    id                    TEXT PRIMARY KEY,
    tenant_id             TEXT NOT NULL,
    project_id            TEXT NOT NULL,
    render_job_id         TEXT NOT NULL,
    status                TEXT NOT NULL DEFAULT 'PENDING',
    
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
    CONSTRAINT fk_render_output_commit_job 
        FOREIGN KEY (render_job_id) REFERENCES render_job(id) ON DELETE RESTRICT,
    CONSTRAINT uq_render_output_commit_job 
        UNIQUE(render_job_id),
    CONSTRAINT ck_render_output_commit_status 
        CHECK (status IN ('PENDING', 'COMMITTED', 'FAILED'))
);

CREATE INDEX ix_render_output_commit_tenant ON render_output_commit(tenant_id);
CREATE INDEX ix_render_output_commit_status ON render_output_commit(status);
```

### New Table: `render_output_item`

```sql
CREATE TABLE render_output_item (
    id                    TEXT PRIMARY KEY,
    output_commit_id      TEXT NOT NULL,
    output_role           TEXT NOT NULL DEFAULT 'PRIMARY_VIDEO',
    
    -- Storage
    object_identity       TEXT,
    content_checksum_sha256 TEXT,
    content_size          BIGINT,
    media_type            TEXT,
    media_metadata_json   TEXT,
    
    -- References
    storage_reference_id  TEXT,
    artifact_id           TEXT,
    
    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT fk_render_output_item_commit 
        FOREIGN KEY (output_commit_id) REFERENCES render_output_commit(id) ON DELETE RESTRICT,
    CONSTRAINT uq_render_output_item_role 
        UNIQUE(output_commit_id, output_role),
    CONSTRAINT ck_render_output_item_role 
        CHECK (output_role IN ('PRIMARY_VIDEO', 'THUMBNAIL', 'SUBTITLE', 'HLS_MANIFEST', 'AUDIO', 'PREVIEW_PROXY'))
);

CREATE INDEX ix_render_output_item_commit ON render_output_item(output_commit_id);
```

### Modified Table: `render_job`

```sql
ALTER TABLE render_job ADD COLUMN idempotency_key TEXT;
ALTER TABLE render_job ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
CREATE UNIQUE INDEX uq_render_job_idempotency 
    ON render_job(idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
```

### Modified Table: `product`

```sql
-- Add unique constraint for RenderJob-based product uniqueness
CREATE UNIQUE INDEX uq_product_render_job_type 
    ON product(render_job_id, product_type) 
    WHERE render_job_id IS NOT NULL;
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

### render_output_commit

| Field | Type | Description |
|-------|------|-------------|
| id | TEXT PK | `roc-{uuid}` |
| tenant_id | TEXT | Tenant scope |
| project_id | TEXT | Project scope |
| render_job_id | TEXT FK UNIQUE | Parent RenderJob (one commit per job) |
| status | TEXT | PENDING → COMMITTED or FAILED |
| failure_code | TEXT | Error classification |
| failure_summary | TEXT | Safe error message |
| committed_at | TIMESTAMPTZ | Atomic "ready" marker |
| version | INTEGER | Optimistic locking |

### render_output_item

| Field | Type | Description |
|-------|------|-------------|
| id | TEXT PK | `roi-{uuid}` |
| output_commit_id | TEXT FK | Parent commit |
| output_role | TEXT | PRIMARY_VIDEO, THUMBNAIL, etc. |
| object_identity | TEXT | Deterministic object key |
| content_checksum_sha256 | TEXT | SHA-256 of actual bytes |
| content_size | BIGINT | Bytes |
| media_type | TEXT | MIME type |
| media_metadata_json | TEXT | Resolution, codec, etc. |
| storage_reference_id | TEXT FK | Links to StorageReference |
| artifact_id | TEXT FK | Links to Artifact |

## Idempotency Keys

| Entity | Key | Constraint |
|--------|-----|-----------|
| RenderOutputCommit | `render_job_id` | UNIQUE(render_job_id) ✅ |
| RenderOutputItem | `(output_commit_id, output_role)` | UNIQUE(output_commit_id, output_role) ✅ |
| RenderBillingRecord | `bill-{jobId}` | UNIQUE(job_id) ✅ |
| BillingLedger | `(reference_type, reference_id)` | PROPOSED UNIQUE |
| QuotaUsage | `(tenant_id, feature_code)` | PROPOSED UNIQUE |
