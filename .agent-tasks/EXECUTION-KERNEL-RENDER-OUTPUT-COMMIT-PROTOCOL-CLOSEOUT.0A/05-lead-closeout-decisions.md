# Lead Closeout Decisions

## 1. Commit Cardinality

```text
Decision: ONE_RENDERJOB_ONE_RENDEROUTPUTCOMMIT
```

One RenderJob maps to at most one RenderOutputCommit.

Multiple physical or logical outputs are modeled as child RenderOutputItems.

```text
UNIQUE(render_output_commit.render_job_id)
UNIQUE(render_output_item.output_commit_id, output_role)
```

## 2. Canonical State Set

```text
QUEUED
SELECTING_PROVIDER
PROVIDER_SELECTED
EXECUTING
COMPLETING
COMPLETED
FAILED
CANCELLED
```

FALLBACKING: EXCLUDED
RETRYING: EXCLUDED
Future retry: Creates new RenderJob

## 3. COMPLETING Semantics

```text
COMPLETING = Provider succeeded AND platform is performing canonical Render Output Commit Protocol
```

## 4. Deterministic Key Semantics

| Scenario | Behavior |
|----------|----------|
| Same RenderJob replay | Same key, idempotent overwrite |
| Same key + same checksum | Reuse existing object |
| Same key + different checksum | FAIL with deterministic-output conflict |
| Silent overwrite | FORBIDDEN |
| Blob success / DB failure | Object remains uncommitted, not user-visible |
| Process restart | Resume from durable DB/object facts |
| New retry attempt | New RenderJob ID, new key namespace |
| User visibility | RenderOutputCommit COMMITTED AND Product READY |

## 5. V5 Scope

Minimum V5 includes:
- render_output_commit table
- render_output_item table
- Product uniqueness constraint
- Billing/quota idempotency constraints
- render_job.updated_at
- Later transition CAS support

## 6. Finding Ownership

| Finding | Owner |
|---------|-------|
| Hard-coded Artifact metadata | PROTOCOL_IMPLEMENTATION |
| timeline:// URI | PROTOCOL_IMPLEMENTATION |
| URI hash as checksum | PROTOCOL_IMPLEMENTATION |
| Quota read-modify-write | V5_SCHEMA + PROTOCOL_IMPLEMENTATION |
| BillingLedger random UUID | V5_SCHEMA + PROTOCOL_IMPLEMENTATION |
| Later transitions without CAS | PROTOCOL_IMPLEMENTATION |
| Compensation service | PROTOCOL_IMPLEMENTATION (default-disable) |
| canRetry | REMOVE_AS_STALE_CONTRACT |
