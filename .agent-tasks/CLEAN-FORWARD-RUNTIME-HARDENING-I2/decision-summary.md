# CFRH-I2 — Decision Recovery Summary

## Scope
TIMELINE_READ_OWNERSHIP_AND_LEGACY_QUERY_CLOSURE.
Not: canonical redesign, revision-graph redesign, save-service redesign,
broad internal-1.0 cleanup, Roadmap #21/#22.

## Repository reality (base 5318a3fd)
- TimelineRevisionService retained: 11 behavior methods (TRQ-01..11), 389 lines.
- Production callers: 19 sites across controllers (2), render (2 services),
  editor-sync (1 service).
- Controller endpoints: 15 total; 10 call legacy query behavior; 5 canonical
  (restore/merge/render).
- P1 symbol set: 8 authoritative symbols verified (unchanged after I1).
- TimelineMergeEngine uses repository/snapshot global reads directly
  (L754/768/775) — in scope despite not being a service caller.

## Classifications (19 audited symbols/surfaces)
- PRODUCTION_AMBIENT_GLOBAL_READ_FORBIDDEN: 5
  (snapshot.findPayload, snapshot.findById, repo.findById, merge loadRevision,
   merge loadPayload-null-tenant)
- PROJECT_SCOPED_BUT_TENANT_NOT_VERIFIED: 6
  (findHeadByProject, listByProject, findLatestByProject, listHistory,
   listFacets, listEditSessions)
- LOAD_THEN_CHECK_OWNERSHIP: 5 (getDetail, compare, previewReplay,
  previewSteps, service findById)
- EXPLICIT_SYSTEM_AUTHORITY_EXCEPTION: 1 (listDistinctProjectIds)
- ALREADY_OWNERSHIP_SCOPED: 1 (updateAnnotation — project predicate)
- LEGACY_SERVICE_ONLY_AND_DISAPPEARS_WITH_I2: 1 (legacy read authority)
- SAFE (KEEP): findOwnedById (existing canonical safe API)

## Contracts (I2-C1..C15 adopted; no redundant new names)
- C1 explicit ownership context; C2 verified at/before persistence read;
  C3 ambient global forbidden; C4 system port required; C5 service not
  permanent authority; C6 deletion after behavioral replacement closure;
  C7 no unshipped overloads; C8 no dual authority; C9 query projections
  non-authoritative; C10 metadata distinct from semantic writes;
  C11 project-only accepted only with schema evidence; C12 load-then-check
  forbidden; C13 render/merge/internal callers in scope; C14 system
  exceptions explicit+auditable; C15 guard scope = frozen symbol set.

## Key dispositions
- TimelineRevisionService = DELETE_AFTER_BEHAVIORAL_REPLACEMENT_CLOSURE (I2-E)
- updateAnnotation = RETAIN_AS_OWNERSHIP_SCOPED_METADATA_COMMAND (add tenant)
- findOwnedById = KEEP (canonical safe read)
- listDistinctProjectIds = SystemMaintenanceReader port (I2-F)
- All other TRQ-01..11 = MIGRATE_TO_OWNERSHIP_SCOPED_QUERY

## Result
- BLOCKERS = 0
- UNRESOLVED_DISPOSITION_COUNT = 0
- ARCHITECTURE_PREMISE_FAILURE = NO
- ARCHITECTURE_ESCALATION = NONE
- READY_FOR_CFRH_I2_BOUNDED_IMPLEMENTATION = YES

## P1 duplicate-row guard follow-up
CLOSED in this decision recovery (see governance doc §I2.8: raw-symbol
dedupe in verify-cfrh-i1-single-source-of-truth.py).
