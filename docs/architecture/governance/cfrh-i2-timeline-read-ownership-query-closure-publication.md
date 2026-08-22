# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I2 TIMELINE READ OWNERSHIP AND LEGACY QUERY CLOSURE — PUBLICATION

## Milestone

CLEAN-FORWARD-RUNTIME-HARDENING-I2 — TIMELINE READ OWNERSHIP AND LEGACY QUERY CLOSURE BOUNDED PRODUCTION IMPLEMENTATION

## Recovery

| Field | Value |
|---|---|
| RECOVERY_MODE | EXACT_LOCAL_RECOVERY |
| BASE_SHA | d0fab2791c25a99cb17652e975f24b4dfc849b8d |
| BASE_TREE | acb60c879babbb0bb32073ac03223103896765e2 (d0fab279 tree) |
| CANDIDATE_SHA | 9f3d5f35d4f71c181ec37477af4cf528d248fe84 |
| CANDIDATE_TREE | cb7cd5b48be3ce39eb0acf6dc0e7a564f75fad0c |
| PUBLICATION_SHA | (set after commit) |

The four previously-reported implementation SHAs (c5a1b4fa, b7c46828, 1a1fe92d, dfd88601)
existed in the local Git object store (verified via git cat-file) and were recovered
unchanged; they were never pushed, hence absent from GitHub. Recovery required no
reimplementation.

## Wave SHAs

| Wave | SHA | Content |
|---|---|---|
| I2-A | c5a1b4fa | TimelineRevisionQueryService + TimelineRevisionDiffQuery + ownership-scoped repository predicates |
| I2-B | b7c46828 | snapshot read ownership closure (findOwnedById / findLatestOwnedByProject / BaseJob TimelineData project threading) |
| I2-C | 1a1fe92d | controller/render/editor-sync caller migration (22 legacy invocation sites → 0) |
| I2-D | c5a1b4fa + b7c46828 + 1a1fe92d | compare/preview/merge ownership closure (see below) |
| I2-E | dfd88601 | TimelineRevisionService deletion |
| I2-F | b720d5ad | SystemMaintenanceReader + privileged consumer closure + Cfrhi2SystemAuthorityGuardTest |
| I2-G | 0a2a5221 | Cfrhi2FinalReadAuthorityGuardTest (zero-authority metrics) |
| test waves | 5a1ffa24, 82843135 | test adaptation |
| gate fix | 4c6e6ece | verifyGcr2ArtifactAuthority updated for I2-E deletion |
| isolation fix | 9f3d5f35 | render Stub tenant-context isolation (final candidate) |

## I2-D explicit evidence

- TimelineRevisionDiffQuery (introduced c5a1b4fa): compareRevisions / previewPatchReplay /
  previewPatchSteps all read base revisions through findOwnedById(projectId, tenantId, id) —
  9/9 owned reads, 0 global reads, 0 load-then-check patterns.
- Controller/workbench call sites (migrated 1a1fe92d): all 7 diff/preview calls carry
  (projectId, TenantContext.get()) ownership context.
- TimelineMergeEngine (b7c46828): loadRevision → findOwnedById; loadPayload →
  findOwnedById (ambient fallback removed, fail-closed); findAcceptedDuplicate →
  listOwnedByProject (tenant predicate in SQL, no Java post-load filter).
- I2_D_IMPLEMENTATION_STATUS = PASS

## System authority (source-truth count = 3)

Approved privileged/system-authority consumers (mechanically derived from production source):

1. GlobalAssetIntegrityService
2. KnownStorageUriIndexService
3. TimelineAssetGcService (runGlobalGc)

The earlier DR evidence count of 2 was incomplete: TimelineAssetGcService.runGlobalGc
previously performed a private jOOQ scan of TIMELINE_SNAPSHOT (INVALID_BYPASS of the
service layer). That private scan was removed; the service now enumerates projects via
SystemMaintenanceReader.listProjectIdsWithSnapshots() and reads latest snapshots via the
privileged findLatestSnapshot(projectId) port. Evidence files
(system-authority-exception-evidence.md, ownership-read-manifest.tsv) were updated to 3.

## Final zero metrics

- LEGACY_TIMELINE_REVISION_QUERY_SERVICE_CLASS_COUNT = 0
- LEGACY_TIMELINE_REVISION_QUERY_SERVICE_REFERENCE_COUNT = 0
- LEGACY_TIMELINE_REVISION_QUERY_INVOCATION_SITE_COUNT = 0
- KNOWN_UNSCOPED_PRODUCTION_TIMELINE_READ_SYMBOL_COUNT = 0
- HTTP_ENDPOINT_WITH_LEGACY_QUERY_DEPENDENCY_COUNT = 0
- AMBIENT_GLOBAL_TIMELINE_SNAPSHOT_READ_COUNT = 0
- AMBIENT_GLOBAL_TIMELINE_REVISION_READ_COUNT = 0
- UNPRIVILEGED_SYSTEM_GLOBAL_TIMELINE_READ_COUNT = 0
- UNRESOLVED_OWNERSHIP_CONTEXT_COUNT = 0
- DUAL_READ_AUTHORITY_COUNT = 0
- COMPATIBILITY_WRAPPER_COUNT = 0

## Tests / Gates

| Check | Result |
|---|---|
| timeline-module | PASS (800 tests) |
| render-module | PASS (2970 tests) |
| platform-app | PASS (571 tests) |
| FULL SUITE (40 modules) | 7699 tests / 0 failures / 0 errors / 43 skipped |
| Cfrhi1LegacyWriteAuthorityGuardTest | PASS (3/3) |
| Cfrhi2SystemAuthorityGuardTest | PASS (3/3) |
| Cfrhi2FinalReadAuthorityGuardTest | PASS (4/4) |
| verifyGcr2ArtifactAuthority | PASS |
| pfirr1RemediationCheck | PASS |
| verifyC1Cnm1RedGates | PASS |
| jooqFoundationCheck | PASS |
| verifyTimelineEffectTransitionCanonicalization | PASS |
| :render-module:verifyC20RenderPlanBoundaryGuard | PASS |
| :platform-app:bootJar | PASS |
| RED/mutation | RED-1..6 FAIL-DETECTED (2 probe rounds), guards GREEN after restore |

## Decision

- CLEAN_FORWARD_RUNTIME_HARDENING_I2_FCV = PASS
- BLOCKERS = 0
- ARCHITECTURE_ESCALATION = NONE
- ARCHITECTURE_PREMISE_FAILURE = NO
- TimelineRevisionService = DELETED
- SystemMaintenanceReader = IMPLEMENTED
- BaseJob ownership disposition = THREAD_EXISTING_PROJECT_CONTEXT_TO_LOADER
- Restore write authority = CANONICAL_UNCHANGED
- Restore response legacy query dependency = REMOVED
