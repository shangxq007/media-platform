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
- ~~Full Render Job Status contract (not implemented)~~ → Implemented in R7
- Frontend Workbench integration (not implemented)
- OpenCue production dispatch (disabled)
- Remotion production dispatch (disabled)
- MinIO/S3 storage (deferred)

---

## Backend R7 — Render Job Status + Product Result Contract

R7 provides a stable API contract for querying render job status and render product result lookup. The frontend and future async OpenCue integration can consume render outcomes without depending on provider/backend/environment/storage internals.

### What R7 Adds Over R6.1

- `GET /{revisionId}/render-jobs/{renderJobId}` — status query endpoint
- `GET /{revisionId}/render-jobs/{renderJobId}/result` — result query endpoint
- `RenderJobStatusService` — reconstructs status/result from Product metadata
- `RenderJobStatusResponse` — safe status DTO (no provider/backend/environment/paths)
- `RenderJobResultResponse` — safe result DTO (no provider/backend/environment/paths)
- Status enum: `READY`, `FAILED`, `RUNNING` (mapped from ProductStatus)
- Future-safe: works in synchronous mode, supports future async OpenCue without API change

### R7 Design Decision: Reconstruct from Product Metadata

R6/R6.1 generates `renderJobId` (e.g., `rj_abc123`) during render but does NOT persist it in a dedicated render_job table. The renderJobId exists only inside `Product.metadataJson` as a provenance field.

R7 reconstructs render job status by:
1. Scanning Products in the project via `ProductRuntimeService.findByProject()`
2. Parsing each Product's `metadataJson` to match `renderJobId` and `timelineRevisionId`
3. Mapping `ProductStatus` to API-facing status (`READY`/`FAILED`/`RUNNING`)

This avoids a new DB migration while providing a stable API contract. Future async mode can add a dedicated render_job record without changing the API.

### R7 Status Query

```bash
curl -s http://localhost:8080/api/v1/render/projects/prj_123/timeline/revisions/rev_456/render-jobs/rj_789 | jq .
```

Response (READY):
```json
{
  "renderJobId": "rj_789",
  "projectId": "prj_123",
  "timelineRevisionId": "rev_456",
  "snapshotId": "snap_...",
  "status": "READY",
  "renderMode": "timeline-revision-render",
  "outputProfile": "default_1080p",
  "outputFormat": "mp4",
  "outputProductId": "prod_...",
  "productStatus": "READY",
  "inputProductIds": ["prod_input_1"],
  "inputDependencyCount": 1,
  "createdAt": "2026-06-27T...",
  "completedAt": "2026-06-27T...",
  "message": "Render completed successfully",
  "resultAvailable": true
}
```

### R7 Result Query

```bash
curl -s http://localhost:8080/api/v1/render/projects/prj_123/timeline/revisions/rev_456/render-jobs/rj_789/result | jq .
```

Response (READY):
```json
{
  "renderJobId": "rj_789",
  "projectId": "prj_123",
  "timelineRevisionId": "rev_456",
  "snapshotId": "snap_...",
  "outputProductId": "prod_...",
  "productStatus": "READY",
  "mimeType": "video/mp4",
  "outputFormat": "mp4",
  "width": 1920,
  "height": 1080,
  "fps": 30,
  "durationSeconds": 10.5,
  "hasSubtitles": true,
  "baselineRenderer": "ffmpeg-libass",
  "renderMode": "timeline-revision-render",
  "inputProductIds": ["prod_input_1"],
  "inputDependencyCount": 1,
  "createdAt": "2026-06-27T...",
  "completedAt": "2026-06-27T...",
  "message": "Render result available"
}
```

### What R7 Does NOT Prove

- Full async job system (still synchronous controlled-local)
- Dedicated render_job record (reconstructs from Product metadata)
- OpenCue production dispatch (disabled)
- Remotion production dispatch (disabled)
- MinIO/S3 storage (deferred)

### Architecture Compliance

- No Kernel/SPI changes
- No Product/Timeline model semantic changes
- No Execution lifecycle changes
- No new DB migration
- No provider/backend/environment/storageProvider exposed
- No signed URLs or local paths exposed
- No storageReferenceId exposed
- Product remains canonical communication object
- Timeline remains canonical editing model
- ProductDependency lineage remains formal dependency model

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

---

