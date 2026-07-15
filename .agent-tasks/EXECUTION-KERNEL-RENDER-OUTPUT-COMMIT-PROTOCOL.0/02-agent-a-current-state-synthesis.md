# Agent A: Current-State Synthesis — RenderJob Output-Commit Path

**Branch**: `arch/render-output-commit-protocol` @ `234689e`
**Date**: 2026-07-15
**Scope**: Validate prior Agent A report (03-agent-a-output-commit-and-blob-ownership.md) against production source. Identify all output-commit paths, resolve open issues.

---

## Table of Contents

1. [Verification Methodology](#1-verification-methodology)
2. [Canonical Output-Commit Path](#2-canonical-output-commit-path)
3. [Complete Production Path Inventory](#3-complete-production-path-inventory)
4. [Issue Resolution](#4-issue-resolution)
5. [Prior Report Validation Matrix](#5-prior-report-validation-matrix)
6. [Gaps and New Findings](#6-gaps-and-new-findings)

---

## 1. Verification Methodology

Every statement in the prior report was verified against the current production source at commit `234689e`. Source files read in full:

| File | Path | Lines |
|------|------|-------|
| RenderJobExecutionService.java | render-module/…/app/ | 669 |
| RenderArtifactStorageService.java | render-module/…/infrastructure/ | 52 |
| StorageCatalogService.java | storage-module/…/app/ | 70 |
| StorageCatalogPort.java | storage-module/…/api/ | 48 |
| ArtifactRepository.java | storage-module/…/app/ | 80 |
| ArtifactGraphRepository.java | render-module/…/infrastructure/artifact/ | 301 |
| BillingEnforcementService.java | render-module/…/infrastructure/billing/ | 272 |
| RenderBillingRecord.java | render-module/…/infrastructure/billing/ | 102 |
| RenderBillingRecordRepository.java | render-module/…/infrastructure/billing/ | 169 |
| RenderQuotaService.java | render-module/…/app/ | 39 |
| QuotaUsageRepository.java | render-module/…/app/ | 87 |
| ProductRuntimeService.java | render-module/…/app/product/ | 94 |
| RenderJobRepository.java | render-module/…/infrastructure/ | 516 |
| RenderJobStatusHistoryRepository.java | render-module/…/app/ | 51 |
| RenderJobFailureService.java | render-module/…/app/ | 39 |
| RenderJobClaimService.java | render-module/…/app/ | (read) |
| RenderProvider.java | render-module/…/infrastructure/ | 77 |
| FFmpegRenderProvider.java | render-module/…/infrastructure/ffmpeg/ | 530 |
| OutboxBackedNotificationEventPublisher.java | outbox-event-module/…/app/ | 128 |

Additionally: grep for `Ids.newId("art")` across all render providers, grep for `new RenderResult(`, grep for `consumeQuota`.

---

## 2. Canonical Output-Commit Path

The primary output-commit path is `RenderJobExecutionService.finishRenderPhaseInternal()` (lines 321–458). It is called from three entry points:

```
Entry Point 1: execute() [line 167, NO @Transactional]
  → finishRenderPhaseInternal() [line 238]
  Transactional context: NONE (caller must provide)

Entry Point 2: executeAfterSubmit() [line 248, @Transactional]
  → finishRenderPhaseInternal() [line 308]
  Transactional context: Outer @Transactional from RenderOrchestratorService.submitRenderJob()

Entry Point 3: finishRenderPhase() [line 316, @Transactional]
  → finishRenderPhaseInternal() [line 318]
  Transactional context: Own @Transactional
```

### Step-by-step sequence within `finishRenderPhaseInternal()`:

```
finishRenderPhaseInternal(tenantId, jobId)
│
│  [Lines 321-352] Preconditions
├─ 1. Load job record (jOOQ read)
├─ 2. Tenant validation
├─ 3. Early return if COMPLETED
├─ 4. Resolve ai_script if null
├─ 5. Effect entitlement validation
├─ 6. Ensure EXECUTING state (stateMachine.transition + updateStatus)
│
│  [Lines 354-365] Billing Reservation
├─ 7. if billingEnforcementEnabled:
│     billingEnforcementService.reserveQuota(tenantId, jobId, 0.10)
│       ├─ renderQuotaService.consumeQuota(tenantId, "render", 1)     ← QUOTA CONSUMPTION #1
│       │     └─ quotaUsageRepository.incrementUsage() [read-then-write]
│       └─ billingRecordRepository.save()                              ← BILLING RECORD (ESTIMATED)
│             └─ INSERT/UPSERT render_billing_record id="bill-{jobId}"
│
│  [Lines 367-376] Provider Render
├─ 8. assertJobNotInTerminalState(jobId)
├─ 9. executeRenderWithOptionalDag(jobId, projectId, aiScript, profile, tenantId, baseJobId)
│     ├─ Path A: PipelineDagExecutorService.execute() → DagExecutionResult
│     │   └─ Returns RenderResult(artifactId, storageUri, duration, format, resolution)
│     │       artifactId = dag.pipelineResult().artifactId() or Ids.newId("art")
│     └─ Path B: ProviderRuntimeEngine.resolveProvider() → provider.render()
│         └─ FFmpegRenderProvider.render() → RenderResult
│             ├─ Writes file: {storageRoot}/artifacts/{jobId}/output.mp4
│             ├─ artifactId = Ids.newId("art")                           ← NEW ARTIFACT ID
│             └─ storageUri = "localFsStorageProvider://artifacts/{jobId}/output.mp4"
│
│  [Lines 378-389] Billing Finalization
├─ 10. if billingEnforcementEnabled:
│      billingEnforcementService.finalizeCost(tenantId, jobId, "ffmpeg", duration, 0)
│        ├─ costEstimationService.estimate() → actualCost
│        ├─ billingRecordRepository.findByJobId(jobId)
│        ├─ existing.finalize(actualCost, duration, providerId, outputSizeBytes)
│        ├─ billingRecordRepository.save(finalized)                      ← BILLING RECORD (FINALIZED)
│        └─ usageMeteringService.recordUsage() × 2                      ← EXTERNAL METERING
│             idempotency: "job-{jobId}-seconds", "job-{jobId}-bytes"
│      *** FAILURE SWALLOWED (catch + log.warn, no re-throw) ***
│
│  [Lines 391-394] State Transition
├─ 11. stateMachine.transition(EXECUTING → COMPLETING)
├─ 12. updateStatus(jobId, projectId, EXECUTING, COMPLETING, null)
│        ├─ renderJobRepository.updateStatus(jobId, "COMPLETING")
│        ├─ historyRepository.record()                                   ← STATUS HISTORY INSERT
│        └─ notificationEventPublisher.publish(StatusChangedEvent)       ← OUTBOX EVENT
│
│  [Lines 396-407] Blob Upload
├─ 13. contentType = contentTypeForFormat(format)
├─ 14. relativePath = storageUri.replace("localFsStorageProvider://", "")
├─ 15. artifactStorageService.uploadJobOutput(jobId, projectId, artifactId, relativePath, contentType)
│        ├─ Files.readAllBytes(localFile)                                ← FILE READ (entire file)
│        ├─ objectKey = artifactId + "/" + fileName                      ← USES PROVIDER'S artifactId
│        ├─ blobStorage.put(PutObjectCommand("artifacts", objectKey, bytes, contentType))
│        │                                                               ← BLOB WRITE
│        └─ storageCatalogPort.registerArtifact(jobId, projectId, storageRef)
│              └─ StorageCatalogService.registerArtifact()
│                    ├─ newArtifactId = Ids.newId("art")                 ← ⚠️ DIFFERENT ARTIFACT ID
│                    ├─ ArtifactRef(newArtifactId, …, "mp4", "1920x1080", 30L, now)
│                    └─ artifactRepository.save()                        ← ARTIFACT TABLE INSERT
│
│  [Lines 409-441] ArtifactGraph
├─ 16. contentHash = computeContentHash(storageUri)                      ← HASH OF URI STRING
├─ 17. rootNode = ArtifactNode.create(artifactId, jobId, type, storageUri, parents, contentHash)
│        Uses provider's artifactId (from RenderResult)
├─ 18. Optional: timelineNode = ArtifactNode.create(Ids.newId("art-timeline"), …)
├─ 19. artifactGraph = ArtifactGraph.create(jobId, rootNode)
├─ 20. artifactGraphRepository.saveGraph(artifactGraph)
│        ├─ INSERT/UPSERT artifact_node (per node)                       ← ARTIFACT_NODE TABLE
│        └─ INSERT/UPSERT artifact_graph                                 ← ARTIFACT_GRAPH TABLE
│
│  [Line 444] Artifact URI on Job
├─ 21. renderJobRepository.updateArtifactUri(jobId, storageUri)          ← RENDER_JOB UPDATE
│
│  [Lines 446-449] State Completion
├─ 22. stateMachine.transition(COMPLETING → COMPLETED)
├─ 23. updateStatus(jobId, projectId, COMPLETING, COMPLETED, null)
│        ├─ renderJobRepository.updateStatus(jobId, "COMPLETED")
│        ├─ historyRepository.record()                                   ← STATUS HISTORY INSERT
│        └─ notificationEventPublisher.publish(StatusChangedEvent)       ← OUTBOX EVENT
│
│  [Line 450] Post-Completion Quota
├─ 24. quotaService.consumeQuota(tenantId, "render", 1)                 ← QUOTA CONSUMPTION #2
│        └─ quotaUsageRepository.incrementUsage() [read-then-write]
│
│  [Lines 452-454] Events
├─ 25. notificationEventPublisher.publish(ArtifactCreatedEvent)
│        └─ Outbox: artifact.created:{artifactId}                        ← OUTBOX EVENT
├─ 26. eventPublisher.publishEvent(RenderJobCompletedEvent)
│        └─ Spring synchronous: audit, notifications, etc.
│
└─ return jobId
```

---

## 3. Complete Production Path Inventory

### 3.1 Paths That Write Output Blobs

| # | Component | Mechanism | File Path Pattern |
|---|-----------|-----------|-------------------|
| 1 | FFmpegRenderProvider.render() | ProcessToolRunner → ffmpeg subprocess | `{storageRoot}/artifacts/{jobId}/output.mp4` |
| 2 | FFmpegRenderProvider.renderSynthetic() | ProcessToolRunner → ffmpeg testsrc | `{storageRoot}/artifacts/{jobId}/output.mp4` |
| 3 | PipelineDagExecutorService | Delegates to provider(s) | Varies by pipeline stage |
| 4 | RenderArtifactStorageService.uploadJobOutput() | Files.readAllBytes → blobStorage.put() | Object key: `{artifactId}/{fileName}` |
| 5 | All other providers (Remotion, Blender, Natron, Mlt, GStreamer, Libass, Skia, VapourSynth, Shotstack, etc.) | Each writes to `{storageRoot}/artifacts/{jobId}/{provider}-output.mp4` | `{storageRoot}/artifacts/{jobId}/…` |

**Key observation**: Every provider writes to the local filesystem first. `RenderArtifactStorageService` then copies from local FS to blob storage. The local FS write is not transactional and persists even if the DB transaction rolls back.

### 3.2 Paths That Create StorageReference

| # | Component | Method | Key Behavior |
|---|-----------|--------|--------------|
| 1 | BlobStorage.put() | Returns `StorageObjectRef(bucket, objectKey)` | Created by the blob storage provider (LocalFs or S3) |
| 2 | RenderArtifactStorageService | Constructs `PutObjectCommand("artifacts", objectKey, bytes, contentType)` | Object key = `{artifactId}/{fileName}` using provider's artifactId |

### 3.3 Paths That Create/Update Artifact

| # | Table | Component | Method | ID Generation |
|---|-------|-----------|--------|---------------|
| 1 | `artifact` | ArtifactRepository.save() | Plain INSERT (no UPSERT) | `Ids.newId("art")` in StorageCatalogService.registerArtifact() |
| 2 | `artifact_node` | ArtifactGraphRepository.saveNode() | INSERT/UPSERT on `id` | ID from RenderResult (provider-generated) |
| 3 | `artifact_graph` | ArtifactGraphRepository.saveGraph() | INSERT/UPSERT on `graph_id` | Graph ID from ArtifactGraph.create() |

**⚠️ Critical**: Rows 1 and 2 use **different artifact IDs** for the same physical blob. See [§4.1](#41-artifact-id-mismatch) for resolution.

### 3.4 Paths That Create/Update FINAL_RENDER Product

**None exist in the production output-commit path.**

`ProductRuntimeService` is a standalone service with `register()` and `markReady()` methods. No code in `finishRenderPhaseInternal()`, no event listener, and no outbox consumer bridges `RenderJobCompletedEvent` → `Product.register()`. Products must be created explicitly by external callers.

### 3.5 Paths That Reserve/Consume Quota

| # | Location | Call | When |
|---|----------|------|------|
| 1 | BillingEnforcementService.reserveQuota() line 144 | `renderQuotaService.consumeQuota(tenantId, "render", 1)` | Before render execution |
| 2 | RenderJobExecutionService line 450 | `quotaService.consumeQuota(tenantId, "render", 1)` | After COMPLETING→COMPLETED transition |

Both call `QuotaUsageRepository.incrementUsage()` which is a **read-then-write** pattern with no locking.

### 3.6 Paths That Write Billing Ledger

| # | Location | Write | Table |
|---|----------|-------|-------|
| 1 | BillingEnforcementService.reserveQuota() | `billingRecordRepository.save(record)` | `render_billing_record` (status=ESTIMATED, id="bill-{jobId}") |
| 2 | BillingEnforcementService.finalizeCost() | `billingRecordRepository.save(finalized)` | `render_billing_record` (status=FINALIZED, UPSERT) |
| 3 | BillingEnforcementService.finalizeCost() | `usageMeteringService.recordUsage()` × 2 | External metering system |

### 3.7 Paths That Set RenderJob State

| Transition | Location | Mechanism |
|------------|----------|-----------|
| → SELECTING_PROVIDER | execute() line 186 / executeAfterSubmit() line 266 | claimService.claimForSelection() (REQUIRES_NEW CAS) or direct update |
| SELECTING_PROVIDER → PROVIDER_SELECTED | execute() line 222 / executeAfterSubmit() line 294 | stateMachine.transition + updateStatus |
| PROVIDER_SELECTED → EXECUTING | execute() line 227 / executeAfterSubmit() line 298 | stateMachine.transition + updateStatus |
| EXECUTING → COMPLETING | finishRenderPhaseInternal() line 392-394 | stateMachine.transition + updateStatus |
| COMPLETING → COMPLETED | finishRenderPhaseInternal() line 447-449 | stateMachine.transition + updateStatus |
| → FAILED (durable) | RenderJobFailureService.recordDurableFailure() | REQUIRES_NEW CAS via markActiveJobFailed() |
| → FAILED (in-flow) | failJob() helper line 621-625 | updateStatus + RenderJobFailedEvent |

**Failed state CAS** (`markActiveJobFailed`) accepts jobs in: SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING.

---

## 4. Issue Resolution

### 4.1 Artifact ID Mismatch

**Status**: CONFIRMED — Real bug, severity CRITICAL.

**Root cause**: `StorageCatalogPort.registerArtifact()` interface signature does **not** accept an artifactId parameter:

```java
// StorageCatalogPort.java line 21
ArtifactRef registerArtifact(String renderJobId, String projectId, StorageObjectRef providerRef);
```

`StorageCatalogService.registerArtifact()` (line 31) generates its own ID:

```java
String artifactId = Ids.newId("art");
```

Meanwhile, `RenderArtifactStorageService.uploadJobOutput()` receives the provider's `artifactId` parameter and uses it for the blob object key (`artifactId + "/" + fileName`), but **never passes it** to `registerArtifact()`.

**Consequence**: Two different artifact IDs exist for the same physical blob:
- `artifact` table row: ID from `StorageCatalogService` (auto-generated)
- `artifact_node` table row: ID from `RenderResult.artifactId()` (provider-generated)
- Blob object key: uses provider's artifactId

The `artifact` table and `artifact_node` table are **disconnected** — they reference different IDs for the same output. Queries joining on artifact ID will return no results.

**Fix direction**: Either:
1. Accept artifactId in `StorageCatalogPort.registerArtifact()` and pass through from caller, or
2. Have `StorageCatalogService` return its generated ID and use that downstream for the ArtifactGraph, or
3. Unify into a single artifact identity service.

### 4.2 Double Quota Accounting

**Status**: CONFIRMED — Real bug, severity HIGH.

**Evidence** (two call sites, same execution path):

1. `BillingEnforcementService.reserveQuota()` line 144:
   ```java
   renderQuotaService.consumeQuota(tenantId, "render", 1);
   ```

2. `RenderJobExecutionService.finishRenderPhaseInternal()` line 450:
   ```java
   quotaService.consumeQuota(tenantId, "render", 1);
   ```

Both call `QuotaUsageRepository.incrementUsage()` which is read-then-write. On a successful render, quota is incremented by **2**, not 1.

**Additional risk**: `incrementUsage()` has no optimistic locking or `SELECT FOR UPDATE`. Concurrent calls can lose increments (last-write-wins race).

**Fix direction**: Remove the second `consumeQuota` call (line 450) since reservation already accounts for the unit, or introduce an idempotency key on quota consumption.

### 4.3 Content Hash Semantics

**Status**: CONFIRMED — Real limitation, severity MEDIUM.

**Evidence** (line 664-668):

```java
private String computeContentHash(String uri) {
    if (uri == null) return "";
    return "hash-" + Integer.toHexString(uri.hashCode());
}
```

The hash is `hash-{Integer.toHexString(uri.hashCode())}` — a Java `String.hashCode()` of the URI string, not a cryptographic hash of the file content.

**Consequences**:
- Two different files at the same URI → same hash (false positive dedup)
- Same content at different URIs → different hash (missed dedup)
- `String.hashCode()` is 32-bit → high collision probability at scale
- Hash is deterministic for same URI → idempotent for retries (this is actually correct for the retry case)

**Fix direction**: Replace with SHA-256 of actual file bytes, computed during upload (before or alongside `Files.readAllBytes()`).

### 4.4 Product Registration Gap

**Status**: CONFIRMED — Real gap, severity MEDIUM.

**Evidence**: No code in `finishRenderPhaseInternal()` references `ProductRuntimeService`, `Product`, or any product-related domain. No event listener bridges `RenderJobCompletedEvent` → `Product.register()`. The `OutboxBackedNotificationEventPublisher` writes events to the outbox, but no outbox consumer is registered to create products.

`ProductRuntimeService.register()` requires:
- `product.status() == ProductStatus.REGISTERED`
- `product.hasProvenance()` (needs `ownerAssetId`, `producerId`, or `sourceTimelineRevisionId`)

These fields must be populated from the render context (jobId, projectId, artifactId, storageUri), but no code does this mapping.

---

## 5. Prior Report Validation Matrix

| Prior Report Claim | Verified? | Notes |
|--------------------|-----------|-------|
| `finishRenderPhaseInternal()` lines 321-458 is the canonical path | ✅ | Lines 321-458 confirmed |
| Runs inside @Transactional from caller | ✅ | But `execute()` path (line 238) has no @Transactional on the method itself |
| Billing reservation: `reserveQuota()` → `consumeQuota()` + billing record INSERT | ✅ | Lines 356-365, BillingEnforcementService lines 137-154 |
| Provider render: `provider.render()` → `RenderResult` with fresh artifactId | ✅ | All 12+ providers use `Ids.newId("art")` |
| Blob upload: `Files.readAllBytes()` → `blobStorage.put()` → `storageCatalogPort.registerArtifact()` | ✅ | RenderArtifactStorageService lines 34-47 |
| `StorageCatalogService.registerArtifact()` generates its own artifactId | ✅ | Line 31: `Ids.newId("art")` — different from provider's |
| ArtifactGraph uses provider's artifactId | ✅ | Line 411: `artifactId = renderResult.artifactId()` |
| Content hash is URI-based, not content-based | ✅ | Line 664-668: `Integer.toHexString(uri.hashCode())` |
| Billing finalization failure is swallowed | ✅ | Lines 385-388: `catch (Exception e) { log.warn(…); }` |
| Double quota consumption | ✅ | Line 144 (reserveQuota) + line 450 (post-completion) |
| `quota_usage.incrementUsage()` is read-then-write without locking | ✅ | QuotaUsageRepository lines 23-42 |
| `RenderBillingRecord` ID is deterministic: `"bill-" + jobId` | ✅ | RenderBillingRecord.create() line 28 |
| `ArtifactRepository.save()` is plain INSERT (no UPSERT) | ✅ | ArtifactRepository lines 22-32 |
| `ArtifactGraphRepository.saveGraph()` uses onConflict.doUpdate() | ✅ | Lines 61-67 (nodes), lines 103-107 (graph) |
| `RenderJobStatusHistoryRepository.record()` uses new ID each time | ✅ | Line 24: `Ids.newId("rsh")` |
| Outbox events use idempotency keys | ✅ | OutboxBackedNotificationEventPublisher: `artifact.created:{artifactId}`, `render.job.completed:{jobId}`, `render.job.status.changed:{jobId}:{old}:{new}` |
| No automatic Product creation from render completion | ✅ | No reference to ProductRuntimeService in finishRenderPhaseInternal |
| `RenderJobFailureService.recordDurableFailure()` uses REQUIRES_NEW | ✅ | Line 29: `@Transactional(propagation = Propagation.REQUIRES_NEW)` |
| BillingEnforcementService is `@Autowired(required = false)` | ✅ | RenderJobExecutionService line 106 |
| In-memory state machine (ConcurrentHashMap) | ⚠️ | State machine is instantiated per-service (line 156: `new RenderJobStateMachine()`), not shared. DB is authoritative. |
| State machine validates transitions in-memory | ✅ | `updateStatus()` calls `stateMachine.validateTransition()` |
| FFmpeg writes to `{storageRoot}/artifacts/{jobId}/output.mp4` | ✅ | FFmpegRenderProvider line 83 |
| `Files.readAllBytes()` loads entire file into memory | ✅ | RenderArtifactStorageService line 40 |

---

## 6. Gaps and New Findings

### 6.1 Transaction Boundary Gap for `execute()` Path (NEW)

**Severity**: MEDIUM

The `execute()` method (line 167) is **not** annotated with `@Transactional`. It calls `finishRenderPhaseInternal()` directly (line 238), not through the `@Transactional`-annotated `finishRenderPhase()` (line 316). This means the execute path has **no explicit transactional context** for `finishRenderPhaseInternal()`.

However, in practice, `execute()` is likely called from a controller that may or may not have `@Transactional`. If called without an outer transaction, all DB operations in `finishRenderPhaseInternal()` run in auto-commit mode — each statement commits independently, making partial failure non-atomic.

**Contrast**: `executeAfterSubmit()` is `@Transactional` (line 247) and `finishRenderPhase()` is `@Transactional` (line 315) — both provide transactional context.

### 6.2 Artifact Table Has Hardcoded Metadata (NEW)

**Severity**: LOW

`StorageCatalogService.registerArtifact()` (lines 32-35) creates `ArtifactRef` with hardcoded values:
```java
"mp4", "1920x1080", 30L
```

These are not derived from the actual render result. Non-MP4 outputs (DASH, HLS, WebM) would be registered with incorrect format metadata.

### 6.3 `markActiveJobFailed()` Accepts COMPLETING State (NEW)

**Severity**: LOW

`RenderJobRepository.markActiveJobFailed()` (line 170) accepts jobs in `COMPLETING` state:
```java
field("status").in("SELECTING_PROVIDER", "PROVIDER_SELECTED", "EXECUTING", "COMPLETING")
```

This means a job can transition from COMPLETING → FAILED via `recordDurableFailure()`, but the in-memory state machine may not have a valid transition for COMPLETING → FAILED. The DB-level CAS wins regardless of the in-memory state machine.

### 6.4 All Providers Share Same Output Path Pattern (CONFIRMED)

All providers write to `{storageRoot}/artifacts/{jobId}/output.mp4` (or `{provider}-output.mp4`). The `MockRenderProvider` is unique in using `artifactId` in the storage URI (`artifacts/{artifactId}/output.mp4`), but this is only for testing.

### 6.5 `render_job.artifact_uri` Stores Provider-Prefixed URI (NEW)

**Severity**: LOW

`renderJobRepository.updateArtifactUri(jobId, storageUri)` stores the full URI including the provider prefix (e.g., `localFsStorageProvider://artifacts/{jobId}/output.mp4`). Consumers must strip the prefix to resolve to a local path, as `RenderArtifactStorageService` does on line 401.

### 6.6 Timeline JSON Artifact Has Synthetic URI (NEW)

**Severity**: LOW

When `renderResult.duration() > 0`, the code creates a timeline artifact node (lines 425-434) with URI `"timeline://" + jobId + "/timeline.json"` — a non-existent URI. The content hash for this is `computeContentHash("timeline://" + jobId)`. This artifact node references a file that doesn't exist in blob storage.

---

## Summary

### Critical Issues (from prior report, all CONFIRMED)

1. **Artifact ID Mismatch**: `StorageCatalogService` generates its own ID; `ArtifactGraph` uses provider's ID. Two tables (`artifact` vs `artifact_node`) reference different IDs for the same blob. Fix: plumb artifactId through the `StorageCatalogPort.registerArtifact()` interface.

2. **Double Quota Accounting**: `consumeQuota()` called at reservation (line 144 of BillingEnforcementService) AND at completion (line 450 of finishRenderPhaseInternal). Successful renders consume 2 units. Fix: remove the second call.

3. **Content Hash is URI-Based**: `computeContentHash()` hashes the URI string, not file content. Defeats deduplication. Fix: hash actual file bytes during upload.

4. **No Product Registration Bridge**: No code connects render completion to Product creation. Fix: add event listener or explicit call in the output-commit path.

### Medium Issues (all CONFIRMED)

5. **Quota Read-Then-Write Race**: No locking on `incrementUsage()`.
6. **Billing Finalization Swallowed**: Errors logged but don't fail the job.
7. **Memory Pressure**: `Files.readAllBytes()` loads entire file.
8. **Orphan Blobs**: Non-transactional writes persist after TX rollback.

### New Findings

9. **`execute()` path has no @Transactional**: Direct call to `finishRenderPhaseInternal()` bypasses the `@Transactional` on `finishRenderPhase()`.
10. **Hardcoded artifact metadata**: `"mp4", "1920x1080", 30L` in StorageCatalogService regardless of actual output.
11. **Timeline JSON artifact references non-existent URI**: `"timeline://"` scheme doesn't resolve to any blob.
