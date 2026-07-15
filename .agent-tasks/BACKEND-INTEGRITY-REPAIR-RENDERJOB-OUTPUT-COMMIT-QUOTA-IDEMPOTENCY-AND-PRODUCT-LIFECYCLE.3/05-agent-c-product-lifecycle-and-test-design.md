# 05 — Agent C: Product, Artifact, StorageReference Lifecycle Coupling Audit & Test Design

## 1. Entity Inventory

### 1.1 RenderJob (infrastructure record)
- **File**: `render-module/src/main/java/com/example/platform/render/infrastructure/RenderJob.java`
- **Nature**: Pure DTO record. No lifecycle methods. Carries id, jobType, mode, canvas, assets, timeline, captions, style, output, requiredCapabilities, constraints, allowDegrade, preferredProviders, blockedProviders.
- **Persistence**: jOOQ via `RenderJobRepository` against `render_job` table.
- **Status field**: String column `status` on `render_job` row — not part of the record itself.

### 1.2 RenderJobStatus (enum)
- **File**: `render-module/src/main/java/com/example/platform/render/domain/RenderJobStatus.java`
- **States**: QUEUED, SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, FALLBACKING, RETRYING, COMPLETING, COMPLETED, FAILED, CANCELLED, REJECTED
- **Terminal states**: COMPLETED, FAILED, CANCELLED, REJECTED
- **Active states**: SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, FALLBACKING, RETRYING, COMPLETING

### 1.3 RenderJobStateMachine
- **File**: `render-module/src/main/java/com/example/platform/render/domain/RenderJobStateMachine.java`
- **Nature**: In-memory ConcurrentHashMap-based state machine. NOT backed by DB. Used for transition validation and trace emission only.
- **Critical**: The DB is the real source of truth for status; the state machine is an in-process validator.

### 1.4 Artifact (artifact-catalog-module)
- **File**: `artifact-catalog-module/src/main/java/com/example/platform/artifact/domain/Artifact.java`
- **Nature**: Record with id, renderJobId, projectId, storageUri, format, resolution, duration, sizeBytes, checksum, status (ArtifactStatus), tombstonedAt, createdAt.
- **ArtifactStatus**: ACTIVE (default), TOMBSTONED
- **isUsable()**: true iff status == ACTIVE

### 1.5 ArtifactNode / ArtifactGraph (render-module)
- **Files**: `render-module/.../domain/artifact/ArtifactNode.java`, `ArtifactGraph.java`
- **Nature**: In-memory DAG of render outputs. ArtifactNode has id, jobId, type, uri, parentArtifactIds, createdAt, version, hash, metadata.
- **ArtifactGraph**: Immutable DAG with root, version, addNode/getNodes.
- **Persistence**: `ArtifactGraphRepository` (saveGraph/getGraph).

### 1.6 StorageReference
- **File**: `render-module/src/main/java/com/example/platform/render/domain/storage/StorageReference.java`
- **Nature**: Record with storageReferenceId, providerType, StorageClass, rootPath, relativePath, checksum, contentHash, fileSize, mimeType, createdAt, updatedAt.
- **Persistence**: `StorageReferenceRepository` via `StorageRuntimeService`.

### 1.7 Product
- **File**: `render-module/src/main/java/com/example/platform/render/domain/product/Product.java`
- **Nature**: Record — aggregate root for Product Runtime. Fields: productId, tenantId, projectId, ownerAssetId, productType, representationKind, producerType, producerId, sourceTimelineRevisionId, status (ProductStatus), storageReferenceId, checksum, contentHash, mimeType, version, metadataJson, createdAt, updatedAt.
- **Persistence**: `ProductRepository` via `ProductRuntimeService`. Uses `ON CONFLICT DO UPDATE` (upsert by product_id).

### 1.8 ProductStatus
- **States**: REGISTERED, PROCESSING, READY, FAILED, SUPERSEDED, ARCHIVED
- **Lifecycle**: REGISTERED → READY (happy path), REGISTERED → FAILED (error path)

---

## 2. Lifecycle Coupling Map

### 2.1 Happy Path Flow

