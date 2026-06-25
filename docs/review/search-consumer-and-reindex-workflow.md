---
status: implementation-report
created: 2026-06-24
scope: render-module + outbox-event-module
truth_level: current
owner: platform
---

# Platform Foundation Sprint 022 — Search Consumer & Reindex Workflow

## Search Architecture Audit

### Current Search Model

Asset search (Sprint 009) uses in-memory keyword matching across filename + semantic metadata JSON. No event-driven index update. When an asset is enriched or published, the search index does NOT automatically refresh.

### After Sprint 022

Event-driven search: AssetPublished → SearchConsumer → Coordination job → REINDEX task → projection rebuilt.

## New Components (3)

| Component | Role |
|-----------|------|
| `AssetSearchConsumer` | `@EventListener` for asset events → creates SEARCH_REINDEX jobs |
| `SearchReindexTaskHandler` | `TaskHandler` (REINDEX capability) → rebuilds SearchProjection from Asset Registry + Semantic Metadata |
| `SearchProjection` | Unified search view record — derived from Asset + Semantic Metadata |

## Event → Job → Task → Projection Flow

```
AssetPublishedEvent (via outbox → dispatcher → Spring event)
    ↓
AssetSearchConsumer.onAssetPublished(event)
    ↓
coordinationService.createJob(SEARCH_REINDEX, ASSET, assetId)
    ↓
coordinationService.createTask(jobId, REINDEX, REINDEX, null, bitPosition=0)
    ↓ (NOTIFY platform_task → DispatchBooster → PlatformTaskDispatcher)
PlatformTaskDispatcher picks up REINDEX task
    ↓
TaskHandlerRegistry.resolve(REINDEX) → SearchReindexTaskHandler
    ↓
handler.execute(ctx):
  - Reads Asset from AssetRepository
  - Reads AssetSemanticMetadata
  - Extracts transcripts, scenes, objects
  - Builds SearchProjection
  - Logs completion
    ↓
coordinationService.completeTask(taskId)
    ↓
completedMask |= bit → barrier evaluation → job COMPLETED
```

## SearchProjection Model

| Field | Source |
|-------|--------|
| assetId | Asset.id |
| filename | Asset.filename |
| assetType | Asset.mediaType |
| transcriptText | AssetSemanticMetadata → transcripts.text (joined) |
| sceneLabels | AssetSemanticMetadata → scenes.label |
| objects | AssetSemanticMetadata → objects.label |
| brands | (reserved for future) |
| people | (reserved for future) |
| classification | Asset.classification |
| license | Asset.license |
| publishStatus | Asset.publishStatus |

**Principle:** Projection is rebuildable. Asset Registry + Semantic Metadata are the source of truth.

## Triggering Events

| Event | Consumer | Reason |
|-------|----------|--------|
| `AssetEnrichedEvent` | `onAssetEnriched` | New transcript/scene/object data available |
| `AssetPublishedEvent` | `onAssetPublished` | Mark as publicly discoverable |
| `AssetArchivedEvent` | `onAssetArchived` | Remove from public index |

## Barrier Integration

The REINDEX task is a single-task job (bit 0). When the task completes:
1. `coordinationService.completeTask()` updates `completedMask |= 1`
2. `evaluateBarrier()` checks `(1 & 1) == 1` → barrier satisfied
3. Job status → COMPLETED

For multi-task reindex (future): add SEARCH_REINDEX + MARKETPLACE_LISTING tasks with bits 0,1. Barrier fires when both complete.

## Observability

```
Search reindex triggered: asset=a1 event=asset.published
Search reindex job created: job=pjob_xx task=REINDEX
Task LEASED: id=ptsk_yy capability=REINDEX attempt=1/3
SearchReindexHandler: rebuilding projection for asset=a1
SearchReindexHandler: projection rebuilt: transcripts=120 chars scenes=3 objects=5
Task COMPLETED: id=ptsk_yy
Job COMPLETED: id=pjob_xx type=SEARCH_REINDEX
```

## Tests (3 tests, all passing)

| Test | Scenario |
|------|----------|
| `AssetSearchConsumerTest.shouldCreateReindexJobOnAssetPublished` | Event → createJob(SEARCH_REINDEX) → createTask(REINDEX) |
| `SearchReindexTaskHandlerTest.shouldReportReindexCapability` | Handler reports REINDEX capability |
| `SearchReindexTaskHandlerTest.shouldExecuteReindexForAsset` | Handler executes without throwing |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| SearchProjection not persisted | Built in-memory. Future: persist to search_projection table for search API consumption. |
| No ElasticSearch | Full-text search still using PostgreSQL ILIKE. ElasticSearch deferred. |
| No Vector DB | Semantic search not implemented. Vector DB deferred. |
| Single-bit job | REINDEX is bit 0 only. Multi-task jobs (REINDEX + NOTIFY + ANALYTICS) deferred. |
| Tenant context required | AssetRepository.findById needs tenant context. Task execution may not have it set. |

## Deferred Items

| Item | Sprint |
|------|--------|
| Persist SearchProjection to table | Sprint 023 |
| Wire SearchProjection into AssetSearchService | Sprint 023 |
| Marketplace consumer | Sprint 023 |
| ElasticSearch integration | 2027+ |
| Vector DB / semantic search | 2027+ |
