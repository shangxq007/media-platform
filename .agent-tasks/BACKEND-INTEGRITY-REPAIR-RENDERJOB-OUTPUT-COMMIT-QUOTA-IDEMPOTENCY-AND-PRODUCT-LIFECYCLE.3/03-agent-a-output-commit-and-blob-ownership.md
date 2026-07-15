# Agent A: Complete Output-Commit Path — Provider Render Result Through Blob Storage, Artifact, Product, Billing, and RenderJob Completion

## Table of Contents
1. [Overview and Path Diagram](#1-overview-and-path-diagram)
2. [Stage 1: Provider Render Execution](#stage-1-provider-render-execution)
3. [Stage 2: Billing Reservation (Pre-Render)](#stage-2-billing-reservation-pre-render)
4. [Stage 3: Blob Storage Upload](#stage-3-blob-storage-upload)
5. [Stage 4: StorageCatalog Registration (StorageReference)](#stage-4-storagecatalog-registration-storagereference)
6. [Stage 5: ArtifactGraph Creation](#stage-5-artifactgraph-creation)
7. [Stage 6: Artifact URI Persistence on RenderJob](#stage-6-artifact-uri-persistence-on-renderjob)
8. [Stage 7: Billing Finalization (Post-Render)](#stage-7-billing-finalization-post-render)
9. [Stage 8: Quota Consumption](#stage-8-quota-consumption)
10. [Stage 9: State Machine Completion + Events](#stage-9-state-machine-completion--events)
11. [Stage 10: Product Publication](#stage-10-product-publication)
12. [Cross-Cutting: Transaction Boundaries](#cross-cutting-transaction-boundaries)
13. [Cross-Cutting: Idempotency Analysis](#cross-cutting-idempotency-analysis)
14. [Cross-Cutting: Orphan and Duplicate Risks](#cross-cutting-orphan-and-duplicate-risks)
15. [Summary of Findings](#summary-of-findings)

---

## 1. Overview and Path Diagram

The complete output-commit path lives in a single method: `RenderJobExecutionService.finishRenderPhaseInternal()` (lines 321–458). This method runs inside a `@Transactional` context propagated from the caller. The flow is:

```
finishRenderPhaseInternal(tenantId, jobId)
│
├─ 1. Load job record (jOOQ read)
├─ 2. Billing: reserveQuota()
│      ├─ renderQuotaService.consumeQuota() → quota_usage INSERT/UPDATE
│      └─ billingRecordRepository.save() → render_billing_record INSERT
├─ 3. Provider render: provider.render() → RenderResult
│      └─ FFmpegRenderProvider → writes file to local FS
├─ 4. Billing: finalizeCost()
│      ├─ billingRecordRepository.save() → render_billing_record UPSERT
│      └─ usageMeteringService.recordUsage() × 2 → external metering
├─ 5. State: EXECUTING → COMPLETING
├─ 6. Blob upload: artifactStorageService.uploadJobOutput()
│      ├─ Files.readAllBytes(localFile) → IO read
│      ├─ blobStorage.put() → IO write (local FS or S3)
│      └─ storageCatalogPort.registerArtifact() → artifact INSERT
├─ 7. ArtifactGraph: create + save
│      └─ artifactGraphRepository.saveGraph() → artifact_node + artifact_graph INSERT
├─ 8. Job artifact_uri UPDATE
├─ 9. State: COMPLETING → COMPLETED
├─ 10. Quota consumption: quotaService.consumeQuota() → quota_usage UPDATE
└─ 11. Events published:
       ├─ notificationEventPublisher.publish(ArtifactCreatedEvent)
       │   └─ Outbox: artifact INSERT (outbox_events)
       └─ eventPublisher.publishEvent(RenderJobCompletedEvent)
           └─ Spring synchronous: AuditEventHandler, NotificationEventHandler, etc.
```

---

## Stage 1: Provider Render Execution

### Source File and Method
- **File**: `RenderJobExecutionService.java` line 367–376
- **Method**: `finishRenderPhaseInternal()` → `executeRenderWithOptionalDag()` → `provider.render()`
- **Provider impl**: `FFmpegRenderProvider.render()` (line 72–177)

### What Happens
1. `providerRuntimeEngine.resolveProvider()` selects the best render provider
2. `provider.render(jobId, aiScript, profile)` executes the actual render
3. FFmpeg writes output file to local FS: `${app.storage.local-root}/artifacts/${jobId}/output.mp4`
4. Returns `RenderResult(artifactId, storageUri, duration, format, resolution)`

### Transaction Propagation
- Provider render runs **inside** the caller's `@Transactional` context
- However, the actual render (ffmpeg process, file I/O) is **non-transactional** — filesystem writes are not rolled back by Spring transactions

### External I/O
- **Write**: Local filesystem (ffmpeg output file)
- **Process**: ffmpeg subprocess execution via `ProcessToolRunner`

### Database Writes
- `renderJobRepository.updateTraceId(jobId, traceId)` — line 531
- `renderJobRepository.updateSelectedProvider(jobId, providerName)` — line 532

### Idempotency Behavior
- **NOT idempotent**: Each call generates a new `artifactId` via `Ids.newId("art")` (line 154 in FFmpegRenderProvider)
- The provider writes to a fixed path (`artifacts/{jobId}/output.mp4`) so re-execution **overwrites** the previous output
- No deduplication check exists before rendering

### Failure Boundary
- If render fails, exception propagates to `finishRenderPhaseInternal` catch block (line 372–376)
- `failureService.recordDurableFailure(jobId, ...)` is called in **REQUIRES_NEW** transaction — persists even if outer tx rolls back
- Exception re-thrown as `IllegalStateException("Render failed")`

### Orphan/Duplicate Risk
- **Orphan risk**: If the process crashes after ffmpeg writes the file but before any DB writes, the file exists on disk with no DB record
- **Duplicate risk**: Re-execution overwrites the same file path; no versioning

---

## Stage 2: Billing Reservation (Pre-Render)

### Source File and Method
- **File**: `RenderJobExecutionService.java` lines 356–365
- **Calls**: `BillingEnforcementService.reserveQuota()` → `RenderBillingRecordRepository.save()`
- **File**: `BillingEnforcementService.java` lines 137–154
- **File**: `RenderBillingRecordRepository.java` lines 33–73

### What Happens
1. Checks `billingEnforcementService.isEnforcementEnabled()` (config flag `billing.enforcement.enabled`)
2. Calls `renderQuotaService.consumeQuota(tenantId, "render", 1)` — increments quota usage
3. Creates `RenderBillingRecord.create(jobId, tenantId, estimatedCost, now)` with status `ESTIMATED`
4. Saves to `render_billing_record` table via UPSERT (`onConflict(id).doUpdate()`)

### Transaction Propagation
- **Same transaction** as the caller's `@Transactional`
- If the outer transaction rolls back, the billing reservation also rolls back

### External I/O
- None (DB-only)

### Database Writes
- `quota_usage` — INSERT or UPDATE via `QuotaUsageRepository.incrementUsage()`
- `render_billing_record` — INSERT/UPSERT via `RenderBillingRecordRepository.save()`

### Idempotency Behavior
- `RenderBillingRecord` ID is deterministic: `"bill-" + jobId` (line 28 in RenderBillingRecord)
- Uses `onConflict(id).doUpdate()` — **idempotent** for the same jobId
- `quota_usage.incrementUsage()` uses **read-then-write** pattern — **NOT idempotent**, double-increment on re-entry

### Failure Boundary
- If reservation fails, `failureService.recordDurableFailure()` called (REQUIRES_NEW), then exception thrown
- Job transitions to FAILED state

### Orphan/Duplicate Risk
- **Orphan**: Billing records are cleaned up with the transaction rollback (same tx)
- **Duplicate**: `quota_usage.incrementUsage()` has a read-then-write race — concurrent calls could both read the same value and overwrite each other's increment

---

## Stage 3: Blob Storage Upload

### Source File and Method
- **File**: `RenderArtifactStorageService.java` lines 34–47
- **Method**: `uploadJobOutput(jobId, projectId, artifactId, localRelativePath, contentType)`
- **Called from**: `RenderJobExecutionService.java` line 402

### What Happens
1. Resolves local file path: `Path.of(storageRoot, localRelativePath)`
2. Validates file exists: `Files.isRegularFile(localFile)`
3. Reads entire file into memory: `Files.readAllBytes(localFile)` — **⚠️ large file risk**
4. Constructs object key: `artifactId + "/" + fileName`
5. Calls `blobStorage.put(PutObjectCommand("artifacts", objectKey, bytes, contentType))`
6. Registers artifact in storage catalog: `storageCatalogPort.registerArtifact(jobId, projectId, storageRef)`

### Transaction Propagation
- Runs inside the caller's `@Transactional` context
- **However**: Filesystem I/O (read + write) is NOT transactional
- If the DB transaction rolls back after blob write, the blob remains in storage (orphan)

### External I/O
- **Read**: Local filesystem (`Files.readAllBytes`)
- **Write**: Blob storage (local FS via `LocalFsStorageProvider.put()` or S3 via `S3BlobStorageProvider.put()`)

### Database Writes
- Indirectly via `storageCatalogPort.registerArtifact()` → `ArtifactRepository.save()` → `artifact` table INSERT

### Idempotency Behavior
- Object key is `artifactId + "/" + fileName` where `artifactId` is generated fresh each time
- `ArtifactRepository.save()` does a plain INSERT (no UPSERT) — **NOT idempotent**, will fail on duplicate artifact ID
- `LocalFsStorageProvider.put()` uses `REPLACE_EXISTING` — overwrites existing file, so file-level is idempotent

### Failure Boundary
- If file not found: `IOException("Rendered file not found")` — caught by caller's catch block
- If blob write fails: exception propagates, `failureService.recordDurableFailure()` called
- If catalog registration fails: exception propagates, but blob is already written (orphan blob)

### Orphan/Duplicate Risk
- **Orphan blob**: If catalog registration fails after blob write succeeds, the blob exists in storage with no DB reference
- **Duplicate**: If `artifactId` collides (unlikely with `Ids.newId`), the INSERT would fail
- **Memory pressure**: `Files.readAllBytes()` loads entire file into memory — dangerous for large video files

---

## Stage 4: StorageCatalog Registration (StorageReference)

### Source File and Method
- **File**: `StorageCatalogService.java` lines 30–41
- **Method**: `registerArtifact(renderJobId, projectId, providerRef)`
- **Interface**: `StorageCatalogPort.java`

### What Happens
1. Generates new artifact ID: `Ids.newId("art")` — **⚠️ different from the artifactId passed in!**
2. Creates `ArtifactRef` with hardcoded format="mp4", resolution="1920x1080", duration=30L
3. Saves to `artifact` table via `ArtifactRepository.save()`

### Transaction Propagation
- Same transaction as caller

### External I/O
- None (DB-only)

### Database Writes
- `artifact` table — plain INSERT (no UPSERT, no conflict handling)

### Idempotency Behavior
- **NOT idempotent**: Generates a new ID each call; plain INSERT will fail on duplicate if ID collides
- The `artifactId` generated here is **different** from the `artifactId` in the `RenderResult` — this is a **data inconsistency**: the storage catalog creates its own artifact ID that doesn't match the one in the RenderResult/ArtifactGraph

### Failure Boundary
- If INSERT fails (duplicate key), exception propagates

### Orphan/Duplicate Risk
- **ID mismatch**: The `StorageCatalogService.registerArtifact()` generates a NEW artifactId (`Ids.newId("art")`) that differs from the `artifactId` in `RenderResult`. The `ArtifactGraph` uses the RenderResult's artifactId, but the storage catalog uses its own. This means:
  - Two different artifact IDs exist for the same physical blob
  - The `artifact` table and `artifact_node` table reference different IDs for the same output
- **Orphan**: If the outer transaction rolls back after this INSERT but before blob cleanup, the artifact record exists with no matching blob (if blob write also rolled back — but it can't since it's filesystem)

---

## Stage 5: ArtifactGraph Creation

### Source File and Method
- **File**: `RenderJobExecutionService.java` lines 410–441
- **Domain**: `ArtifactGraph.create()`, `ArtifactNode.create()`
- **Repository**: `ArtifactGraphRepository.java` lines 37–111

### What Happens
1. Computes content hash: `computeContentHash(storageUri)` — **⚠️ hash is based on URI string, not file content**
2. Creates root `ArtifactNode` with the RenderResult's `artifactId`
3. Creates `ArtifactGraph` containing the root node
4. Optionally adds timeline JSON artifact node
5. Saves via `artifactGraphRepository.saveGraph()` which:
   - Inserts each node into `artifact_node` table (UPSERT via `onConflict(id).doUpdate()`)
   - Inserts graph metadata into `artifact_graph` table (UPSERT via `onConflict(graph_id).doUpdate()`)

### Transaction Propagation
- Same transaction as caller

### External I/O
- None (DB-only)

### Database Writes
- `artifact_node` — UPSERT per node
- `artifact_graph` — UPSERT for graph metadata

### Idempotency Behavior
- **Idempotent**: Uses `onConflict.doUpdate()` for both nodes and graph — safe for re-entry
- Artifact node IDs are deterministic (passed in from RenderResult)

### Failure Boundary
- If save fails, exception propagates to caller's catch block
- `artifactGraphRepository` is `@Autowired(required = false)` — null check on line 438

### Orphan/Duplicate Risk
- **Content hash weakness**: `computeContentHash()` hashes the URI string, not the actual file bytes. Two different files with the same URI would have the same hash, defeating deduplication
- **Graph orphan**: If the transaction rolls back, the graph is not persisted (same tx). But the blob in storage remains

---

## Stage 6: Artifact URI Persistence on RenderJob

### Source File and Method
- **File**: `RenderJobExecutionService.java` line 444
- **Method**: `renderJobRepository.updateArtifactUri(jobId, storageUri)`
- **Repository**: `RenderJobRepository.java` lines 224–229

### What Happens
1. Updates `render_job.artifact_uri` column with the storage URI

### Transaction Propagation
- Same transaction as caller

### Database Writes
- `render_job` — UPDATE artifact_uri WHERE id = jobId

### Idempotency Behavior
- **Idempotent**: Simple UPDATE, overwrites previous value

### Failure Boundary
- If UPDATE fails, exception propagates

### Orphan/Duplicate Risk
- None (idempotent UPDATE)

---

## Stage 7: Billing Finalization (Post-Render)

### Source File and Method
- **File**: `RenderJobExecutionService.java` lines 379–389
- **Calls**: `BillingEnforcementService.finalizeCost()`
- **File**: `BillingEnforcementService.java` lines 183–223

### What Happens
1. Calculates actual cost via `costEstimationService.estimate()`
2. Loads existing billing record: `billingRecordRepository.findByJobId(jobId)`
3. If not found, creates new one (defensive)
4. Finalizes record: `existing.finalize(actualCost, duration, providerId, outputSizeBytes)`
5. Saves finalized record via UPSERT
6. Records usage in metering system: `usageMeteringService.recordUsage()` × 2 (seconds + bytes)

### Transaction Propagation
- Same transaction as caller
- **Critical**: `finalizeCost` failure is **swallowed** (line 386–388: catch + log.warn, no re-throw)
- This means billing finalization failure does NOT fail the job

### External I/O
- `usageMeteringService.recordUsage()` — external metering system (2 calls)

### Database Writes
- `render_billing_record` — UPSERT
- External metering system — 2 usage records

### Idempotency Behavior
- `RenderBillingRecord` UPSERT is idempotent (deterministic ID = `"bill-" + jobId`)
- `usageMeteringService.recordUsage()` uses idempotency keys: `"job-{jobId}-seconds"` and `"job-{jobId}-bytes"` — likely idempotent if metering service respects keys

### Failure Boundary
- **Failure is swallowed**: Billing finalization errors are logged but do not fail the job
- This is intentional — billing shouldn't block job completion

### Orphan/Duplicate Risk
- **Orphan billing record**: If reservation succeeded but finalization is skipped (exception swallowed), the billing record stays in `ESTIMATED` status forever
- **Double metering**: If the job is retried, `usageMeteringService.recordUsage()` could record duplicate usage if the metering service doesn't deduplicate by idempotency key

---

## Stage 8: Quota Consumption

### Source File and Method
- **File**: `RenderJobExecutionService.java` line 450
- **Method**: `quotaService.consumeQuota(tenantId, "render", 1)`
- **Service**: `RenderQuotaService.java` lines 32–34
- **Repository**: `QuotaUsageRepository.java` lines 23–42

### What Happens
1. Calls `quotaUsageRepository.incrementUsage(tenantId, "render", 1)`
2. Read-then-write: reads current value, adds 1, writes back

### Transaction Propagation
- Same transaction as caller

### Database Writes
- `quota_usage` — UPDATE or INSERT

### Idempotency Behavior
- **NOT idempotent**: Read-then-write pattern means each call increments by 1
- If the job is retried, quota is consumed again

### Failure Boundary
- If quota increment fails, exception propagates and job completion fails

### Orphan/Duplicate Risk
- **Double consumption**: On retry, quota is consumed twice (once during reservation, once during completion)
- **Race condition**: Concurrent `incrementUsage()` calls can lose increments (read-modify-write without locking)

---

## Stage 9: State Machine Completion + Events

### Source File and Method
- **State transitions**: `RenderJobExecutionService.java` lines 392–394 (EXECUTING→COMPLETING), 447–449 (COMPLETING→COMPLETED)
- **Status history**: `RenderJobStatusHistoryRepository.record()` — lines 23–29
- **Notification**: `notificationEventPublisher.publish(ArtifactCreatedEvent)` — line 452–453
- **Spring event**: `eventPublisher.publishEvent(RenderJobCompletedEvent)` — line 454

### What Happens
1. State machine validates transition (in-memory ConcurrentHashMap)
2. `renderJobRepository.updateStatus(jobId, "COMPLETING")` / `updateStatus(jobId, "COMPLETED")`
3. `historyRepository.record()` — inserts into `render_job_status_history`
4. `notificationEventPublisher.publish()` → `OutboxBackedNotificationEventPublisher`:
   - Writes to `outbox_events` table with idempotency key `"artifact.created:{artifactId}"`
   - Writes to `outbox_events` table with idempotency key `"render.job.completed:{jobId}"`
5. `eventPublisher.publishEvent()` → Spring synchronous event listeners:
   - `AuditEventHandler.onRenderJobCompleted()` — writes audit log
   - Other listeners (notifications, etc.)

### Transaction Propagation
- Same transaction as caller for DB writes
- Spring `eventPublisher.publishEvent()` is **synchronous** — runs in the same thread/transaction
- Outbox events are written to DB in the same transaction — reliable delivery

### Database Writes
- `render_job` — UPDATE status (×2: COMPLETING, COMPLETED)
- `render_job_status_history` — INSERT (×2)
- `outbox_events` — INSERT (×2: artifact.created, render.job.completed)
- Audit handler may write to audit table

### Idempotency Behavior
- Outbox events use idempotency keys: `"artifact.created:{artifactId}"` and `"render.job.completed:{jobId}"`
- `render_job_status_history` — plain INSERT with new ID each time — **NOT idempotent** for re-entry
- State machine transition is idempotent for same from→to (returns same state)

### Failure Boundary
- If status UPDATE fails, exception propagates — job fails
- If history INSERT fails, exception propagates — job fails
- If outbox INSERT fails, exception propagates — job fails
- If Spring event listener fails, exception propagates — job fails (no try-catch around event publishing)

### Orphan/Duplicate Risk
- **Duplicate history records**: If the method is called twice (e.g., retry), duplicate history entries are created
- **Outbox deduplication**: Idempotency keys protect against duplicate outbox events
- **Event listener side effects**: Spring synchronous events execute in the same transaction — if a listener fails, the entire job completion fails

---

## Stage 10: Product Publication

### Source File and Method
- **File**: `ProductRuntimeService.java`
- **Methods**: `register()` (lines 23–31), `markReady()` (lines 33–38)

### What Happens
- **Product is NOT automatically created in the output-commit path**
- `ProductRuntimeService` is a standalone service that must be called explicitly
- There is no event listener or automatic bridge from `RenderJobCompletedEvent` → `Product.register()`
- The `Product` domain model requires explicit provenance (`ownerAssetId`, `producerId`, or `sourceTimelineRevisionId`)

### Transaction Propagation
- `register()` and `markReady()` are both `@Transactional` — each in their own transaction

### Database Writes
- `product` table — via `ProductRepository.save()`

### Idempotency Behavior
- `register()` validates `status == REGISTERED` and `hasProvenance()` — will throw if called twice with wrong status
- `markReady()` returns early if already `READY` — **idempotent**

### Orphan/Duplicate Risk
- **No automatic Product creation**: Products are never created as part of the render output-commit path
- **Gap**: There is no bridge between render completion and product publication — this is a **missing integration point**

---

## Cross-Cutting: Transaction Boundaries

### The Outer Transaction
The entire `finishRenderPhaseInternal()` runs inside a single `@Transactional` context:

```
RenderOrchestratorService.submitRenderJob() @Transactional
  → RenderJobExecutionService.executeAfterSubmit() @Transactional (inherits)
    → finishRenderPhaseInternal() (no annotation, inherits)
```

Or via:
```
RenderJobExecutionService.finishRenderPhase() @Transactional
  → finishRenderPhaseInternal() (inherits)
```

### What's IN the Transaction
| Operation | In TX? | Notes |
|-----------|--------|-------|
| Billing reservation (quota_usage, render_billing_record) | ✅ | Rolls back with TX |
| State transitions (render_job status updates) | ✅ | Rolls back with TX |
| Status history inserts | ✅ | Rolls back with TX |
| StorageCatalog registration (artifact table) | ✅ | Rolls back with TX |
| ArtifactGraph save (artifact_node, artifact_graph) | ✅ | Rolls back with TX |
| Artifact URI update on render_job | ✅ | Rolls back with TX |
| Quota consumption | ✅ | Rolls back with TX |
| Outbox event inserts | ✅ | Rolls back with TX |
| Billing finalization (render_billing_record) | ✅ | But failure is swallowed |

### What's NOT in the Transaction (Non-Transactional Side Effects)
| Operation | In TX? | Risk |
|-----------|--------|------|
| FFmpeg process execution | ❌ | Process runs regardless of TX outcome |
| Local filesystem writes (ffmpeg output) | ❌ | Files persist even if TX rolls back |
| Blob storage write (LocalFs/S3) | ❌ | Blobs persist even if TX rolls back |
| File read (Files.readAllBytes) | ❌ | Memory consumed regardless |
| usageMeteringService.recordUsage() | ❌ | External system, not rolled back |
| Spring synchronous event listeners | ⚠️ | In TX but if they call external systems... |

### REQUIRES_NEW Boundaries
| Service | Propagation | Purpose |
|---------|-------------|---------|
| `RenderJobClaimService.claimForSelection()` | REQUIRES_NEW | Claim survives outer rollback |
| `RenderJobFailureService.recordDurableFailure()` | REQUIRES_NEW | Failure record survives outer rollback |

---

## Cross-Cutting: Idempotency Analysis

| Stage | Component | Idempotent? | Mechanism |
|-------|-----------|-------------|-----------|
| Provider render | FFmpegRenderProvider | ❌ | New artifactId each call; overwrites file |
| Billing reservation | QuotaUsageRepository.incrementUsage() | ❌ | Read-then-write |
| Billing reservation | RenderBillingRecordRepository.save() | ✅ | Deterministic ID + UPSERT |
| Blob storage | LocalFsStorageProvider.put() | ✅ | REPLACE_EXISTING |
| Blob storage | S3BlobStorageProvider.put() | ✅ (typically) | S3 PUT is idempotent |
| StorageCatalog | ArtifactRepository.save() | ❌ | Plain INSERT, no UPSERT |
| ArtifactGraph | ArtifactGraphRepository.saveGraph() | ✅ | onConflict.doUpdate() |
| Artifact URI | RenderJobRepository.updateArtifactUri() | ✅ | Simple UPDATE |
| Billing finalization | RenderBillingRecordRepository.save() | ✅ | UPSERT |
| Metering | usageMeteringService.recordUsage() | ✅/⚠️ | Depends on idempotency key handling |
| Status update | RenderJobRepository.updateStatus() | ✅ | Simple UPDATE |
| Status history | RenderJobStatusHistoryRepository.record() | ❌ | Plain INSERT, new ID each time |
| Outbox events | OutboxBackedNotificationEventPublisher | ✅ | Idempotency key on each event |
| Quota consumption | QuotaUsageRepository.incrementUsage() | ❌ | Read-then-write |

---

## Cross-Cutting: Orphan and Duplicate Risks

### Orphan Risks

1. **Orphan blob files** (HIGH): If the DB transaction rolls back after `blobStorage.put()` succeeds, the blob remains in storage with no DB reference. The file on local FS is never cleaned up.

2. **Orphan billing records** (LOW): Billing reservation rolls back with the TX. But if finalization is skipped (swallowed exception), the billing record stays in `ESTIMATED` status forever.

3. **Orphan ffmpeg output** (MEDIUM): FFmpeg writes to `artifacts/{jobId}/output.mp4`. If the process crashes after ffmpeg completes but before any DB writes, the file exists with no DB record. No cleanup mechanism exists.

4. **Orphan storage catalog artifacts** (LOW): The `ArtifactRepository.save()` INSERT rolls back with the TX, so no orphan in the catalog. But the blob it references may exist.

### Duplicate Risks

1. **Double quota consumption** (HIGH): Quota is consumed during reservation (`reserveQuota` calls `consumeQuota`) AND again at completion (line 450: `quotaService.consumeQuota`). On a successful render, quota is consumed **twice**.

2. **Artifact ID mismatch** (HIGH): `StorageCatalogService.registerArtifact()` generates its own `artifactId` (line 31: `Ids.newId("art")`) that differs from the `artifactId` in `RenderResult`. Two different IDs exist for the same physical blob. The `artifact` table and `artifact_node` table reference different IDs.

3. **Duplicate status history** (LOW): If `finishRenderPhaseInternal` is called twice (e.g., retry path), duplicate history records are created.

4. **Double metering on retry** (MEDIUM): If the job is retried after billing finalization succeeded, `usageMeteringService.recordUsage()` records usage again. The idempotency keys (`"job-{jobId}-seconds"`, `"job-{jobId}-bytes"`) should protect against this if the metering service respects them.

5. **Read-then-write race on quota_usage** (MEDIUM): `QuotaUsageRepository.incrementUsage()` uses read-then-write without optimistic locking or SELECT FOR UPDATE. Concurrent calls can lose increments.

---

## Summary of Findings

### Critical Issues

1. **Artifact ID Mismatch**: `StorageCatalogService.registerArtifact()` generates a different artifactId than the one in `RenderResult`. The `artifact` table and `artifact_node` table reference different IDs for the same blob. This breaks the link between storage catalog and artifact graph.

2. **Double Quota Consumption**: `reserveQuota()` calls `renderQuotaService.consumeQuota()` (line 144 of BillingEnforcementService), and then line 450 of `finishRenderPhaseInternal` calls `quotaService.consumeQuota()` again. Successful renders consume quota twice.

3. **No Transactional Envelope for External I/O**: Blob storage writes and filesystem operations are not rolled back when the DB transaction fails, creating orphan blobs. No compensating action or cleanup mechanism exists.

4. **Content Hash is URI-Based**: `computeContentHash()` hashes the URI string, not the actual file content (line 664–668). This defeats deduplication — two different files with the same URI path would have the same hash.

### Medium Issues

5. **Memory Pressure from `Files.readAllBytes()`**: `RenderArtifactStorageService.uploadJobOutput()` reads the entire rendered file into memory (line 40). For large video files, this can cause OOM.

6. **No Product Creation Bridge**: There is no automatic path from render completion to Product registration. `ProductRuntimeService.register()` must be called explicitly, and no event listener bridges the gap.

7. **Billing Finalization Failure Swallowed**: `finalizeCost()` failure is caught and logged but does not fail the job (lines 386–388). This means billing records can be left in `ESTIMATED` status indefinitely.

8. **Quota Read-Then-Write Race**: `QuotaUsageRepository.incrementUsage()` has no locking, allowing concurrent increments to be lost.

### Low Issues

9. **State Machine is In-Memory Only**: `RenderJobStateMachine` uses `ConcurrentHashMap` for state tracking. State is lost on restart. The real state is in the DB, but the in-memory state machine can be inconsistent.

10. **Outbox Events are In-TX**: Outbox events are written in the same transaction. If the transaction is long-running, event delivery is delayed until commit. This is by design (transactional outbox pattern) but means events are not visible until commit.

11. **Synchronous Event Listeners**: `eventPublisher.publishEvent(RenderJobCompletedEvent)` is synchronous. If an audit or notification handler is slow or fails, it blocks or fails the entire job completion.

---

## File Reference Index

| File | Path | Role in Output-Commit |
|------|------|----------------------|
| RenderJobExecutionService.java | render-module/.../app/ | Orchestrator of entire flow |
| RenderArtifactStorageService.java | render-module/.../infrastructure/ | Blob upload + catalog registration |
| BillingEnforcementService.java | render-module/.../infrastructure/billing/ | Quota reservation + finalization |
| RenderBillingRecordRepository.java | render-module/.../infrastructure/billing/ | Billing record persistence |
| RenderQuotaService.java | render-module/.../app/ | Quota check + consumption |
| QuotaUsageRepository.java | render-module/.../app/ | Quota usage persistence |
| ArtifactGraphRepository.java | render-module/.../infrastructure/artifact/ | Artifact graph persistence |
| RenderJobRepository.java | render-module/.../infrastructure/ | Job status + metadata persistence |
| RenderJobStatusHistoryRepository.java | render-module/.../app/ | Status transition history |
| RenderJobClaimService.java | render-module/.../app/ | REQUIRES_NEW claim for start |
| RenderJobFailureService.java | render-module/.../app/ | REQUIRES_NEW failure recording |
| RenderJobStateMachine.java | render-module/.../domain/ | In-memory state validation |
| StorageCatalogService.java | storage-module/.../app/ | Storage artifact registration |
| ArtifactRepository.java | storage-module/.../app/ | Artifact metadata persistence |
| BlobStorage.java | storage-module/.../domain/ | Blob storage interface |
| LocalFsStorageProvider.java | storage-module/.../infrastructure/ | Local FS blob implementation |
| StorageObjectRef.java | storage-module/.../domain/ | Storage reference value object |
| PutObjectCommand.java | storage-module/.../domain/ | Blob write command |
| FFmpegRenderProvider.java | render-module/.../infrastructure/ffmpeg/ | FFmpeg render implementation |
| RenderProvider.java | render-module/.../infrastructure/ | Provider interface + RenderResult |
| ProductRuntimeService.java | render-module/.../app/product/ | Product registration (disconnected) |
| Product.java | render-module/.../domain/product/ | Product domain model |
| NotificationEventPublisher.java | shared-kernel/.../notification/ | Event publishing interface |
| OutboxBackedNotificationEventPublisher.java | outbox-event-module/.../app/ | Outbox-backed event publisher |
| ArtifactCreatedEvent.java | shared-kernel/.../events/ | Artifact created event record |
| RenderJobCompletedEvent.java | shared-kernel/.../events/ | Job completed event record |
