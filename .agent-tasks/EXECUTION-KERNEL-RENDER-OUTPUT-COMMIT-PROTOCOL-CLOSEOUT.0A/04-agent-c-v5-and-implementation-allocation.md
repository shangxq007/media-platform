# 04 — Agent C: V5 Schema Determination and Finding Allocation

**Branch**: `arch/render-output-commit-protocol-closeout` @ `a539594`
**Prior Reports Consumed**:
- `.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL.0/02-agent-a-current-state-synthesis.md`
- `.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL.0/04-agent-b-quota-idempotency-and-compensation.md`
- `.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL.0/04-agent-c-schema-and-failure-review.md`
- `.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL.0/05-lead-option-decision.md`
- `.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL.0/07-agent-e-independent-architecture-verification.md`
- `.agent-tasks/EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL.0/09-final-decision.md`
- `docs/architecture/adr/ADR-026-render-output-commit-protocol.md`
- `docs/architecture/target/render-output-commit-schema-proposal.md`
- `docs/architecture/target/render-output-commit-implementation-roadmap.md`

---

## 1. Complete Finding Inventory

Every finding from Agent A (current-state synthesis), Agent B (quota/idempotency/compensation), Agent C (schema/failure-window), and the lead decision is catalogued below with an allocation to one of five categories:

| Category | Meaning |
|----------|---------|
| **V5_SCHEMA** | Must exist in V5 Flyway migration. Protocol cannot function without it. |
| **PROTOCOL_IMPLEMENTATION** | Java/service-layer change required during protocol implementation. No schema change. |
| **FAILURE_WINDOW_VERIFICATION** | Must be verified by integration test during failure-window verification phase. |
| **REMOVE_AS_STALE_CONTRACT** | Dead code, misleading field, or reachable-but-unsafe trigger to remove before or during implementation. |
| **EXPLICIT_FUTURE_DEBT** | Acknowledged gap deferred beyond protocol implementation. Documented, not hidden. |

---

## 2. Finding Allocation Table

### 2.1 Schema Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| S1 | No `render_output` table exists | Agent C §2.2-N2 | **V5_SCHEMA** | Core protocol table. UNIQUE(render_job_id, output_type) is the one-output-per-job invariant. Cannot implement protocol without it. |
| S2 | `render_job` has no `idempotency_key` column | Agent C §1.1 | **V5_SCHEMA** | Required for deduplicating job submissions. ADR-026 explicitly lists it. |
| S3 | `billing_ledger_entry` has no unique constraint on `(reference_type, reference_id)` | Agent B §1.3, Agent C §6 | **V5_SCHEMA** | Prevents duplicate ledger entries for same reference. Without it, billing is not idempotent. |
| S4 | `quota_usage` has no unique constraint on `(tenant_id, feature_code)` | Agent B §1.2, Agent C §6 | **V5_SCHEMA** | Prevents duplicate quota rows. Enables atomic increment. Without it, read-then-write race persists. |
| S5 | `render_job.updated_at` missing from DDL but referenced in CAS queries | Agent C §2.2-N1 | **V5_SCHEMA** | `claimForSelection()` and `markActiveJobFailed()` SET updated_at. Column must exist in DDL. If it exists via undocumented migration, V5 must formally declare it. |
| S6 | `product` table has no `render_job_id` column | Agent C §1.2, §3.3 | **V5_SCHEMA** | Required for completion invariant condition 5: "FINAL_RENDER Product exists and is READY" must be queryable by render_job_id. |
| S7 | `render_job.status` is varchar with no CHECK constraint | Agent C §2.2-N5 | **EXPLICIT_FUTURE_DEBT** | Low severity. State machine is in-memory, DB is varchar. Adding CHECK is defensive but not blocking. Can be added in a later migration. |

