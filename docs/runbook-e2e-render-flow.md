# Runbook: End-to-End Render Flow

## Purpose

This runbook documents the first end-to-end business main chain (核心业务主链路) for the media platform. It covers the full flow from tenant creation through render job execution.

## Business Chain

```
Create tenant/user/API key
  -> create project
  -> submit AI video render job
  -> check permission and quota
  -> create RenderJob
  -> write Outbox event
  -> execute simplified workflow/orchestrator
  -> call Mock AI Provider
  -> call Mock Render Provider
  -> save artifact metadata/placeholder with LocalFs Storage
  -> update RenderJob status
  -> write AuditRecord
  -> create NotificationEvent
  -> deliver through Mock Notification Provider
  -> query job, artifacts, events, audit records through APIs
```

## Prerequisites

- Java 25 (via asdf-vm or SDKMAN!)
- Gradle 9.1 (via wrapper)
- curl (for smoke script)
- Application running: `./gradlew :platform-app:bootRun`

## Quick Start

### 1. Start the Application

```bash
cd media-platform
./gradlew :platform-app:bootRun
```

Wait for the log line: `Started PlatformApplication in ... seconds`

### 2. Run the E2E Smoke Script

```bash
./scripts/smoke/e2e-render-flow.sh
```

Expected output: `E2E RENDER FLOW PASSED` with 13 checks all green.

### 3. Run Integration Tests

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` — all tests pass including `RenderFlowIntegrationTest`.

## API Walkthrough

### Step 1: Health Check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Expected: `{"status":"UP"}`

### Step 2: Create Tenant

```bash
curl -s -X POST http://localhost:8080/api/v1/identity/tenants \
  -H "Content-Type: application/json" \
  -d '{"name": "My Tenant"}' | jq .
```

Response:
```json
{
  "id": "ten_...",
  "name": "My Tenant",
  "status": "ACTIVE",
  "createdAt": "2026-05-08T..."
}
```

Save the `id` as `$TENANT_ID`.

### Step 3: Create Project

```bash
curl -s -X POST http://localhost:8080/api/v1/identity/tenants/$TENANT_ID/projects \
  -H "Content-Type: application/json" \
  -d '{"name": "My Project", "description": "Test project"}' | jq .
```

Save the `id` as `$PROJECT_ID`.

### Step 4: Create API Key

```bash
curl -s -X POST http://localhost:8080/api/v1/identity/tenants/$TENANT_ID/apikeys \
  -H "Content-Type: application/json" \
  -d '{"principal": "my-service"}' | jq .
```

Response includes `apiKey` (plaintext, only returned once) and `fingerprint`.

### Step 5: Create Render Job

```bash
curl -s -X POST http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs \
  -H "Content-Type: application/json" \
  -d '{"projectId": "'$PROJECT_ID'", "timelineSnapshotId": "snap_001", "profile": "default_1080p"}' | jq .
```

Response:
```json
{
  "id": "rj_...",
  "projectId": "prj_...",
  "timelineSnapshotId": "snap_001",
  "profile": "default_1080p",
  "status": "QUEUED"
}
```

Save the `id` as `$JOB_ID`.

### Step 6: Query Render Job

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs/$JOB_ID | jq .
```

### Step 7: Execute Local Workflow

```bash
curl -s -X POST http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs/$JOB_ID/execute-local | jq .
```

This triggers the full orchestration chain:
1. Quota check
2. AI script generation (via Mock AI Provider)
3. Render (via Mock Render Provider)
4. Storage (via LocalFs Storage Provider)
5. Notification (via Mock Notification Provider)
6. Audit record

### Step 8: Query Execution Status

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs/$JOB_ID/execution | jq .
```

### Step 9: Query Quota

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/quota | jq .
```

### Step 10: Query Entitlements

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/entitlements | jq .
```

### Step 11: Query Notifications

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/notifications | jq .
```

### Step 12: Query Audit Records

```bash
curl -s http://localhost:8080/api/v1/audit/compliance/overview | jq .
```

### Step 13: Query Outbox

```bash
curl -s http://localhost:8080/api/v1/outbox/overview | jq .
```

## Architecture Decisions

### Port Interfaces (ADR-002)

Cross-module service access uses port interfaces, not direct service references:

