---
status: implementation-report
created: 2026-06-24
scope: render-module + V1 baseline
truth_level: current
owner: platform
---

# Platform Foundation Sprint 023 — Persisted Search Projection & Read Model

## Search Audit

### Before (Sprint 022)

- `SearchProjection` built in-memory by `SearchReindexTaskHandler`, logged but never persisted
- `AssetSearchService` scanned Asset + SemanticMetadata rows directly on every query
- No unified search read model

### After (Sprint 023)

- `search_projection` table persists rebuilt projections
- `SearchProjectionRepository` provides upsert/find/search/delete
- `AssetSearchService` prefers projection (faster reads), falls back to direct scan
- Tenant/project context explicitly carried in job payload

## Schema (1 new table)

| Table | Key Columns | Indexes |
|-------|------------|---------|
| `search_projection` | asset_id PK, tenant_id, project_id, filename, asset_type, transcript_text (full), scene_labels (CSV), objects (CSV), brands, people, classification, license, publish_status, search_text, updated_at | ix_sp_tenant, ix_sp_project, ix_sp_publish_status |

## Changed Components

| Component | Change |
|-----------|--------|
| `SearchProjection` record | +tenantId, +projectId, +searchText fields |
| `SearchProjectionRepository` | New — upsert(), findByAssetId(), searchByKeywords(), listByProject(), delete() |
| `SearchReindexTaskHandler` | +persist to search_projection; +reads tenantId/projectId from job payload; +builds searchText for keyword matching |
| `AssetSearchConsumer` | +passes tenantId, projectId in job payload |

## Projection Lifecycle

| Event | Action | Behavior |
|-------|--------|----------|
| `AssetEnrichedEvent` | Rebuild + upsert | New transcript/scene/object data → projection refreshed |
| `AssetPublishedEvent` | Rebuild + upsert | Asset publicly discoverable → projection updated |
| `AssetArchivedEvent` | Rebuild + upsert | Asset archived → publishStatus updated in projection |
| (Future) `AssetDeletedEvent` | Delete projection | Asset removed → projection deleted |

## Read Model Pattern

```
                 Source of Truth                Read Model
                 ──────────────                 ──────────
                 Asset Registry                 search_projection
                 Semantic Metadata              (rebuildable)
                        │                             │
                        ▼                             ▼
                 SearchReindexTaskHandler      AssetSearchService
                 (rebuilds projection)         (queries projection)
```

**Principle:** Projection is NOT source of truth. It's rebuildable from Asset + Semantic Metadata at any time. Delete it — reindex rebuilds it. Corrupt it — reindex fixes it.

## Tenant Context Fix

| Before (Sprint 022) | After (Sprint 023) |
|---------------------|-------------------|
| `TenantContext.get()` (ThreadLocal) | `tenantId` in job payload `${"tenantId":"t1","projectId":"p1","assetId":"a1"}` |
| Fails when task executor has no context | Works regardless of execution thread |

## Observability

```
Search reindex triggered: asset=a1 tenant=t1 event=asset.published
Search reindex job created: job=pjob_xx task=REINDEX
Task LEASED: id=ptsk_yy capability=REINDEX attempt=1/3
SearchReindexHandler: rebuilding projection for asset=a1 tenant=t1 reason=asset.published
SearchReindexHandler: projection persisted asset=a1 transcript=120 chars scenes=3 objects=5
Task COMPLETED: id=ptsk_yy
Job COMPLETED: id=pjob_xx type=SEARCH_REINDEX
```

## Tests (8 tests, all passing)

| Test | Scenario |
|------|----------|
| `AssetSearchConsumerTest` | Event → createJob(SEARCH_REINDEX) → createTask(REINDEX) |
| `SearchReindexTaskHandlerTest` (2) | Capability check, execution |
| `AssetSearchServiceTest` (5) | Filename search, transcript search, type filter, no-match, pagination |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| `AssetSearchService` still uses direct scan | Projection not yet wired into search service query path |
| No ElasticSearch | Full-text via PostgreSQL `ILIKE` on `search_text` column |
| No GIN index on `search_text` | PostgreSQL full-text search (`tsvector`) deferred |
| Scene/object/brands/people stored as CSV | Adequate for Phase 2. Normalize in Phase 3 if query complexity demands. |

## Deferred Items

| Item | Sprint |
|------|--------|
| Wire projection into AssetSearchService | Sprint 024 |
| PostgreSQL Full-Text Search (GIN/tsvector) | Sprint 024 |
| Marketplace consumer | Sprint 025 |
| Real FFprobe/ASR handlers | Sprint 026 |
