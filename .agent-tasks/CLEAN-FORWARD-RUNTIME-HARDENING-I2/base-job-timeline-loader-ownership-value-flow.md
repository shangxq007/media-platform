# CFRH-I2 — BaseJobTimelineLoader Ownership Value-Flow (FROZEN)

## CURRENT_VALUE_FLOW
```
AiTimelineEditService.editFromBaseJob(tenantId, baseJobId, instruction, context)  [L95]
  context.projectId() available [L106]
  → baseJobTimelineLoader.loadInternalTimelineJson(baseJobId, tenantId)  [L100-101]
IncrementalRenderOrchestrationService.tryResolve(newTimelineJson, baseJobId, tenantId, ...)  [L33]
  → baseJobTimelineLoader.loadInternalTimelineJson(baseJobId, tenantId)  [L47]
BaseJobTimelineLoader.loadInternalTimelineJson(baseJobId, tenantId)  [L34]
  → renderJobRepository.findTimelineDataById(baseJobId)  [L42]
  → TimelineData(tenantId, aiScript, timelineSnapshotId)
  → timelineSnapshotService.findPayload(snapshotId)  [L56]  ← UNSAFE GLOBAL READ
```

## AUTHORITATIVE_PROJECT_SOURCE
render_job table PROJECT_ID column (RenderJobRepository:38,50,66,72 select
PROJECT_ID). TimelineData currently does NOT project it
(findTimelineDataById selects only TENANT_ID/AI_SCRIPT/TIMELINE_SNAPSHOT_ID).
ProjectId is present in the same row but not threaded through.

Additionally AiTimelineEditContext.projectId() is already available at the
AiTimelineEditService call site (L106) — project context exists at caller.

## AUTHORITATIVE_TENANT_SOURCE
- Parameter tenantId (both callers).
- TimelineData.tenantId (from render_job.TENANT_ID).
- tenantGuard.requireJobTenant(tenantId, baseJobId) at loader entry [L39].

## CURRENT_UNSAFE_READ
timelineSnapshotService.findPayload(snapshotId) — BaseJobTimelineLoader.java:56
(global by snapshotId; no project/tenant predicate).

## TARGET_PORT
timelineSnapshotService.findOwnedById(projectId, tenantId, snapshotId)
(existing safe ownership-scoped API; already used by TimelineMergeEngine:762).

## TARGET_SIGNATURE
BaseJobTimelineLoader.loadInternalTimelineJson(String baseJobId, String tenantId, String projectId)
or loader-internal project resolution via TimelineData.projectId projection.

## TARGET_PERSISTENCE_PREDICATE
SELECT payload FROM timeline_snapshot WHERE id = ? AND project_id = ? AND tenant_id = ?

## TARGET_WAVE
I2-B (SNAPSHOT_READ_OWNERSHIP_CLOSURE)

## FINAL_DISPOSITION
THREAD_EXISTING_PROJECT_CONTEXT_TO_LOADER:
- TimelineData gains project_id projection (findTimelineDataById selects
  RENDER_JOB.PROJECT_ID).
- Loader passes projectId+tenantId to findOwnedById instead of findPayload.
- AiTimelineEditService call site may pass context.projectId() explicitly.

## UNRESOLVED_COUNT
0