- `RenderOrchestratorPort` in `render.api.port` — used by `workflow-module`
- `NotificationEventPublisher` in `shared.notification` — cross-module SPI
- `AiGatewayPort` in `ai.api` — used by `render-module`

### Mock Providers

All external providers are mocked:

- `StubChatProvider` — deterministic mock AI script generation
- `MockRenderProvider` — simulates rendering with 200-1000ms delay
- `LocalFsStorageProvider` — filesystem-based storage in `./.data/storage`
- `MockNotificationProvider` — in-memory notification delivery

### Module Boundaries

- `render-module` depends on `ai-module`, `audit-compliance-module`, `storage-module` via `allowedDependencies`
- `workflow-module` depends on `render-module` via port interface (`RenderOrchestratorPort`)
- All modules use constructor injection (no `@Autowired`)

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused` | App not running | Start with `./gradlew :platform-app:bootRun` |
| `404 Not Found` | Wrong API path | Check path matches `/api/v1/tenants/{tenantId}/...` |
| `401 Unauthorized` | API key auth enabled | Set `app.identity.api-key-auth-enabled=false` |
| `BUILD FAILED` | Module boundary violation | Check `allowedDependencies` in `package-info.java` |
| Test context load failure | Flyway timing | Ensure `@ActiveProfiles("test")` on test class |

## Related Documents

- `docs/roo-execution-log.md` — full execution history
- `docs/roo-gap-report.md` — gap analysis
- `docs/module-boundaries.md` — module dependency graph
- `docs/runbook-local.md` — local development runbook

## Timeline Core Smoke Path

The Timeline Core Testable R1 adds a backend smoke path proving Timeline → Product closure.

### Flow

```
TimelineSpec (fixture)
    → TimelineRenderJobMapper.toRenderJobRequest()
    → controlled local output file (NOT real FFmpeg/libass)
    → RenderOutputRegistrationService.registerOutput()
    → StorageReference (LOCAL provider)
    → Product (ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE, status=READY)
    → queryable via ProductController