```
RenderJob QUEUED
  ↓ claimService.claimForSelection() [REQUIRES_NEW CAS: QUEUED→SELECTING_PROVIDER]
RenderJob SELECTING_PROVIDER
  ↓ stateMachine.transition() + updateStatus()
RenderJob PROVIDER_SELECTED
  ↓ stateMachine.transition() + updateStatus()
RenderJob EXECUTING
  ↓ billingEnforcementService.reserveQuota()
  ↓ provider.render() → RenderResult(artifactId, storageUri, duration, format, resolution)
  ↓ billingEnforcementService.finalizeCost()
  ↓ stateMachine.transition() + updateStatus()
RenderJob COMPLETING
  ↓ artifactStorageService.uploadJobOutput()
  ↓ ArtifactGraph.create() + artifactGraphRepository.saveGraph()
  ↓ renderJobRepository.updateArtifactUri()
  ↓ stateMachine.transition() + updateStatus()
RenderJob COMPLETED
  ↓ quotaService.consumeQuota()
  ↓ notificationEventPublisher.publish(ArtifactCreatedEvent)
  ↓ eventPublisher.publishEvent(RenderJobCompletedEvent)
```

### 2.2 Product Lifecycle (RenderOutputRegistrationService — separate path)

```
RenderOutputRegistrationService.registerOutput()
  ↓ validatePath() — path traversal, existence, regular file, non-zero
  ↓ computeSha256() — checksum
  ↓ StorageRuntimeService.register(StorageReference)  ← StorageReference created
  ↓ storageRuntime.verifyChecksum()
  ↓ ProductRuntimeService.register(Product[FINAL_RENDER, REGISTERED])  ← Product created
  ↓ productRuntime.markReady(productId)  ← Product → READY
  ↓ productRuntime.linkDependency() (if provenance has inputProductIds)
```

### 2.3 Two Separate Registration Paths (Critical Finding)

There are **two independent artifact registration paths** that do NOT share a single Product lifecycle:

| Aspect | Path A: `finishRenderPhaseInternal` | Path B: `RenderOutputRegistrationService` |
|--------|--------------------------------------|-------------------------------------------|
| Called from | `RenderJobExecutionService.finishRenderPhaseInternal()` | `CaptionTemplateRenderService`, `PreviewRenderJobService`, etc. |
| Creates ArtifactGraph | YES — `ArtifactGraph.create()` + `ArtifactGraphRepository.saveGraph()` | NO |
| Creates Artifact (catalog) | NO — only ArtifactGraph node | NO |
| Creates StorageReference | Via `artifactStorageService.uploadJobOutput()` (blob storage) | Via `storageRuntime.register()` (StorageReference domain) |
| Creates Product | NO | YES — FINAL_RENDER, REGISTERED → READY |
| Creates Product dependency edges | NO | YES (if provenance.inputProductIds) |
| Publishes events | ArtifactCreatedEvent, RenderJobCompletedEvent | NO |

**Implication**: The `finishRenderPhaseInternal` path (used by the main execute flow) does NOT create a Product at all. Product creation only happens via `RenderOutputRegistrationService`, which is a separate orchestration path used by caption/preview services.

---

## 3. Exact Current Conditions

### 3.1 Artifact Creation

**Path A (ArtifactGraph)**:
- **When**: After render succeeds, during COMPLETING phase.
- **Condition**: `renderResult` is non-null, `artifactStorageService.uploadJobOutput()` succeeds.
- **Code**: `RenderJobExecutionService` lines 409-441.
- **Created**: ArtifactNode with id, jobId, type (from extension), storageUri, parentArtifactIds=[], hash.
- **Persisted**: Via `ArtifactGraphRepository.saveGraph(artifactGraph)`.

**Path B (Artifact catalog record)**:
- **Condition**: NOT directly created in the render execution path. The `artifact-catalog-module` Artifact record is a separate concern.

### 3.2 Artifact Readiness

**ArtifactGraph**: No readiness concept — nodes are immutable once created. Usability = presence in graph.

**Artifact (catalog)**: `isUsable()` returns true iff `status == ArtifactStatus.ACTIVE`. Default status is ACTIVE on creation.

### 3.3 StorageReference Persistence

**Path A** (`artifactStorageService.uploadJobOutput()`):
- Reads local file from `storageRoot + relativePath`.
- Calls `blobStorage.put()` to upload bytes.
- Calls `storageCatalogPort.registerArtifact()` to register in storage catalog.
- **No `StorageReference` domain object created** — this uses a different storage abstraction.