## Backend R8 — Local Real Render Harness

R8 provides a repeatable local real-render smoke harness that proves the full R6.1 + R7 backend chain works with a real media file and a real FFmpeg/libass execution.

### What R8 Proves

- TimelineRevision → input RAW_MEDIA Product resolution works end-to-end
- StorageRuntime materialization produces a real file path
- FFmpeg/libass real render executes with `-i <materializedInputPath>` (no testsrc/lavfi)
- Output is a real playable mp4 (verified via ffprobe)
- Output Product is FINAL_RENDER + MEDIA_FILE + READY
- ProductDependency lineage is created (output DERIVED_FROM input)
- R7 status query returns correct status/resultAvailable/outputProductId/inputProductIds
- R7 result query returns correct mimeType/dimensions/fps/duration
- No sensitive data (provider/backend/environment/signedUrl/path) in responses

### Running the Smoke

```bash
# Run the real render smoke test
./gradlew :render-module:test --tests "*TimelineRevisionRealRenderSmokeTest"

# Run all R6/R6.1/R7/R8 regression
./gradlew :render-module:test \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRenderServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineInputProductResolverTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRenderJobMapperTest" \
  --tests "com.example.platform.render.app.output.RenderOutputRegistrationServiceTest" \
  --tests "com.example.platform.render.app.timeline.RenderJobStatusServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRealRenderSmokeTest"

# Run controller contract tests
./gradlew :platform-app:test --tests "com.example.platform.web.render.TimelineRevisionRenderJobStatusControllerTest"
```

### FFmpeg Requirement

The real render smoke requires FFmpeg and ffprobe on PATH. If unavailable, the test is **skipped** (not failed) with the message: "FFmpeg not available; real baseline render smoke skipped."

Install FFmpeg:
- Ubuntu/Debian: `sudo apt install ffmpeg`
- macOS: `brew install ffmpeg`
- Verify: `ffmpeg -version && ffprobe -version`

### What R8 Does NOT Cover

- Docker/Compose dev runtime
- ~~MinIO/S3 storage~~ → R10A adds S3-compatible read/materialize path
- OpenCue async execution
- Remotion production dispatch
- Multi-input track rendering
- Subtitle burn-in (uses simple video-only timeline)
- Frontend integration
- Production deployment

## Backend R10A — S3-Compatible StorageRuntime Provider: Read/Materialize Path

R10A adds a generic S3-compatible materialization provider to the StorageRuntime,
enabling render input materialization from S3-compatible object storage.

### What R10A Adds Over R8

- `S3ObjectMaterializer` — downloads S3 objects to local temp files for render input
- `StorageRuntimeService` now supports both LOCAL and S3-compatible materialization
- Provider-agnostic: works with any S3-compatible backend (RustFS, SeaweedFS, AWS S3, R2)
- Checksum verification (SHA-256) for S3 materialized objects
- Integration test against RustFS dev backend

### S3 Materialization Flow

```
StorageReference (providerType=S3, rootPath=bucket, relativePath=objectKey)
  → StorageRuntimeService.materialize()
  → S3ObjectMaterializer.materialize(bucket, objectKey, expectedChecksum)
  → S3 HeadObject (verify existence)
  → S3 GetObject (download bytes)
  → Write to local temp file
  → Compute SHA-256, verify against stored checksum
  → Return local temp file path
  → RenderInputMaterializationService validates path
  → FFmpeg/libass renders from local path
```

### Configuration

S3 materialization uses existing `storage.s3.*` configuration:

```yaml
storage:
  s3:
    enabled: true
    endpoint: http://localhost:9000
    region: us-east-1
    access-key: dev-access-key
    secret-key: dev-secret-key
    path-style-access: true
    default-bucket: media-platform-dev
```

### Running R10A Tests

```bash
# Unit tests (no S3 backend required)
./gradlew :storage-module:test --tests "com.example.platform.storage.infrastructure.S3ObjectMaterializerTest"

# Integration tests (requires RustFS running)
docker compose -f docker-compose.dev.yml --profile s3 up -d
./gradlew :storage-module:test --tests "com.example.platform.storage.infrastructure.S3ObjectMaterializerIntegrationTest"

# Regression tests
./gradlew :render-module:test \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRenderServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineInputProductResolverTest" \
  --tests "com.example.platform.render.app.timeline.RenderJobStatusServiceTest" \
  --tests "*TimelineRevisionRealRenderSmokeTest"
```