```

### What This Proves

- Timeline metadata survives mapping to RenderJob request
- Mapper validates all inputs fail-closed (duration, fps, canvas, format, path safety)
- Render output can be registered through StorageRuntime → ProductRuntime
- Final render becomes a READY Product with checksum and provenance

### What This Does NOT Prove

- Real FFmpeg/libass rendering (requires baseline FFmpeg integration test)
- Real Remotion rendering (gated for advanced template rendering)
- Real OpenCue execution (disabled by default)
- MinIO/S3 storage (deferred to Storage R2)
- Full Timeline Git productization (branch/merge/conflict UI not included)
- Full Workflow Runtime (no Temporal orchestration)

## Timeline Real Baseline Render Smoke (R2)

R2 adds a real FFmpeg/libass baseline render smoke proving the full chain with actual media rendering.

### Flow

```
TimelineSpec (fixture with asset:// URI)
    → TimelineRenderJobMapper.toRenderJobRequest()
    → real FFmpeg testsrc generates test media (2s, 320x180)
    → real FFmpeg/libass subtitle burn-in (SRT/ASS)
    → output media file (verified non-empty)
    → RenderOutputRegistrationService.registerOutput()
    → StorageReference (LOCAL provider, checksum verified)
    → Product (ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE, status=READY)
    → queryable via ProductRuntimeService
```

### What This Proves

- Real FFmpeg/libass subtitle burn-in is invoked
- Timeline metadata survives mapping to RenderJob request
- Rendered output is registered through StorageRuntime → ProductRuntime
- Final render becomes a READY Product with checksum and provenance

### Gating

- Test uses `Assumptions.assumeTrue(ffmpegAvailable)` — skips explicitly if FFmpeg missing
- Skip message: "FFmpeg not available; real baseline render smoke skipped."
- Does NOT silently fall back to controlled temp output
- R1 controlled output smoke remains as the always-fast test

### What This Does NOT Prove

- Full production rendering (uses tiny test media)
- Remotion rendering (gated for advanced template rendering)
- OpenCue execution (disabled by default)
- MinIO/S3 storage (deferred)

## Backend R3 — Render Product Provenance Hardening

R3 hardens final render Product provenance from Timeline/TimelineRevision through
RenderJob execution to final READY Product metadata.

### What R3 Adds Over R2

- `RenderProductProvenance` internal value object for structured provenance metadata
- `TimelineRenderJobMapper.MappingResult` extended with revisionId, snapshotId, subtitleFormat, sourceAssetIds
- `RenderOutputRegistrationService.registerOutput()` overloaded to accept provenance
- Final render Products now carry timeline/render/storage provenance through metadataJson
- `sourceTimelineRevisionId` populated on Product record when revision provenance available

### Final Render Product Provenance Contract

Final render Products now include, when available:

- tenantId, projectId
- timelineId, timelineRevisionId, snapshotId
- renderJobId, executionJobId
- outputProfile, outputFormat
- durationSeconds, fps, width, height
- hasSubtitles, subtitleFormat
- baselineRenderer (e.g., "ffmpeg-libass")
- renderMode (e.g., "baseline-subtitle-burn-in", "final-render")
- inputProductIds, sourceAssetIds
- fileSize, mimeType, checksum (storage facts)

### What R3 Does NOT Prove

- Full Product Graph (dependency edges remain deferred)
- Full Workflow Runtime (no Temporal orchestration)
- Full Timeline Git productization (branch/merge/conflict UI not included)
- Production dispatch (no real OpenCue or Remotion production submit)
- MinIO/S3 storage (deferred)

### Architecture Compliance

- No Product model semantic changes (provenance uses existing metadataJson field)
- No StorageRuntime semantic changes
- No ProductRuntime semantic changes
- No new database columns required
- No signed URLs persisted
- No absolute filesystem paths exposed
- No internal provider/backend/environment selection exposed

## Backend R4 — Render Input Materialization Migration

R4 migrates the render input path from direct local files to the platform-standard
input materialization path through StorageRuntime.

### What R4 Adds Over R3

- `RenderInputMaterialization` internal value object for materialized input results
- `RenderInputMaterializationService` materializes input Products through StorageRuntime
- Input media is now registered as `RAW_MEDIA` Product backed by StorageReference
- Render input is materialized via `StorageRuntimeService.materialize()` before FFmpeg/libass
- Output Product metadata includes `inputProductIds` referencing input RAW_MEDIA Products
- Failure path tests for: missing Product, not READY, no storageReferenceId, not MEDIA_FILE,
  missing StorageReference, zero-byte file, unsupported MIME type

### Input Materialization Flow

```
test media file
    → StorageRuntimeService.register() → StorageReference
    → ProductRuntimeService.register() → RAW_MEDIA Product (REGISTERED)
    → ProductRuntimeService.markReady() → RAW_MEDIA Product (READY)
    → RenderInputMaterializationService.materialize()
        → verify Product exists
        → verify Product status is READY
        → verify representationKind is MEDIA_FILE
        → verify storageReferenceId present
        → verify StorageReference exists
        → StorageRuntimeService.materialize(storageReferenceId)
        → validate materialized path (exists, regular file, non-zero, safe)
        → return RenderInputMaterialization with local path
    → FFmpeg/libass uses materialized local path for render
    → RenderOutputRegistrationService registers output
    → Output Product READY with inputProductIds in provenance
```

### What R4 Does NOT Prove

- Full Product Graph (dependency edges remain deferred)
- Full Workflow Runtime (no Temporal orchestration)
- Full Timeline Git productization (branch/merge/conflict UI not included)
- Production dispatch (no real OpenCue or Remotion production submit)
- MinIO/S3 storage (deferred)

### Architecture Compliance

- Input media enters render as Product/StorageReference
- StorageRuntime owns physical input materialization
- FFmpeg/libass receives local path only after StorageRuntime materialization
- ProductRuntime owns input/output Product lifecycle
- Render code does not resolve StorageReference paths directly
- No signed URLs persisted
- No absolute local paths exposed in public output Product metadata
- No Product model semantic changes
- No StorageRuntime semantic changes
- No ProductRuntime semantic changes
- No new database columns required
- FFmpeg/libass remains baseline subtitle burn-in
- Remotion/OpenCue not required
- MinIO/S3 not required

## Backend R5 — Product Graph Dependency Edges

R5 promotes render input/output lineage from metadata-only provenance into formal
Product dependency edges using existing ProductRuntime/ProductDependency infrastructure.

### What R5 Adds Over R4

- `RenderOutputRegistrationService.registerOutput()` now links formal Product dependency edges
  after output Product registration and `markReady()`
- Input RAW_MEDIA Products linked to output FINAL_RENDER Product via `ProductRuntimeService.linkDependency()`
- Uses `DependencyType.DERIVED_FROM` to express output is derived from input(s)
- `RenderProductProvenance.inputProductIds` used for both metadata and formal lineage edges
- De-duplication: duplicate input IDs create only one dependency edge
- Self-dependency rejected: output Product cannot depend on itself
- Missing input Product fails closed before marking READY
- Cycle detection remains active via existing `ProductRuntimeService` infrastructure
- Dependency edges queryable via `findDependencies()`, `findDependents()`, `findUpstream()`, `findDownstream()`

### Dependency Edge Flow

```
Input RAW_MEDIA Product(s)
    → RenderOutputRegistrationService.registerOutput()
    → Output FINAL_RENDER Product registered
    → ProductRuntimeService.markReady()
    → ProductRuntimeService.linkDependency(outputId, inputId, DERIVED_FROM)
    → Formal Product dependency edge created
    → Queryable via ProductRuntimeService.findDependencies/findDependents
```

### What R5 Does NOT Prove

- Full incremental rendering (dependency edges are read-only lineage)
- Full cache invalidation (not implemented)
- Full Workflow Runtime (no Temporal orchestration)
- Full Timeline Git productization (branch/merge/conflict UI not included)
- Production dispatch (no real OpenCue or Remotion production submit)
- MinIO/S3 storage (deferred)

### Architecture Compliance

- No Product model semantic changes — uses existing `ProductDependency` infrastructure
- No StorageRuntime semantic changes
- No ProductRuntime semantic changes — uses existing `linkDependency()` API
- No new database columns required
- No new graph runtime introduced
- No Artifact Runtime introduced
- No signed URLs persisted
- No absolute filesystem paths exposed
- FFmpeg/libass remains baseline subtitle burn-in
- Remotion/OpenCue not required
- MinIO/S3 not required

## Backend R6 — TimelineRevision Render API

R6 productizes the render chain into a stable backend API that allows a caller
to render an existing TimelineRevision into a final render Product.

### API Contract

```
POST /api/v1/render/projects/{projectId}/timeline/revisions/{revisionId}/render
Content-Type: application/json

{
  "outputProfile": "default_1080p"
}

Response 201:
{
  "renderJobId": "rj_...",
  "timelineRevisionId": "rev_...",
  "snapshotId": "snap_...",
  "outputProductId": "prod_...",
  "productStatus": "READY",
  "storageReferenceId": "stor_...",
  "mimeType": "video/mp4",
  "outputFormat": "mp4",
  "width": 1920,
  "height": 1080,
  "fps": 30,
  "durationSeconds": 10.0,
  "hasSubtitles": false,
  "baselineRenderer": "ffmpeg-libass",
  "renderMode": "timeline-revision-render",
  "message": "Timeline revision rendered successfully"
}
```

### What R6 Adds Over R5

- `TimelineRevisionRenderService` application service orchestrating the full chain
- `TimelineRevisionRenderRequest` / `TimelineRevisionRenderResponse` DTOs
- `POST /{revisionId}/render` endpoint on `TimelineRevisionController`
- Caller submits TimelineRevision + outputProfile, not provider/backend/environment
- FFmpeg/libass baseline render invoked through `ProcessToolRunner`
- Output registered through `RenderOutputRegistrationService` with full provenance
- `sourceTimelineRevisionId` populated on output Product

### Render Flow

```
POST /{revisionId}/render
    → TimelineRevisionRenderService.render()
    → Load TimelineRevision + snapshot
    → Parse to TimelineSpec
    → TimelineRenderJobMapper.toRenderJobRequest()
    → Invoke FFmpeg/libass baseline render
    → RenderOutputRegistrationService.registerOutput() with provenance
    → Product (FINAL_RENDER, MEDIA_FILE, READY)
    → Response with outputProductId and provenance
```

### What R6 Does NOT Prove

- Full async job system (synchronous controlled-local execution for R6)
- Full Render Job Status contract (not implemented)
- Full Workflow Runtime (no Temporal orchestration)
- Frontend Workbench integration (not implemented)
- OpenCue production dispatch (disabled by default)
- Remotion production dispatch (disabled)
- MinIO/S3 storage (deferred)

### Architecture Compliance

- No Kernel/SPI changes
- No Product/Timeline model semantic changes
- No Execution lifecycle changes
- No Artifact Runtime
- FFmpeg/libass remains baseline subtitle burn-in
- Remotion production dispatch remains disabled
- OpenCue production submit remains disabled
- MinIO/S3 not required
- No signed URLs persisted
- No internal provider/backend/environment selection exposed

## Backend R6.1 — TimelineRevision Input Product Resolution

R6.1 resolves input Product references from TimelineRevision, materializes them
through StorageRuntime, and uses the materialized file as the FFmpeg input.

### What R6.1 Adds Over R6

- `TimelineInputProductResolver` (@Service) resolves sourceAssetIds to inputProductIds via ProductRuntimeService.findByAsset()
- sourceAssetIds validated for safety before Product lookup (rejects absolute paths, traversal, URLs, provider hints — exact-match)
- Input Products resolved from timeline asset registry; fail-closed if no READY RAW_MEDIA Product found
- Input Product materialized through `RenderInputMaterializationService` → `StorageRuntimeService.materialize()`
- FFmpeg/libass uses materialized input file (`-i <materializedPath>`) — no testsrc/lavfi fallback
- Output Product metadata includes `inputProductIds`
- Formal ProductDependency edges created via `RenderOutputRegistrationService` (DERIVED_FROM)
- Single-primary-input only; multiple inputs/tracks remain future hardening
- Defensive input path validation: null, exists, regular file, non-zero

### R6.1 Render Flow

```
POST /{revisionId}/render
    → TimelineRevisionRenderService.render()
    → Load TimelineRevision + snapshot
    → Parse to TimelineSpec
    → TimelineRenderJobMapper extracts sourceAssetIds from clips
    → TimelineInputProductResolver.resolve(sourceAssetIds):
        → validate each sourceAssetId (reject unsafe patterns)
        → ProductRuntimeService.findByAsset(sourceAssetId)
        → filter READY + RAW_MEDIA + MEDIA_FILE
        → fail closed if any asset has no matching Product
        → de-duplicate, return inputProductIds
    → Service rejects empty inputProductIds
    → RenderInputMaterializationService.materialize(inputProductIds.get(0))
    → StorageRuntimeService.materialize(storageReferenceId) → local path
    → Defensive validation: null, exists, regular file, non-zero
    → FFmpeg/libass uses "-i <materializedPath>" for render
    → RenderOutputRegistrationService.registerOutput() with provenance
    → ProductDependency edges created (DERIVED_FROM)
    → Response with outputProductId, inputProductIds, inputDependencyCount
```

### API Contract (unchanged)

Request:
```json
{
  "outputProfile": "default_1080p"
}
```

Response 201:
```json
{
  "renderJobId": "rj_...",
  "timelineRevisionId": "rev_...",
  "snapshotId": "snap_...",
  "outputProductId": "prod_...",
  "productStatus": "READY",
  "storageReferenceId": "stor_...",
  "mimeType": "video/mp4",
  "outputFormat": "mp4",
  "width": 1920,
  "height": 1080,
  "fps": 30,
  "durationSeconds": 10.0,
  "hasSubtitles": false,
  "baselineRenderer": "ffmpeg-libass",
  "renderMode": "timeline-revision-render",
  "inputProductIds": ["prod_..."],
  "inputDependencyCount": 1,
  "message": "Timeline revision rendered successfully"
}
```

### What R6.1 Does NOT Prove

- Multiple input tracks (single-primary-input only)
- Full async job system (still synchronous controlled-local)
- Full Render Job Status contract (not implemented)
- Frontend Workbench integration (not implemented)
- OpenCue production dispatch (disabled)
- Remotion production dispatch (disabled)
- MinIO/S3 storage (deferred)

### Architecture Compliance

- No Kernel/SPI changes
- No Product/Timeline model semantic changes
- No Execution lifecycle changes
- No Artifact Runtime
- FFmpeg/libass uses materialized input file (no testsrc/lavfi)
- Remotion production dispatch remains disabled
- OpenCue production submit remains disabled
- MinIO/S3 not required
- No signed URLs persisted
- No materialized input paths exposed in response
- No internal provider/backend/environment selection exposed
- Product remains canonical communication object
- Timeline remains canonical editing model