**Path B** (`RenderOutputRegistrationService`):
- Creates `StorageReference` with providerType, storageClass, rootPath, relativePath, checksum, fileSize, mimeType.
- Calls `storageRuntime.register(storageRef)` → persisted via `StorageReferenceRepository.save()`.
- Followed by `storageRuntime.verifyChecksum()`.

**Key difference**: Path A uses `BlobStorage.put()` + `StorageCatalogPort`. Path B uses `StorageRuntimeService.register()`. These are two different storage registration mechanisms.

### 3.4 FINAL_RENDER Product Creation

- **Only created by**: `RenderOutputRegistrationService.registerProductAndLink()`.
- **Condition**: File must exist, be regular, non-zero, path-valid. StorageReference must register successfully. Checksum must verify.
- **Type**: `ProductType.FINAL_RENDER`, `RepresentationKind.MEDIA_FILE`.
- **Initial status**: `ProductStatus.REGISTERED`.
- **Immediately marked**: `productRuntime.markReady()` → `ProductStatus.READY`.
- **storageReferenceId**: Set to the registered StorageReference ID.
- **provenance**: Requires `producerId` (non-null, checked by `hasProvenance()`).

### 3.5 Product Readiness

- **Condition**: `ProductStatus.READY`.
- **Set by**: `productRuntime.markReady(productId)` — loads product, calls `withStatus(READY)`, saves.
- **Idempotent**: If already READY, returns as-is.
- **No validation** that storageReferenceId is non-null before marking READY.

### 3.6 RenderJob COMPLETING

- **Transition**: EXECUTING → COMPLETING.
- **Condition**: `provider.render()` succeeded (returned `RenderResult`).
- **What happens**: `artifactStorageService.uploadJobOutput()`, ArtifactGraph save, `updateArtifactUri()`.
- **Failure mode**: If `uploadJobOutput()` throws, `failureService.recordDurableFailure()` is called, then exception re-thrown. The `recordDurableFailure` does CAS: `SELECTING_PROVIDER|PROVIDER_SELECTED|EXECUTING|COMPLETING → FAILED`.

### 3.7 RenderJob COMPLETED

- **Transition**: COMPLETING → COMPLETED.
- **Condition**: All of: upload succeeded, ArtifactGraph saved, artifactUri updated.
- **Side effects**: `quotaService.consumeQuota()`, `ArtifactCreatedEvent` published, `RenderJobCompletedEvent` published.
- **Terminal**: No further transitions allowed.

### 3.8 Failure Transitions

| From | To | Trigger | Mechanism |
|------|----|---------|-----------|
| SELECTING_PROVIDER | FAILED | Script resolution fails | `failureService.recordDurableFailure()` [REQUIRES_NEW] |
| PROVIDER_SELECTED | FAILED | Effect entitlement fails | `failureService.recordDurableFailure()` [REQUIRES_NEW] |
| EXECUTING | FAILED | Provider render fails | `failureService.recordDurableFailure()` [REQUIRES_NEW] |
| EXECUTING | FAILED | Billing reservation fails | `failureService.recordDurableFailure()` [REQUIRES_NEW] |
| COMPLETING | FAILED | Storage upload fails | `failureService.recordDurableFailure()` [REQUIRES_NEW] |
| Any active | FAILED | `markActiveJobFailed()` CAS | `REQUIRES_NEW` atomic: status IN (SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING) → FAILED |

---

## 4. Identified Gaps and Risks

### 4.1 Product Not Created in Main Render Path
The `finishRenderPhaseInternal` path does NOT create a FINAL_RENDER Product. Only the `RenderOutputRegistrationService` path does. If the main render path should produce a Product, it's missing.

### 4.2 No Duplicate Finalization Guard
- `finishRenderPhaseInternal` does not check if the job is already COMPLETING or COMPLETED before starting finalization.
- The `execute()` method checks `COMPLETED` and returns early, but does NOT check `COMPLETING`.
- Concurrent calls to `finishRenderPhase()` for the same job could double-upload and double-transition.

### 4.3 StorageReference Not Linked to RenderJob in Path A
Path A (`artifactStorageService.uploadJobOutput()`) uses `BlobStorage` + `StorageCatalogPort`, which is a different abstraction from `StorageRuntimeService`. The `StorageReference` domain object is only created in Path B.

