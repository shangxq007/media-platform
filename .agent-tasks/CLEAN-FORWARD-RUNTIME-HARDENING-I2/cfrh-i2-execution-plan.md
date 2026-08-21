# CFRH-I2 — Bounded Implementation Plan (Decision Recovery)

Base: 5318a3fd (canonical main after I1 integration + guard fix)

## Architecture

TimelineRevisionService (retained query authority) is replaced by small bounded
ownership-scoped query services. NO giant god-service.

Targets:
- TimelineRevisionQueryService — findHead / findById / getRevisionSnapshotPayload
  / listHistory / listFacets / listEditSessions / getDetail (projectId+tenantId)
- TimelineRevisionDiffQuery — compareRevisions / previewPatchReplay /
  previewPatchSteps (projectId+tenantId)
- TimelineSnapshotService.findOwnedById — existing safe snapshot read (KEEP)
- SystemMaintenanceReader — explicit privileged port for listDistinctProjectIds

## Implementation Waves (caller replacement BEFORE service deletion)

### CFRH-I2-A — OWNERSHIP_SCOPED_REVISION_QUERY_FOUNDATION
- Introduce TimelineRevisionQueryService (projectId+tenantId predicates at
  persistence read).
- Introduce TimelineRevisionDiffQuery.
- Repository: add ownership-scoped query predicates
  (revision_id + project_id + tenant_id).
- Snapshot: tenant-aware findLatestByProject(projectId, tenantId).

### CFRH-I2-B — SNAPSHOT_READ_OWNERSHIP_CLOSURE
- Migrate all findPayload / findById(snapshot) callers to findOwnedById:
  RenderJobExecutionService:590, BaseJobTimelineLoader:56,
  TimelineRevisionRenderService:129, PlanBasedTimelineRevisionRenderService:187,
  TimelineAssetLifecycleService:115, TimelineEditorSyncService:70.
- Resolve BaseJobTimelineLoader project-context gap (tenant-only today).

### CFRH-I2-C — CONTROLLER_AND_RENDER_CALLER_MIGRATION
- TimelineRevisionController: 11 query endpoints switch to query services
  (projectId+tenantId from TenantContext).
- TimelineWorkbenchController: facets/compare callers migrated.
- TimelineRevisionRenderService:113 + PlanBasedTimelineRevisionRenderService:174
  findById → ownership-scoped query.

### CFRH-I2-D — COMPARE_PREVIEW_MERGE_READ_CLOSURE
- compareRevisions / previewPatchReplay / previewPatchSteps: ownership-scoped
  base reads (no global repo.findById).
- TimelineMergeEngine: loadRevision(L754) → ownership-scoped findById;
  loadPayload(L768) → findOwnedById always; findAcceptedDuplicate(L775) →
  tenant predicate in SQL (not Java filter).

### CFRH-I2-E — LEGACY_TIMELINE_REVISION_SERVICE_DELETION
- After all callers migrated: delete TimelineRevisionService class.
- Delete TRQ-01..11 methods; legacy service reference count → 0.

### CFRH-I2-F — SYSTEM_AUTHORITY_EXCEPTION + STRUCTURAL_GUARDS
- Introduce SystemMaintenanceReader; rewire GlobalAssetIntegrityService +
  KnownStorageUriIndexService.
- Structural guard: KNOWN_UNSCOPED_PRODUCTION_TIMELINE_READ_SYMBOL_COUNT = 0;
  LEGACY_TIMELINE_REVISION_QUERY_SERVICE_REFERENCE_COUNT = 0.
- Guard scope = exact frozen symbol set (STRUCTURAL_GUARD_SCOPE_MUST_MATCH_MECHANICALLY_AUDITED_SYMBOL_SET_V1).

### CFRH-I2-G — FINAL QUERY AUTHORITY ZERO/OWNERSHIP GATES
- Final gates: zero unscoped production timeline reads; zero legacy service
  references; all ownership predicates at persistence read.

## Sequencing rule
CALLER REPLACEMENT BEFORE LEGACY SERVICE DELETION (I2-E last among migrations).
