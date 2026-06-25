---
status: implementation-report
created: 2026-06-24
scope: platform-app + render-module
truth_level: current
owner: platform
---

# Timeline Git Productization Sprint 004 — Merge API + Asset API

## Implemented APIs

### Timeline Merge API

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `POST /api/v1/render/projects/{projectId}/timeline/revisions/merge` | POST | Three-way timeline merge |

### Asset Version & Governance API

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/projects/{projectId}/assets/{assetId}/versions` | GET | Asset version info |
| `GET /api/v1/projects/{projectId}/assets/{assetId}/governance` | GET | Asset governance metadata |
| `GET /api/v1/projects/{projectId}/assets/{assetId}/jsonld` | GET | JSON-LD export |

### Existing APIs (Unchanged)

| Endpoint | Status |
|----------|--------|
| `GET /revisions` (history) | ✅ Existed |
| `GET /compare` (diff) | ✅ Existed |
| `POST /revisions/{id}/restore` | ✅ Existed |

## Request/Response DTOs

### MergeApiRequest

```java
record MergeApiRequest(
    String tenantId, String baseRevisionId, String sourceRevisionId,
    String targetRevisionId, String authorUserId, String message,
    List<ResolutionDto> resolutions) {}

record ResolutionDto(
    String conflictId, String entityRef, String entityId,
    String conflictType, String resolutionMode) {}
```

### MergeApiResponse

```java
record MergeApiResponse(
    String status,           // MERGED | CONFLICTS | NO_OP | FAILED
    String baseRevisionId, String sourceRevisionId, String targetRevisionId,
    String mergedRevisionId, // null when CONFLICTS
    List<MergeConflictDto> conflicts,
    MergeSummaryDto mergeSummary,
    String message) {}
```

### AssetVersionResponse

```java
record AssetVersionResponse(
    String assetId, String assetVersion, String assetType,
    String ownerId, String projectId, String entityRef,
    String storageUri, String checksum,
    String createdAt, String updatedAt, boolean currentOnly) {}
```

### AssetGovernanceResponse

```java
record AssetGovernanceResponse(
    String assetId, String assetVersion,
    String classification, String license,
    String retentionPolicy, String securityLevel,
    boolean containsPii, boolean aiGenerated) {}
```

## Enhanced RevisionListItem

Added merge metadata fields:
- `boolean isMerge`
- `String mergeParentRevisionIds`
- `String mergeBaseRevisionId`

## Service Reuse

| API | Reused Service | Method |
|-----|---------------|--------|
| Merge (no resolutions) | `TimelineMergeService` | `threeWayMerge(request)` |
| Merge (with resolutions) | `TimelineMergeService` | `threeWayMergeWithResolutions(request, intents)` |
| Asset version | `AssetRegistryService` | `resolve(assetId)` |
| Asset governance | `AssetRegistryService` | `resolve(assetId)` |
| JSON-LD export | `AssetRegistryService` | `buildJsonLdProjection(assetId)` |

## Modified Files

| File | Change |
|------|--------|
| `TimelineRevisionController.java` | +`TimelineMergeService` dependency; +`merge()` endpoint; `RevisionListItem` extended with merge fields; +merge DTOs |
| `AssetController.java` | +`AssetRegistryService` dependency; +`getVersions()`, `getGovernance()`, `exportJsonLd()` endpoints; +version/governance DTOs |
| `AssetControllerTest.java` | +`AssetRegistryService` mock |
| `TimelineEditorSyncServiceTest.java` | Updated `RevisionInfo` constructor (from Sprint 002) |

## New Files

| File | Purpose |
|------|---------|
| `TimelineMergeControllerTest.java` | Merge API tests: merges without conflicts, returns conflicts |
| `AssetVersionGovernanceApiTest.java` | Asset API tests: versions, governance, 404, JSON-LD |

## Tests Run (All Passing)

| Test Class | Tests | Scenarios |
|-----------|-------|-----------|
| `TimelineMergeControllerTest` | 2 | Merge MERGED, Merge CONFLICTS |
| `AssetVersionGovernanceApiTest` | 4 | Version API, Governance API, 404, JSON-LD export |
| `AssetControllerTest` | 5 | Existing tests (adapted for new constructor) |

```bash
./gradlew :platform-app:test --tests '*TimelineMergeControllerTest' --tests '*AssetVersionGovernanceApiTest' --tests '*AssetControllerTest'
# Result: BUILD SUCCESSFUL
```

## Known Limitations

1. **Merge resolution intents use simplified entity resolution** — the current DTO takes `entityId` string instead of full `EntityRef(kind, id)`. Kind defaults to CLIP. Future: support all entity kinds.
2. **Asset version list always returns `currentOnly=true`** — the database has one row per asset. Multi-version history requires `asset_version` table population.
3. **No pagination on version endpoint** — single record response. Paginate when multi-version is implemented.
4. **Merge API reuses revision path** (`/revisions/merge`) — could be extracted to a separate `MergeController` if the merge surface grows.

## Deferred Items

| Item | Sprint |
|------|--------|
| Review API (POST /reviews, GET /reviews/{id}) | Sprint 005 |
| Comment API (POST /comments, threaded) | Sprint 005 |
| Approval API (POST /approve, POST /request-changes) | Sprint 005 |
| Branch model | P4 |
| Rebase | P5 |
| Asset ingestion pipeline | P1 blueprint |
| Asset search | P2 |
| Marketplace | P3 |

## Validation

- [x] No new module
- [x] No V2 migration
- [x] No Review/Comment/Approval API
- [x] No Branch/Rebase
- [x] No OpenCue/OpenLineage/OpenAssetIO/KG
- [x] No Spring AI runtime
- [x] No H2
- [x] ProductionSafetyValidator unchanged
- [x] All API tests passing
