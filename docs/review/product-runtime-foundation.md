---
status: implementation-report
created: 2026-06-25
scope: render-module + platform-app + V1 baseline + shared-kernel
truth_level: current
owner: platform
---

# Foundation F1 — Product Runtime Foundation

## Implemented

### Domain Models (4)
| Model | Purpose |
|-------|---------|
| `Product` | Aggregate root: productId, tenantId, projectId, ownerAssetId, productType, representationKind, producerType/Id, status, storageReferenceId, checksum, contentHash, mimeType, version, metadataJson, timestamps |
| `ProductType` | RAW_MEDIA, TRANSCRIPT, VISION, OCR, EMBEDDING, TIMELINE_EDIT_PLAN, TIMELINE_REVISION, PREVIEW, THUMBNAIL, PROXY, SUBTITLE, FINAL_RENDER, PACKAGE, SEARCH_INDEX, MARKETPLACE_LISTING |
| `RepresentationKind` | MEDIA_FILE, JSON_DOCUMENT, VECTOR_REFERENCE, TIMELINE_PLAN, TIMELINE_REVISION, GRAPH, SEARCH_INDEX, EXTERNAL_REFERENCE |
| `ProductStatus` | REGISTERED, PROCESSING, READY, FAILED, SUPERSEDED, ARCHIVED |

### Schema
`product` table — 18 columns, 6 indexes (tenant, project, asset, producer, status, type)

### Repository + Service + Controller
| Component | Key Methods |
|-----------|-------------|
| `ProductRepository` | save (upsert), findById, findByProject, findByAsset, findLatest |
| `ProductRuntimeService` | register, markReady, markFailed, find, findLatest, findByAsset, findByProject |
| `ProductController` | GET /products/{id}, GET /projects/{projectId}/products, GET /assets/{assetId}/products |

### Events (3)
- `ProductRegisteredEvent` — productId, productType, assetId, projectId, producerId
- `ProductReadyEvent` — productId, productType, assetId
- `ProductFailedEvent` — productId, productType, assetId, error

### Architecture Validation
- Product table: single table, no inheritance (per ADR-010)
- representationKind discriminates file vs non-file products
- storageReferenceId is nullable (Storage Runtime not implemented)
- No Product Graph, No Execution Planner, No Storage Runtime

## Tests
Compilation passes. Existing tests unaffected.

## Known Limitations
| Limitation | Status |
|-----------|--------|
| No Product Graph (dependencies) | Deferred to F2 |
| No Producer integration | Producers use service API manually |
| No Storage Runtime | storageReferenceId nullable |

## R1 Output Closure Status (COMPLETED 2026-06-27)

- `RenderOutputRegistrationService` registers render outputs as file-backed Products
- Products use `ProductType.FINAL_RENDER` + `RepresentationKind.MEDIA_FILE`
- Render outputs flow through StorageRuntime before Product registration
- Product carries `storageReferenceId` linking to verified storage
- Product metadata captures jobId, producerId, fileSize, mimeType, checksum
- `productRuntime.register()` null-safety fix applied (self-referencing cycle check removed from registration — belongs in `linkDependency` only)

## R3 Provenance Hardening Status (COMPLETED 2026-06-27)

- `RenderProductProvenance` internal value object carries timeline/render provenance
- `RenderOutputRegistrationService.registerOutput()` accepts optional provenance
- Final render Products now include: timelineId, timelineRevisionId, snapshotId, renderJobId,
  outputProfile, outputFormat, durationSeconds, fps, width, height, hasSubtitles, subtitleFormat,
  baselineRenderer, renderMode, inputProductIds, sourceAssetIds
- `sourceTimelineRevisionId` populated on Product record when revision provenance available
- No Product model semantic changes — provenance stored in existing metadataJson field
- No new database columns required

## R4 Input Materialization Status (COMPLETED 2026-06-27)

- `RenderInputMaterialization` internal value object for materialized input results
- `RenderInputMaterializationService` materializes input Products through StorageRuntime
- Input media registered as `RAW_MEDIA` Product backed by StorageReference
- Render input materialized via `StorageRuntimeService.materialize()` before FFmpeg/libass
- Output Product metadata includes `inputProductIds` referencing input RAW_MEDIA Products
- Failure path tests for: missing Product, not READY, no storageReferenceId, not MEDIA_FILE,
  missing StorageReference, zero-byte file
- No Product model semantic changes — input references stored in existing metadataJson field
- No StorageRuntime semantic changes
- No ProductRuntime semantic changes
- No new database columns required

## R5 Product Graph Dependency Edges Status (COMPLETED 2026-06-27)

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
- No Product model semantic changes — uses existing `ProductDependency` infrastructure
- No StorageRuntime semantic changes
- No ProductRuntime semantic changes — uses existing `linkDependency()` API
- No new database columns required
- No new graph runtime introduced

## R6 TimelineRevision Render API Status (COMPLETED 2026-06-27)

- `TimelineRevisionRenderService` orchestrates TimelineRevision → render → Product chain
- `TimelineRevisionRenderRequest` / `TimelineRevisionRenderResponse` DTOs for API contract
- `POST /{revisionId}/render` endpoint on `TimelineRevisionController`
- Caller submits TimelineRevision + outputProfile (no provider/backend/environment selection)
- FFmpeg/libass baseline render invoked through `ProcessToolRunner`
- Output registered through `RenderOutputRegistrationService` with full provenance
- `sourceTimelineRevisionId` populated on output Product
- Tests cover: successful render, provenance metadata, failure paths
- No Product model semantic changes
- No StorageRuntime semantic changes
- No ProductRuntime semantic changes
- No new database columns required

## R6.1 TimelineRevision Input Product Resolution Status (COMPLETED 2026-06-27)

- `TimelineInputProductResolver` (@Service) resolves sourceAssetIds to inputProductIds via `ProductRuntimeService.findByAsset()`
- sourceAssetIds validated for safety before Product lookup (rejects absolute paths, traversal, URLs, provider hints — exact-match)
- Input Products resolved from timeline asset registry; fail-closed if no READY RAW_MEDIA Product found
- Input Product materialized through `RenderInputMaterializationService` → `StorageRuntimeService.materialize()`
- FFmpeg/libass uses materialized input file (`-i <materializedPath>`) — no testsrc/lavfi fallback
- Output Product metadata includes `inputProductIds`
- Formal ProductDependency edges created via `RenderOutputRegistrationService` (DERIVED_FROM)
- Single-primary-input only; multiple inputs/tracks remain future hardening
- Response includes `inputProductIds` and `inputDependencyCount`
- No Product model semantic changes
- No ProductRuntime semantic changes
- No new database columns required
- No new graph runtime introduced