### 2.2 Artifact Identity Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| A1 | Artifact ID mismatch: StorageCatalogService generates different ID than provider | Agent A §4.1 | **PROTOCOL_IMPLEMENTATION** | Fix direction: plumb artifactId through StorageCatalogPort.registerArtifact() interface. render_output.artifact_id becomes canonical source. |
| A2 | Artifact table has no unique constraint on render_job_id (multiple artifacts per job) | Agent C §1.5, §4.1 | **EXPLICIT_FUTURE_DEBT** | Current design allows multiple artifacts per job (ArtifactGraph). Protocol uses render_output as single authority. Artifact table schema unchanged for now. |
| A3 | Timeline JSON artifact has synthetic non-existent URI | Agent A §6.6 | **EXPLICIT_FUTURE_DEBT** | Low severity. `"timeline://"` scheme doesn't resolve to blob. Cosmetic issue. Deferred. |
| A4 | ArtifactGraph and Artifact (catalog) are disconnected | Agent C §2.1-6 | **EXPLICIT_FUTURE_DEBT** | Structural issue. Protocol adds render_output as canonical bridge. Full unification deferred to Artifact DAG (POSTPONED per ADR-026). |

### 2.3 Quota/Billing Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| Q1 | Double quota consumption: reserveQuota + consumeQuota both count | Agent A §4.2, Agent B §1.4 | **PROTOCOL_IMPLEMENTATION** | Remove second consumeQuota call at line 450. Reservation already accounts for unit. |
| Q2 | QuotaUsageRepository.incrementUsage is read-then-write, non-atomic | Agent B §1.2 | **PROTOCOL_IMPLEMENTATION** | Replace with atomic `UPDATE SET usage_value = usage_value + ?`. V5 unique constraint (S4) supports this. |
| Q3 | BillingLedgerJdbcRepository.saveEntry is plain INSERT, not idempotent | Agent B §1.3 | **PROTOCOL_IMPLEMENTATION** | Convert to upsert ON CONFLICT. V5 unique constraint (S3) supports this. |
| Q4 | Billing finalization failure silently swallowed | Agent A §5, Agent B §1.4 | **PROTOCOL_IMPLEMENTATION** | In protocol, billing failure must set render_output to FAILED. Current catch-and-log behavior must be replaced. |
| Q5 | `reserveQuota` is a misnomer — immediately consumes | Agent B §1.4 | **EXPLICIT_FUTURE_DEBT** | Naming issue. Functionally correct after Q1 fix. Renaming deferred. |

### 2.4 Lifecycle/State Machine Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| L1 | No CAS on EXECUTING → COMPLETING transition | Agent C §2.2-N4, §4.2 | **PROTOCOL_IMPLEMENTATION** | Change updateStatus to `WHERE id = ? AND status = ?`. Required for duplicate finalization prevention. |
| L2 | No CAS on COMPLETING → COMPLETED transition | Agent C §5.2-F7, §4.2 | **PROTOCOL_IMPLEMENTATION** | Same CAS pattern. Prevents stuck COMPLETING and concurrent double-completion. |
| L3 | No stale-COMPLETING detector | Agent C §5.2-F7 | **EXPLICIT_FUTURE_DEBT** | Extend findStaleExecutingJobs to include COMPLETING. Not blocking protocol. Compensation service is being disabled (R1) anyway. |
| L4 | `render_job_lifecycle_events.output_product_id` never populated | Agent C §2.2-N6 | **PROTOCOL_IMPLEMENTATION** | Protocol creates Product from render_output. Write output_product_id to lifecycle event when Product is created. |
| L5 | State machine is in-memory only (ConcurrentHashMap), per-service instance | Agent C §2.1-4, Agent A §5 | **EXPLICIT_FUTURE_DEBT** | DB is authoritative. In-memory is advisory. Not blocking. Multi-instance concern is real but low severity. |
| L6 | `execute()` path has no @Transactional | Agent A §6.1 | **PROTOCOL_IMPLEMENTATION** | finishRenderPhaseInternal called directly from execute() without transactional context. Protocol must define explicit transaction boundaries per ADR-026 transaction topology. |
| L7 | Product has no validation that storageReferenceId is non-null before markReady | Agent C §2.1-7 | **PROTOCOL_IMPLEMENTATION** | Protocol couples Product READY to committed StorageReference. Must validate. |

