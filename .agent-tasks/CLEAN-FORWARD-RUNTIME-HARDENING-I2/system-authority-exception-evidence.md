# CFRH-I2 — System Authority Exception Evidence

## TimelineSnapshotService.listDistinctProjectIds

### Callers (production, 3 services / 4 call sites + 1 private bypass)
| Caller | Line | Purpose | Privilege context |
|---|---|---|---|
| GlobalAssetIntegrityService | 50 | global asset integrity scan | system maintenance |
| GlobalAssetIntegrityService | 102 | global integrity sweep | system maintenance |
| KnownStorageUriIndexService | 104 | storage-URI index rebuild | system maintenance |
| TimelineAssetGcService.runGlobalGc | 52 | global asset GC sweep | system maintenance (private jOOQ scan, now via SystemMaintenanceReader) |

Note: source-truth audit (2026-08-22) found THREE privileged consumers, not
two: TimelineAssetGcService.runGlobalGc previously enumerated projects through
a private jOOQ scan of TIMELINE_SNAPSHOT (bypassing the service layer). It is
now rewired through SystemMaintenanceReader.listProjectIdsWithSnapshots().

### Analysis
- All three callers perform system-level maintenance sweeps across ALL projects.
- None carries a per-project or per-tenant user context (system enumeration by design).
- The reads are legitimate system maintenance, NOT ambient tenant/user authority leakage.

### Verdict
- Classification: EXPLICIT_SYSTEM_AUTHORITY_EXCEPTION (category D) — 3 approved consumers
  (GlobalAssetIntegrityService, KnownStorageUriIndexService, TimelineAssetGcService).
- Implemented port: SystemMaintenanceReader (timeline-module app) exposing
  listProjectIdsWithSnapshots() + findLatestSnapshot(projectId); all three
  consumers rewired through it; the AssetGc private jOOQ bypass removed.
- MUST NOT remain ambient global authority reachable from the generic
  TimelineSnapshotService public surface.
- Required representation: an explicit privileged system port, e.g.
  SystemMaintenanceReader, with the global enumeration method moved behind it.
- Mechanically auditable: SYSTEM_CANONICAL_READ_REQUIRES_EXPLICIT_PRIVILEGED_PORT_V1.

### Current state
- No explicit SystemMaintenanceReader port exists yet (repository reality 5318a3fd).
- I2 implementation wave CFRH-I2-F introduces it and rewires the 3 callers.

## Conclusion
I2-C4 + SYSTEM_CANONICAL_READ_REQUIRES_EXPLICIT_PRIVILEGED_PORT_V1 adopted.
Explicit port creation is an I2 implementation task (docs/evidence only here).

## Independent-review final correction (append-forward)

Independent implementation review found LEGACY_GLOBAL_DEFINITION_SURFACE_REMAINED_AFTER_CALLER_MIGRATION:
adapter definitions of unscoped reads (findPayload, findById(snapshot), findLatestByProject,
repository findById/findHeadByProject/listByProject) survived even though all production callers
had migrated. Final correction (this execution) removed those definitions and reclassified the two
legitimate global primitives as explicit system-only surfaces:

- TimelineSnapshotService.findPayload(String)              -> DELETED
- TimelineSnapshotService.findById(String)                -> DELETED
- TimelineSnapshotService.findLatestByProject(projectId)  -> RENAMED findLatestForSystemMaintenance (system-only)
- TimelineSnapshotService.listDistinctProjectIds()        -> RENAMED listProjectIdsForSystemMaintenance (system-only)
- TimelineRevisionRepository.findById(String)             -> DELETED
- TimelineRevisionRepository.findHeadByProject(String)    -> DELETED
- TimelineRevisionRepository.listByProject(...) ×3        -> DELETED
- TimelineRevisionRepository.updateAnnotation/listDistinctSources/listAuthorFacets/listEditSessions (unscoped) -> DELETED

System primitives are callable ONLY from SystemMaintenanceReader (guard-enforced).
Approved consumer count remains 3 (unchanged).