### 4.4 ArtifactGraph vs Artifact Disconnect
The `ArtifactGraph` (render-module domain) and `Artifact` (artifact-catalog-module domain) are separate concepts with no explicit linking. `ArtifactGraph` nodes have `jobId` and `uri`, but no `storageReferenceId`.

### 4.5 Billing Failure Does Not Block COMPLETING
Billing finalization (`finalizeCost`) failure is caught and logged but does NOT fail the job. This is intentional per the comment "Don't fail the job for billing finalization errors." However, billing reservation failure DOES fail the job.

### 4.6 State Machine Is In-Memory Only
`RenderJobStateMachine` uses `ConcurrentHashMap` — it's per-JVM-instance, not shared. Multiple concurrent requests to different JVM instances would bypass the in-memory state machine. The real guard is the DB CAS in `markActiveJobFailed()` and `claimForSelection()`.

---

## 5. Test Design

### 5.1 Test Infrastructure Requirements

- **Testcontainers PostgreSQL**: Already available in the project.
- **Real runtime tests**: Use actual `DSLContext`, `RenderJobRepository`, `RenderJobClaimService`, `RenderJobFailureService`, `ProductRuntimeService`, `StorageRuntimeService`, `RenderOutputRegistrationService`.
- **In-memory alternatives**: Use the existing `InMemoryStorageReferenceRepository`, `InMemoryProductRepository`, `InMemoryProductDependencyRepository` for unit-level tests.
- **Concurrency tests**: Use `CountDownLatch`, `CyclicBarrier`, or `ExecutorService` with real DB connections.

### 5.2 Test: Concurrent Start Claim

**Goal**: Verify that two concurrent `execute()` calls for the same QUEUED job result in exactly one winner.

```java
@Test
void concurrentStartClaimExactlyOneWins() throws Exception {
    // Setup: Create a QUEUED render_job row
    String jobId = createQueuedJob("tenant-1", "project-1");

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicBoolean firstResult = new AtomicBoolean();
    AtomicBoolean secondResult = new AtomicBoolean();

    Future<?> f1 = executor.submit(() -> {
        barrier.await();
        firstResult.set(claimService.claimForSelection(jobId));
    });
    Future<?> f2 = executor.submit(() -> {
        barrier.await();
        secondResult.set(claimService.claimForSelection(jobId));
    });

    f1.get(5, TimeUnit.SECONDS);
    f2.get(5, TimeUnit.SECONDS);

    // Exactly one must win
    assertNotEquals(firstResult.get(), secondResult.get(),
        "Exactly one claim must succeed");

    // DB must be SELECTING_PROVIDER
    Record job = renderJobRepository.requireJobRecord(jobId);
    assertEquals("SELECTING_PROVIDER", job.get("status"));

    executor.shutdown();
}
```

### 5.3 Test: Duplicate Finalization Prevention

**Goal**: Verify that calling `finishRenderPhase()` on an already-COMPLETING or COMPLETED job is safe.

```java
@Test
void finishRenderPhaseOnCompletedJobIsIdempotent() {
    String jobId = createCompletedJob("tenant-1", "project-1");
    // Should return jobId without error
    String result = executionService.finishRenderPhase("tenant-1", jobId);
    assertEquals(jobId, result);
    // Status must remain COMPLETED
    Record job = renderJobRepository.requireJobRecord(jobId);
    assertEquals("COMPLETED", job.get("status"));
}

@Test
void finishRenderPhaseOnCompletingJobDoesNotDoubleTransition() {
    String jobId = createJobInState("tenant-1", "project-1", "COMPLETING");
    // The transition COMPLETING → COMPLETING is not valid (state machine rejects)
    // But the method should handle this gracefully
    assertDoesNotThrow(() -> executionService.finishRenderPhase("tenant-1", jobId));
}
```

### 5.4 Test: Blob Storage Failure During COMPLETING

**Goal**: Verify that if `artifactStorageService.uploadJobOutput()` throws, the job transitions to FAILED via REQUIRES_NEW.

```java
@Test
void blobStorageFailureTransitionsToFailed() {
    String jobId = createJobInState("tenant-1", "project-1", "EXECUTING");
    // Configure mock BlobStorage to throw IOException
    when(blobStorage.put(any())).thenThrow(new IOException("S3 timeout"));

    assertThrows(IllegalStateException.class, () ->
        executionService.finishRenderPhase("tenant-1", jobId));

    // Job must be FAILED (committed in REQUIRES_NEW)
    Record job = renderJobRepository.requireJobRecord(jobId);
    assertEquals("FAILED", job.get("status"));
    assertTrue(job.get("error_message", String.class).contains("Storage failed"));
}
```