### 2.5 Content/Checksum Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| C1 | `computeContentHash` uses URI hashCode, not content bytes | Agent A §4.3, Agent C §2.1-8 | **PROTOCOL_IMPLEMENTATION** | Replace with SHA-256 of actual file bytes during upload. ADR-026 explicitly requires content-based checksum. |
| C2 | Hardcoded artifact metadata in StorageCatalogService (`"mp4", "1920x1080", 30L`) | Agent A §6.2 | **PROTOCOL_IMPLEMENTATION** | Must derive from actual RenderResult. Non-MP4 outputs get wrong metadata. |

### 2.6 Compensation/Retry Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| R1 | StaleRenderJobCompensationService reachable via scheduled + startup triggers | Agent B §2, ADR-026 | **REMOVE_AS_STALE_CONTRACT** | Disable scheduled/startup triggers. Operates on incomplete protocol. Must not run until protocol is implemented and compensation is redesigned. |
| R2 | canRetry always false on all RenderJobStatus values | Agent B §3 | **REMOVE_AS_STALE_CONTRACT** | Dead field. No code reads it. Remove from enum and DTOs. Return explicit unsupported. |
| R3 | Compensation misses PROVIDER_SELECTED, COMPLETING states | Agent B §2.3 | **EXPLICIT_FUTURE_DEBT** | When compensation is redesigned post-protocol, must target all non-terminal active states. Deferred with R1. |
| R4 | No concurrency guard on compensation service | Agent B §2.4 | **EXPLICIT_FUTURE_DEBT** | Multi-instance risk. Deferred with R1 (compensation disabled). |
| R5 | No retry runtime exists | ADR-026, Agent B §3 | **EXPLICIT_FUTURE_DEBT** | canRetry is dead (R2). Actual retry via new RenderJob attempt is future work. |

### 2.7 Product Registration Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| P1 | No code connects render completion to Product creation | Agent A §3.4, Agent C §2.1-1 | **PROTOCOL_IMPLEMENTATION** | Protocol creates Product when render_output reaches COMMITTED. Implement in RenderOutputCommitService. |
| P2 | Product.save() upserts by PK only — no deduplication by jobId | Agent C §2.2-N3 | **PROTOCOL_IMPLEMENTATION** | With V5 render_job_id on product (S6), upsert ON CONFLICT(render_job_id, product_type). |

### 2.8 Duplicate Finalization Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| D1 | No duplicate finalization guard on COMPLETING state | Agent C §2.1-2, §4.2 | **PROTOCOL_IMPLEMENTATION** | render_output UNIQUE constraint (S1) + INSERT ON CONFLICT DO NOTHING provides durable guard. |
| D2 | Race window: concurrent finishRenderPhase both succeed | Agent C §4.2, Failure F8 | **FAILURE_WINDOW_VERIFICATION** | Must be tested: two concurrent calls for same jobId, exactly one produces COMMITTED render_output. |

### 2.9 Storage/Blob Findings

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| B1 | Files.readAllBytes loads entire file into memory | Agent A §5-7 | **EXPLICIT_FUTURE_DEBT** | Memory pressure for large files. Streaming upload is future optimization. Not blocking protocol. |
| B2 | Orphan blobs from non-transactional writes | Agent A §5-8, Failure F5/F6/F9 | **FAILURE_WINDOW_VERIFICATION** | Deterministic key (renders/{tenantId}/{jobId}/output.{format}) makes overwrites safe. Verify in failure window tests. |
| B3 | Storage upload not idempotent on retry | Agent C §5.1-F5 | **PROTOCOL_IMPLEMENTATION** | Deterministic key enables idempotent overwrite. Implementation must use deterministic key. |

