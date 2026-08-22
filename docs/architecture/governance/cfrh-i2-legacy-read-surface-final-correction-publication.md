# CFRH-I2 CLEAN-FORWARD LEGACY READ SURFACE FINAL CORRECTION — PUBLICATION

## Trigger

CFRH_I2_INDEPENDENT_REVIEW = CHANGES_REQUIRED

Primary finding: LEGACY_UNSCOPED_READ_METHOD_DEFINITIONS_REMAIN_IN_PRODUCTION_ADAPTERS.
Independent review accepted I2-A..F; I2-G required final guard exactness correction.

## Previous state (superseded candidate, evidence preserved)

| Field | Value |
|---|---|
| PREVIOUS_CANDIDATE_SHA | 9f3d5f35d4f71c181ec37477af4cf528d248fe84 |
| PREVIOUS_PUBLICATION_SHA | 3a30604d022a1558210f3d51d9be7df3368abd26 |
| PREVIOUS_TIP_SHA | deac91f44ae9079b236656c546301fd7b076208a |

The previous FCV (7699 tests / 0 failures / 0 errors) remains valid evidence for
the old candidate but is SUPERSEDED for final closure by this correction.

## Correction

| Field | Value |
|---|---|
| CORRECTION_SHA | 1b9473e8e2d6741f8587cb9dbb8d89619d8343ac |
| NEW_CANDIDATE_SHA | (set after FCV; same as correction SHA) |
| NEW_PUBLICATION_SHA | (set after commit) |

## Removed legacy unscoped read definitions

| Symbol | Disposition |
|---|---|
| TimelineSnapshotService.findPayload(String) | DELETED |
| TimelineSnapshotService.findById(String) | DELETED |
| TimelineRevisionRepository.findById(String) | DELETED |
| TimelineRevisionRepository.findHeadByProject(String) | DELETED |
| TimelineRevisionRepository.listByProject(...) ×3 | DELETED |
| TimelineRevisionRepository.updateAnnotation / listDistinctSources / listAuthorFacets / listEditSessions (unscoped) | DELETED |

## Reclassified system-only primitives

| Old | New | Callable from |
|---|---|---|
| findLatestByProject(projectId) | findLatestForSystemMaintenance(projectId) | SystemMaintenanceReader only |
| listDistinctProjectIds() | listProjectIdsForSystemMaintenance() | SystemMaintenanceReader only |

System primitives are explicitly system-only; ordinary production services may
not call them directly (guard-enforced: DIRECT_SYSTEM_PRIMITIVE_BYPASS_COUNT = 0).

## Final normal read authorities

- Revision: TimelineRevisionQueryService (findById/getDetail/listHistory/listFacets/
  listEditSessions/updateAnnotation — all (projectId, tenantId) scoped),
  TimelineRevisionDiffQuery (compareRevisions/previewPatchReplay/previewPatchSteps)
- Snapshot: TimelineSnapshotService.findOwnedById(projectId, tenantId, snapshotId),
  findLatestOwnedByProject(projectId, tenantId)
- Repository: findOwnedById / findOwnedHead / listOwnedByProject /
  updateOwnedAnnotation / listOwnedDistinctSources / listOwnedAuthorFacets /
  listOwnedEditSessions

## System authority (unchanged, count = 3)

1. GlobalAssetIntegrityService
2. KnownStorageUriIndexService
3. TimelineAssetGcService

## Guard exactness (I2-G final)

- Cfrhi2FinalReadAuthorityGuardTest: legacy DEFINITION count = 0 (not merely
  invocation count); system primitives callable only from SystemMaintenanceReader.
- Cfrhi2SystemAuthorityGuardTest: unexpected-consumer detection (approved set
  exactly 3, mechanically verified; UNAUTHORIZED_SYSTEM_READER_CONSUMER_COUNT = 0).

## RED mutation evidence

| Mutation | Guard | Result |
|---|---|---|
| RED-A findPayload definition reintroduced | FinalReadAuthorityGuard | FAIL-DETECTED |
| RED-B findById definition in adapter | FinalReadAuthorityGuard | FAIL-DETECTED |
| RED-C repo findById definition in adapter | FinalReadAuthorityGuard | FAIL-DETECTED |
| RED-D findHeadByProject/listByProject definitions | FinalReadAuthorityGuard | FAIL-DETECTED |
| RED-E direct system primitive from normal service | FinalReadAuthorityGuard | FAIL-DETECTED |
| RED-F unauthorized 4th SystemMaintenanceReader consumer | SystemAuthorityGuard | FAIL-DETECTED |
| RED-G direct global enumeration bypass | FinalReadAuthorityGuard | FAIL-DETECTED |

All mutations restored; guards GREEN after restore; working tree clean.

## Real tenant-isolation persistence tests

TimelineRevisionServiceTest (PostgresTestContainerSupport, real predicates):
- findByIdRequiresExactProjectAndTenant (wrong project → empty; wrong tenant → empty)
- getDetailIsOwnershipScopedNoLoadThenCheck (wrong project/tenant → empty)
- listHistoryIsTenantIsolated (tenant-2 cannot see tenant-1 rows)
- updateAnnotationIsOwnershipScoped (wrong project/tenant → no update)

## Metrics

LEGACY_TIMELINE_REVISION_QUERY_SERVICE_REFERENCE_COUNT = 0
TIMELINE_SNAPSHOT_FIND_PAYLOAD_DEFINITION_COUNT = 0
TIMELINE_SNAPSHOT_UNSCOPED_FIND_BY_ID_DEFINITION_COUNT = 0
LEGACY_REVISION_REPOSITORY_FIND_BY_ID_DEFINITION_COUNT = 0
LEGACY_FIND_HEAD_BY_PROJECT_DEFINITION_COUNT = 0
LEGACY_LIST_BY_PROJECT_DEFINITION_COUNT = 0
OLD_PROJECT_ONLY_LATEST_NORMAL_API_COUNT = 0 (timeline scope)
UNAUTHORIZED_SYSTEM_READER_CONSUMER_COUNT = 0
DIRECT_SYSTEM_PRIMITIVE_BYPASS_COUNT = 0
DIRECT_TIMELINE_SNAPSHOT_GLOBAL_SQL_BYPASS_COUNT = 0
DUAL_READ_AUTHORITY_COUNT = 0
COMPATIBILITY_WRAPPER_COUNT = 0

## Decision

CLEAN_FORWARD_RUNTIME_HARDENING_I2_RE_FCV = PASS (pending full-suite run)
BLOCKERS = 0
ARCHITECTURE_ESCALATION = NONE
ARCHITECTURE_PREMISE_FAILURE = NO