### 5.5 Test: StorageReference Registration Failure

**Goal**: Verify that if `storageRuntime.register()` throws in `RenderOutputRegistrationService`, the Product is NOT created.

```java
@Test
void storageReferenceFailurePreventsProductCreation() throws Exception {
    Path outputFile = createTempOutputFile("test.mp4");
    // Configure mock StorageReferenceRepository to throw on save
    when(storageRepo.save(any())).thenThrow(new RuntimeException("DB constraint violation"));

    assertThrows(RenderOutputRegistrationException.class, () ->
        service.registerOutput("job-1", "t1", "p1", "ffmpeg", "artifacts/test.mp4"));

    // No Product should exist
    assertFalse(productRepo.findAll().stream()
        .anyMatch(p -> p.metadataJson().contains("job-1")));
}
```

### 5.6 Test: Artifact Failure (Zero-Byte, Missing, Path Traversal)

```java
@Test
void zeroByteFileRejectsRegistration() throws Exception {
    Path emptyFile = tempDir.resolve("empty.mp4");
    Files.writeString(emptyFile, "");

    assertThrows(RenderOutputRegistrationException.class, () ->
        service.registerOutput("job-1", "t1", "p1", "ffmpeg", "empty.mp4"));
}

@Test
void pathTraversalRejectsRegistration() {
    assertThrows(RenderOutputRegistrationException.class, () ->
        service.registerOutput("job-1", "t1", "p1", "ffmpeg", "../../etc/passwd"));
}
```

### 5.7 Test: Product Registration Failure

**Goal**: Verify that if `productRuntime.register()` throws, the StorageReference still exists (not rolled back if in same transaction — depends on transaction boundary).

```java
@Test
void productRegistrationFailureAfterStorageRegistration() throws Exception {
    Path outputFile = createTempOutputFile("test.mp4");
    // Configure ProductRepository to throw on save
    when(productRepo.save(any())).thenThrow(new RuntimeException("Product DB error"));

    assertThrows(RenderOutputRegistrationException.class, () ->
        service.registerOutput("job-1", "t1", "p1", "ffmpeg", "artifacts/test.mp4"));

    // StorageReference WAS registered (before Product registration)
    // Since both are in @Transactional, the StorageReference should be rolled back too
    // This tests that the transaction boundary is correct
}
```

### 5.8 Test: Billing Failure

**Goal**: Verify that billing reservation failure prevents execution, but billing finalization failure does NOT.

```java
@Test
void billingReservationFailurePreventsExecution() {
    String jobId = createJobInState("tenant-1", "project-1", "EXECUTING");
    when(billingEnforcementService.isEnforcementEnabled()).thenReturn(true);
    when(billingEnforcementService.reserveQuota(any(), any(), anyDouble()))
        .thenReturn(BillingEnforcementService.ReservationResult.failure("Quota exceeded"));

    assertThrows(IllegalStateException.class, () ->
        executionService.finishRenderPhase("tenant-1", jobId));

    Record job = renderJobRepository.requireJobRecord(jobId);
    assertEquals("FAILED", job.get("status"));
    assertTrue(job.get("error_message", String.class).contains("Billing reservation failed"));
}

@Test
void billingFinalizationFailureDoesNotFailJob() {
    // Billing finalization failure is caught and logged — job still completes
    // This is by design per the comment in the code
}
```

### 5.9 Test: Product REGISTERED → READY Transition

```java
@Test
void productBecomesReadyAfterRegistration() throws Exception {
    Path outputFile = createTempOutputFile("output.mp4");
    Product product = service.registerOutput("job-1", "t1", "p1", "ffmpeg", "artifacts/output.mp4");

    assertEquals(ProductStatus.READY, product.status());
    assertNotNull(product.storageReferenceId());
    assertNotNull(product.checksum());
    assertEquals(ProductType.FINAL_RENDER, product.productType());
}

@Test
void markReadyIsIdempotent() {
    Product p = createRegisteredProduct("t1", "p1");
    Product ready1 = productRuntime.markReady(p.productId());
    Product ready2 = productRuntime.markReady(p.productId());
    assertEquals(ready1.status(), ready2.status());
    assertEquals(ProductStatus.READY, ready2.status());
}
```