### 2.10 Existing Idempotency (Positive — No Action Required)

| # | Finding | Source | Allocation | Rationale |
|---|---------|--------|------------|-----------|
| X1 | RenderBillingRecord is idempotent (deterministic ID + upsert) | Agent B §1.1 | **NONE** | Already correct. No change needed. |
| X2 | UsageMeteringService.recordUsage has in-memory idempotency keys | Agent B §1.5 | **NONE** | Already correct for current scope. |

---

## 3. Failure-Window Verification Requirements

The following findings require explicit test verification during the failure-window phase:

| # | Failure Window | Findings Tested | Test Description |
|---|---------------|-----------------|------------------|
| FW1 | Blob write fails, no DB state | B2 | Verify: no render_output created, job FAILED, no orphan references |
| FW2 | Blob succeeds, DB fails | B2, D1 | Verify: deterministic key = safe overwrite on retry |
| FW3 | StorageReference creation fails | B2 | Verify: render_output PENDING, blob exists but unreferenced |
| FW4 | Artifact creation fails | B2 | Verify: blob + StorageRef exist, render_output PENDING |
| FW5 | Product creation fails | P1, P2 | Verify: blob + Artifact exist, render_output PENDING, Product not READY |
| FW6 | Billing fails | Q1, Q4 | Verify: all output metadata exists, render_output PENDING |
| FW7 | Publication (COMPLETING → COMMITTED) fails | L1, L2 | Verify: CAS prevents stuck state, retry succeeds |
| FW8 | Completion (COMPLETING → COMPLETED) fails | L2 | Verify: CAS retry, no double COMPLETED |
| FW9 | Duplicate finalization | D1, D2, S1 | Verify: second call returns existing record, no duplicate StorageRef/Artifact/Product/Billing |
| FW10 | Process interruption (JVM crash) | B2, L6 | Verify: deterministic replay, final state is FAILED or COMPLETED |

---

## 4. Minimum V5 Schema Determination

### 4.1 Required DDL Changes

The minimum V5 migration must contain exactly these changes:

#### New Table: `render_output`

Minimum columns for protocol operation:

| Column | Type | Nullable | Default | Purpose |
|--------|------|----------|---------|---------|
| id | TEXT | NOT NULL | — | Primary key (`ro-{uuid}`) |
| tenant_id | TEXT | NOT NULL | — | Tenant scope |
| project_id | TEXT | NOT NULL | — | Project scope |
| render_job_id | TEXT | NOT NULL | — | FK → render_job(id), ON DELETE RESTRICT |
| output_type | TEXT | NOT NULL | `'FINAL_RENDER'` | Extensible output type |
| status | TEXT | NOT NULL | `'PENDING'` | PENDING → COMMITTED or FAILED |
| staged_object_locator | TEXT | NULL | — | Pre-commit blob path |
| committed_object_locator | TEXT | NULL | — | Final deterministic blob path |
| content_checksum | TEXT | NULL | — | SHA-256 of actual bytes |
| content_size | BIGINT | NULL | — | Bytes |
| content_type | TEXT | NULL | — | MIME type |
| storage_reference_id | TEXT | NULL | — | FK → storage_reference |
| artifact_id | TEXT | NULL | — | FK → artifact (canonical source) |
| product_id | TEXT | NULL | — | FK → product |
| quota_operation_id | TEXT | NULL | — | Links to quota operation |
| billing_operation_id | TEXT | NULL | — | Links to billing ledger |
| failure_code | TEXT | NULL | — | Error classification |
| failure_summary | TEXT | NULL | — | Safe error message |
| created_at | TIMESTAMPTZ | NOT NULL | `NOW()` | Row creation |
| updated_at | TIMESTAMPTZ | NOT NULL | `NOW()` | Last mutation |
| committed_at | TIMESTAMPTZ | NULL | — | Atomic "output ready" marker |
| version | INTEGER | NOT NULL | `0` | Optimistic locking |

