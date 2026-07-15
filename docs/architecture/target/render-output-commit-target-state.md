# Render Output Commit — Target State

## Single Canonical Authority

```text
RenderOutputCommit — one record per RenderJob
UNIQUE(render_output_commit.render_job_id)

RenderOutputItem — one record per output role per commit
UNIQUE(render_output_item.output_commit_id, render_output_item.output_role)
```

## Target Sequence

```
execute() [NO @Transactional]
│
├── 1. claim [REQUIRES_NEW, short]
│   └── render_job: QUEUED → SELECTING_PROVIDER
│
├── 2. resolve/select [no transaction]
│   └── script resolution, provider selection
│
├── 3. persist selected_provider [short transaction]
│   └── render_job: SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING
│
├── 4. render [NO transaction]
│   └── FFmpeg execution (external process)
│
├── 5. render_output INSERT [short transaction]
│   └── INSERT INTO render_output (status=PENDING)
│   └── ON CONFLICT DO NOTHING (idempotent)
│
├── 6. blob write [NO transaction]
│   └── deterministic key: renders/{tenantId}/{jobId}/output.{format}
│   └── idempotent overwrite
│
├── 7. metadata commit [short transaction]
│   ├── StorageReference create/update
│   ├── Artifact create/update (READY)
│   ├── render_output: PENDING → COMMITTED
│   └── render_job: EXECUTING → COMPLETING
│
├── 8. publication commit [short transaction]
│   ├── FINAL_RENDER Product create (REGISTERED → READY)
│   ├── Billing consume (idempotent via render_output.id)
│   ├── render_job: COMPLETING → COMPLETED
│   └── lifecycle event
│
└── 9. failure at any point [REQUIRES_NEW]
    ├── render_output: → FAILED
    ├── render_job: → FAILED
    └── Product: not READY
```

## Target State Transitions

### RenderOutputCommit

```
PENDING → COMMITTED (success)
PENDING → FAILED (failure)
```

### RenderJob

```
QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED
                                                         ↓
                                                       FAILED
```

### Product

```
REGISTERED → READY (after render_output COMMITTED)
```

### Artifact

```
CREATED → READY (after blob committed)
```

## Target Database Constraints

```sql
-- One commit per RenderJob
UNIQUE(render_output_commit.render_job_id)

-- One item per output role per commit
UNIQUE(render_output_item.output_commit_id, render_output_item.output_role)

-- Billing idempotency
UNIQUE(reference_type, reference_id) ON billing_ledger_entry

-- Quota idempotency
UNIQUE(tenant_id, feature_code) ON quota_usage
```

## Target Blob Lifecycle

```text
Staging: Not needed (deterministic key)
Write: Idempotent overwrite per RenderJob
Ownership: render_output record
User-visible: Only after render_output COMMITTED
Orphan: None (deterministic key = safe replay)
```

## Target Replay Behavior

```text
Same RenderJob, same output:
- render_output INSERT: ON CONFLICT DO NOTHING (returns existing)
- blob write: idempotent overwrite (same key)
- StorageReference: upsert (same path)
- Artifact: upsert (same ID)
- Product: upsert (same render_job_id)
- Billing: upsert (same bill-{jobId})
```