### Storage Backend Compatibility

| Backend | Status | License | Notes |
|---------|--------|---------|-------|
| RustFS | Default dev backend | Apache-2.0 | S3-compatible, tested |
| SeaweedFS | Future compatibility target | Apache-2.0 | Planned |
| AWS S3 | Supported | — | Generic S3 endpoint |
| Cloudflare R2 | Supported | — | Via `compatibility: r2` |
| MinIO | Not default | AGPLv3 | Licensing concerns |

### What R10A Does NOT Cover

- Output write-back to S3 (deferred to R10B)
- Presigned URL generation for S3 objects
- S3 multipart upload
- S3 lifecycle policies
- OpenCue async execution
- Remotion production dispatch
- Frontend integration
- Production deployment

---

## Backend R10A.1 — S3-Backed Real Render Smoke

R10A.1 adds a real end-to-end smoke test proving that a TimelineRevision render
can use input media stored in S3-compatible object storage, materialize it through
StorageRuntime, render with real FFmpeg/libass, and produce a READY FINAL_RENDER
Product with ProductDependency lineage and R7 status/result query support.

### What R10A.1 Verifies Over R10A

- Full render chain with S3-backed input (not just S3 materialization in isolation)
- Input media uploaded to S3, StorageReference created with S3_COMPATIBLE provider type
- StorageRuntime materializes from S3 to local temp file
- FFmpeg/libass renders using materialized input (no testsrc/lavfi)
- Output registered as LOCAL storage (S3 output write-back is R10B)
- ProductDependency lineage (output DERIVED_FROM input)
- R7 status query: READY, resultAvailable, outputProductId, inputProductIds
- R7 result query: mimeType, dimensions, fps, duration, baselineRenderer
- Public response safety: no bucket/key/path/signed URL exposure

### Provider Type Hardening

R10A.1 adds `S3_COMPATIBLE` and `OBJECT_STORAGE` as accepted provider types
in `StorageRuntimeService.isS3CompatibleProvider()` and `StorageProviderType` enum:

| Value | Status | Notes |
|-------|--------|-------|
| `S3` | Preferred | Generic S3-compatible |
| `S3_COMPATIBLE` | Accepted | Explicit S3-compatible alias |
| `OBJECT_STORAGE` | Accepted | Storage-neutral alias |
| `MINIO`, `OSS`, `GCS`, `AZURE` | Legacy | Backward compatibility |
| `RUSTFS`, `SEAWEEDFS` | Rejected | Backend-specific, not storage-neutral |

### Running R10A.1

```bash
# Prerequisites: FFmpeg + S3 endpoint
docker compose -f docker-compose.dev.yml --profile s3 up -d

# Run S3-backed real render smoke
./gradlew :render-module:test --tests "*TimelineRevisionS3RealRenderSmokeTest"

# Full regression
./gradlew :render-module:test \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionS3RealRenderSmokeTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRealRenderSmokeTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRenderServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineInputProductResolverTest" \
  --tests "com.example.platform.render.app.timeline.RenderJobStatusServiceTest"
```

### S3-Backed Render Flow

```
Generate test mp4 (3s, 320x180)
  → Upload to S3 (RustFS at localhost:9000)
  → Create StorageReference (providerType=S3_COMPATIBLE, rootPath=bucket, relativePath=key)
  → Register RAW_MEDIA Product (READY, ownerAssetId=sourceAssetId)
  → Create TimelineRevision
  → TimelineRevisionRenderService.render()
    → Resolve input Product from sourceAssetId
    → RenderInputMaterializationService.materialize()
      → StorageRuntimeService.materialize()
        → S3ObjectMaterializer.materialize(bucket, key, checksum)
        → HeadObject + GetObject → local temp file
    → FFmpeg/libass renders from local temp file
    → RenderOutputRegistrationService.registerOutput()
      → StorageReference (LOCAL) + Product (FINAL_RENDER, READY)
      → ProductDependency (DERIVED_FROM)
    → R7 status/result queries verified
```

### What R10A.1 Does NOT Cover

- S3 output write-back (R10B)
- Multiple input tracks
- Subtitle burn-in with S3-backed subtitles
- Async render execution
- Production deployment