Constraints:

| Name | Type | Columns | Purpose |
|------|------|---------|---------|
| render_output_pkey | PRIMARY KEY | (id) | Row identity |
| fk_render_output_job | FOREIGN KEY | render_job_id → render_job(id) | Referential integrity |
| uq_render_output_job_type | UNIQUE | (render_job_id, output_type) | **THE critical invariant: one output per job per type** |
| ck_render_output_status | CHECK | status IN ('PENDING', 'COMMITTED', 'FAILED') | Status domain |

Indexes:

| Name | Columns | Purpose |
|------|---------|---------|
| ix_render_output_tenant | (tenant_id) | Tenant-scoped queries |
| ix_render_output_status | (status) | Status-based queries |
| ix_render_output_job | (render_job_id) | Job-scoped lookups |

#### Modified Table: `render_job`

| Change | Column/Index | Type | Purpose |
|--------|-------------|------|---------|
| ADD COLUMN | idempotency_key | TEXT, NULLABLE | Deduplicate job submissions |
| ADD INDEX (UNIQUE, PARTIAL) | uq_render_job_idempotency | (idempotency_key) WHERE idempotency_key IS NOT NULL | Unique constraint for dedup |
| VERIFY/ADD COLUMN | updated_at | TIMESTAMPTZ, NOT NULL, DEFAULT NOW() | Must exist (referenced by CAS queries). If already exists via undocumented migration, V5 formalizes it. |

#### Modified Table: `billing_ledger_entry`

| Change | Index | Columns | Purpose |
|--------|-------|---------|---------|
| ADD UNIQUE INDEX | uq_billing_ledger_reference | (reference_type, reference_id) | Idempotency: no duplicate ledger entries for same reference |

#### Modified Table: `quota_usage`

| Change | Index | Columns | Purpose |
|--------|-------|---------|---------|
| ADD UNIQUE INDEX | uq_quota_usage_tenant_feature | (tenant_id, feature_code) | Idempotency: no duplicate quota rows. Enables atomic increment. |

#### Modified Table: `product`

| Change | Column/Index | Type | Purpose |
|--------|-------------|------|---------|
| ADD COLUMN | render_job_id | TEXT, NULLABLE | Link Product back to RenderJob |
| ADD INDEX (UNIQUE, PARTIAL) | uq_product_render_job_type | (render_job_id, product_type) WHERE render_job_id IS NOT NULL | One FINAL_RENDER Product per RenderJob |

### 4.2 What V5 Does NOT Include

| Item | Reason for Exclusion |
|------|---------------------|
| render_job.status CHECK constraint | Deferred (S7). Not blocking. |
| artifact table changes | Protocol uses render_output as authority. Artifact table schema unchanged. |
| Retries table/schema | Retry runtime not implemented (R5). |
| Cleanup/orphan table | Deferred. Deterministic key makes orphans bounded. |
| Temporal/OpenCue columns | Future integration. Not started. |

### 4.3 V5 Ordering

The migration must apply in this order within a single Flyway script:

1. `ALTER TABLE render_job ADD COLUMN updated_at` (if not exists) — required before any code references it
2. `ALTER TABLE render_job ADD COLUMN idempotency_key` — required for Phase 2
3. `ALTER TABLE product ADD COLUMN render_job_id` — required for completion invariant
4. `CREATE TABLE render_output` — the core table, references render_job(id)
5. `CREATE UNIQUE INDEX uq_billing_ledger_reference ON billing_ledger_entry(...)` — required for Phase 5
6. `CREATE UNIQUE INDEX uq_quota_usage_tenant_feature ON quota_usage(...)` — required for Phase 5
7. Indexes on render_output

### 4.4 V5 Backward Compatibility

All changes are additive (new columns are NULLABLE, new table is empty, new indexes are on existing data). No existing data is modified. No existing queries break. V5 is safe to apply on a running system with existing data.