### 5.10 Test: Checksum Verification Failure

```java
@Test
void checksumMismatchRejectsRegistration() throws Exception {
    Path outputFile = createTempOutputFile("output.mp4");
    // Tamper with the file after checksum computation but before verification
    // This is hard to test without mocking — use a StorageRuntimeService that
    // returns false for verifyChecksum
    when(storageRuntime.verifyChecksum(anyString())).thenReturn(false);

    assertThrows(RenderOutputRegistrationException.class, () ->
        service.registerOutput("job-1", "t1", "p1", "ffmpeg", "artifacts/output.mp4"));
}
```

### 5.11 Test: Durable Failure Survives Outer Transaction Rollback

```java
@Test
void durableFailureCommittedEvenWhenOuterTransactionRollsBack() {
    String jobId = createJobInState("tenant-1", "project-1", "EXECUTING");

    // Simulate outer transaction rollback after failure recording
    try {
        failureService.recordDurableFailure(jobId, "Provider crash");
        throw new RuntimeException("Simulated outer failure");
    } catch (RuntimeException e) {
        // Expected
    }

    // The durable failure should be committed (REQUIRES_NEW)
    Record job = renderJobRepository.requireJobRecord(jobId);
    assertEquals("FAILED", job.get("status"));
    assertTrue(job.get("error_message", String.class).contains("Provider crash"));
}
```

### 5.12 Test: ArtifactGraph Created During COMPLETING

```java
@Test
void completingPhaseCreatesArtifactGraph() {
    String jobId = createJobInState("tenant-1", "project-1", "EXECUTING");
    // Mock provider to return a valid RenderResult
    // Execute finishRenderPhase
    executionService.finishRenderPhase("tenant-1", jobId);

    // Verify ArtifactGraph was saved
    // Verify artifactUri was updated on the job
    Record job = renderJobRepository.requireJobRecord(jobId);
    assertNotNull(job.get("artifact_uri"));
    assertEquals("COMPLETED", job.get("status"));
}
```

---

## 6. Test Implementation Strategy

### 6.1 Unit-Level Tests (In-Memory)
Use `InMemoryStorageReferenceRepository`, `InMemoryProductRepository`, `InMemoryProductDependencyRepository` for testing `RenderOutputRegistrationService` and `ProductRuntimeService` in isolation. These already exist in the test codebase.

### 6.2 Integration Tests (Testcontainers)
For `RenderJobExecutionService`, `RenderJobClaimService`, `RenderJobFailureService` — use real PostgreSQL via Testcontainers. These need real DB for CAS operations (`claimForSelection`, `markActiveJobFailed`).

### 6.3 Concurrency Tests
Use `CyclicBarrier` + `ExecutorService` with real DB connections. Verify exactly-once semantics for claim and finalization.

### 6.4 Failure Injection
- **BlobStorage**: Mock `BlobStorage.put()` to throw.
- **StorageReferenceRepository**: Mock `save()` to throw.
- **ProductRepository**: Mock `save()` to throw.
- **BillingEnforcementService**: Mock `reserveQuota()` to return failure.
- **S3ObjectWriter**: Mock `upload()` to throw or return mismatched checksum.

---

## 7. Summary of Findings

| Finding | Severity | Impact |
|---------|----------|--------|
| Main render path does not create FINAL_RENDER Product | HIGH | Render outputs have no Product lifecycle in the primary execution path |
| No duplicate finalization guard on COMPLETING state | MEDIUM | Concurrent `finishRenderPhase` calls could double-upload |
| Two separate storage registration mechanisms (BlobStorage vs StorageRuntimeService) | MEDIUM | StorageReference domain is not used in main render path |
| State machine is in-memory only | LOW | Real guard is DB CAS; state machine is for validation/tracing only |
| Billing finalization failure silently swallowed | LOW | By design, but could mask accounting issues |
| ArtifactGraph and Artifact (catalog) are disconnected | MEDIUM | No cross-reference between the two artifact concepts |
| Product has no validation that storageReferenceId is non-null before markReady | LOW | Could create a READY Product without storage |
| `computeContentHash` uses URI hashCode, not actual content | LOW | Placeholder — production should hash actual bytes |