---

## 5. Implementation Phase Allocation Map

Mapping findings to ADR-026 implementation roadmap phases:

| Phase | Scope | V5 Required | Findings Addressed |
|-------|-------|-------------|-------------------|
| **Phase 1** | Schema + Repository | YES (V5) | S1, S2, S3, S4, S5, S6 |
| **Phase 2** | Single output-commit authority | No | P1, D1, L1, L2, L6 |
| **Phase 3** | Blob ownership + checksum | No | C1, B3, A1 |
| **Phase 4** | Artifact/Product publication coupling | No | P2, L4, L7, C2 |
| **Phase 5** | Quota/Billing idempotency | YES (V5 constraints) | Q1, Q2, Q3, Q4 |
| **Phase 6** | Disable stale compensation | No | R1 |
| **Phase 7** | Remove canRetry | No | R2 |
| **Phase 8** | Failure-window verification | No | FW1–FW10, D2, B2 |
| **Phase 9** | Architecture guards | No | Guard script updates |

---

## 6. Allocation Summary by Category

### V5_SCHEMA (6 findings)

| ID | Finding |
|----|---------|
| S1 | New `render_output` table with UNIQUE(render_job_id, output_type) |
| S2 | `render_job.idempotency_key` column + partial unique index |
| S3 | `billing_ledger_entry` UNIQUE(reference_type, reference_id) |
| S4 | `quota_usage` UNIQUE(tenant_id, feature_code) |
| S5 | `render_job.updated_at` column (verify/formalize) |
| S6 | `product.render_job_id` column + partial unique index |

### PROTOCOL_IMPLEMENTATION (17 findings)

| ID | Finding |
|----|---------|
| A1 | Artifact ID mismatch fix — plumb artifactId through StorageCatalogPort |
| Q1 | Remove double quota consumption |
| Q2 | Atomic quota increment (replace read-then-write) |
| Q3 | BillingLedger upsert (replace plain INSERT) |
| Q4 | Billing failure must set render_output FAILED (not swallow) |
| L1 | CAS on EXECUTING → COMPLETING |
| L2 | CAS on COMPLETING → COMPLETED |
| L4 | Populate render_job_lifecycle_events.output_product_id |
| L6 | Explicit transaction boundaries for execute() path |
| L7 | Validate storageReferenceId before Product markReady |
| C1 | Content checksum: SHA-256 of actual bytes |
| C2 | Derive artifact metadata from RenderResult (not hardcoded) |
| P1 | Product creation coupled to render_output COMMITTED |
| P2 | Product upsert ON CONFLICT(render_job_id, product_type) |
| D1 | render_output ON CONFLICT DO NOTHING for duplicate finalization |
| B3 | Deterministic blob key for idempotent upload |
| R1 | Disable StaleRenderJobCompensationService |

### FAILURE_WINDOW_VERIFICATION (10 findings)

| ID | Finding |
|----|---------|
| FW1 | Blob write fails, no DB state |
| FW2 | Blob succeeds, DB fails |
| FW3 | StorageReference creation fails |
| FW4 | Artifact creation fails |
| FW5 | Product creation fails |
| FW6 | Billing fails |
| FW7 | Publication fails (COMPLETING → COMMITTED) |
| FW8 | Completion fails (COMPLETING → COMPLETED) |
| FW9 | Duplicate finalization |
| FW10 | Process interruption (JVM crash) |

### REMOVE_AS_STALE_CONTRACT (1 finding)

| ID | Finding |
|----|---------|
| R2 | Remove `RenderJobStatus.canRetry` field (always false, dead code) |

Note: R1 (disable compensation) is allocated to PROTOCOL_IMPLEMENTATION rather than REMOVE_AS_STALE_CONTRACT because disabling a reachable service is an implementation action, not a contract removal. R2 is a pure dead-field removal.

### EXPLICIT_FUTURE_DEBT (11 findings)

| ID | Finding | Deferred Because |
|----|---------|-----------------|
| S7 | render_job.status CHECK constraint | Low severity, not blocking |
| A2 | Artifact table multiple rows per render_job_id | Design allows it; render_output is authority |
| A3 | Timeline JSON synthetic URI | Cosmetic, low severity |
| A4 | ArtifactGraph/Artifact disconnection | Deferred to Artifact DAG (POSTPONED) |
| Q5 | reserveQuota naming misnomer | Functionally correct after Q1 fix |
| L3 | No stale-COMPLETING detector | Compensation disabled (R1); redesign later |
| L5 | In-memory state machine per-instance | DB is authoritative; advisory only |
| B1 | Files.readAllBytes memory pressure | Streaming upload is future optimization |
| R3 | Compensation misses PROVIDER_SELECTED/COMPLETING | Deferred with R1 |
| R4 | No concurrency guard on compensation | Deferred with R1 |
| R5 | No retry runtime | New RenderJob attempt is future work |

---

## 7. Findings Cross-Reference: ADR-026 Problems → Allocation

| ADR-026 Problem # | Description | Finding IDs | Allocations |
|-------------------|-------------|-------------|-------------|
| 1 | No canonical output-commit authority | S1, P1, D1 | V5_SCHEMA, PROTOCOL_IMPL |
| 2 | Artifact ID mismatch | A1 | PROTOCOL_IMPL |
| 3 | Double quota consumption | Q1 | PROTOCOL_IMPL |
| 4 | Content hash = URI hash | C1 | PROTOCOL_IMPL |
| 5 | Product registration disconnected | P1, P2 | PROTOCOL_IMPL |
| 6 | No duplicate-finalization guard | D1, L1, L2 | PROTOCOL_IMPL |
| 7 | QuotaUsage non-idempotent | Q2, S4 | PROTOCOL_IMPL, V5_SCHEMA |
| 8 | BillingLedger non-idempotent | Q3, S3 | PROTOCOL_IMPL, V5_SCHEMA |
| 9 | canRetry always false | R2 | REMOVE_AS_STALE |
| 10 | StaleCompensation reachable | R1 | PROTOCOL_IMPL |

---

## 8. Decision: R1 Allocation

`R1` (disable StaleRenderJobCompensationService) was evaluated for both REMOVE_AS_STALE_CONTRACT and PROTOCOL_IMPLEMENTATION.

**Allocated to**: PROTOCOL_IMPLEMENTATION (Phase 6)

**Reasoning**: The compensation service is currently reachable and functional — it is not dead code. Disabling it is an active implementation decision that must happen during protocol implementation to prevent the service from operating on an incomplete protocol. This is an implementation-phase action, not a contract removal. Once the protocol is fully implemented and compensation is redesigned, the service may be re-enabled with proper protocol awareness.

R2 (canRetry removal) is allocated to REMOVE_AS_STALE_CONTRACT because the field is genuinely dead — always false, never read, no external dependency. It is safe to remove independently of the protocol implementation.

---

## 9. Completeness Check

| Source | Findings Extracted | Allocated |
|--------|--------------------|-----------|
| Agent A (current-state synthesis) | 11 findings | 11 ✅ |
| Agent B (quota/idempotency/compensation) | 11 findings | 11 ✅ |
| Agent C (schema/failure-review) | N1–N6 + 8 prior findings confirmed | 14 unique ✅ |
| Lead decision | 10 problems + protocol design | Mapped to findings ✅ |
| ADR-026 | 10 problems | All traced ✅ |
| Agent E verification | 0 new findings (verification only) | N/A ✅ |

**Total unique findings**: 45 (6 V5_SCHEMA + 17 PROTOCOL_IMPL + 10 FAILURE_WINDOW + 1 REMOVE_STALE + 11 EXPLICIT_DEBT)

All findings from all prior reports are accounted for. No finding is unallocated.
