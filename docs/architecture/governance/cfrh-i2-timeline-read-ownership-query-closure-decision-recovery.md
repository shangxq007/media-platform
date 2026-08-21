# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I2 TIMELINE READ OWNERSHIP AND LEGACY QUERY CLOSURE

## Decision Recovery

| Field | Value |
|---|---|
| BASE_SHA | 5318a3fd0477a92511ebb8dd1d56eaf6caa2ee41 |
| BASE_TREE | 8f30244a6d03667ac43d35dab170389c9e764086 |
| TASK_TYPE | DOCS / EVIDENCE ONLY |
| PRODUCTION_CHANGE_COUNT | 0 |
| TEST_CHANGE_COUNT | 0 |
| BUILD_CHANGE_COUNT | 0 |
| MIGRATION_CHANGE_COUNT | 0 |
| GENERATED_CHANGE_COUNT | 0 |

## 1. Scope

Close ownership-unscoped production reads and retire TimelineRevisionService
as a legacy query authority — WITHOUT disturbing the closed canonical write
architecture (CFRH-I1 postconditions remain).

NOT: canonical model redesign, revision-graph redesign,
TimelineRevisionSaveService redesign, broad internal-1.0 cleanup,
Roadmap #21/#22.

## 2. Repository reality (base 5318a3fd)

- TimelineRevisionService retained: 11 behavior methods (TRQ-01..11), 389
  lines, zero semantic write authority (I1 postcondition).
- Production callers: 19 sites — TimelineRevisionController (11 endpoints),
  TimelineWorkbenchController (3), TimelineRevisionRenderService (1),
  PlanBasedTimelineRevisionRenderService (1), TimelineEditorSyncService (3).
- Controller endpoints: 15 (mechanically derived); 10 call legacy query
  behavior; 5 canonical (restore/merge/render/render-jobs×2).
- P1 authoritative symbol set: 8 (unchanged after I1; re-verified).
- TimelineMergeEngine uses repository/snapshot global reads directly
  (loadRevision L754 repo.findById; loadPayload L768 findPayload;
  findAcceptedDuplicate L775 listByProject + Java tenant filter) — in I2 scope
  despite not being a TimelineRevisionService caller.

## 3. Behavior inventory (TRQ-01..11)

Full matrix: .agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-I2/
timeline-revision-query-behavior-matrix.tsv (11 behaviors, each with
current authority / callers / ownership context / predicates / disposition /
target authority / wave / guard).

Dispositions:
- TRQ-01..04, TRQ-06, TRQ-07, TRQ-09, TRQ-10, TRQ-11 = MIGRATE_TO_OWNERSHIP_SCOPED_QUERY
- TRQ-05 updateAnnotation = RETAIN_AS_OWNERSHIP_SCOPED_METADATA_COMMAND
- TRQ-08 compareRevisions = MIGRATE_TO_OWNERSHIP_SCOPED_QUERY

UNRESOLVED_DISPOSITION_COUNT = 0.

## 4. Ownership inventory (19 audited symbols/surfaces)

Classification (section 19 categories):

| Category | Count | Symbols |
|---|---|---|
| A PRODUCTION_AMBIENT_GLOBAL_READ_FORBIDDEN | 5 | snapshot.findPayload, snapshot.findById, repo.findById, merge loadRevision, merge loadPayload (null-tenant path) |
| B PROJECT_SCOPED_BUT_TENANT_NOT_VERIFIED | 6 | repo.findHeadByProject, repo.listByProject, snapshot.findLatestByProject, listHistory, listFacets, listEditSessions |
| C LOAD_THEN_CHECK_OWNERSHIP | 5 | getDetail, compareRevisions, previewPatchReplay, previewPatchSteps, service findById |
| D EXPLICIT_SYSTEM_AUTHORITY_EXCEPTION | 1 | snapshot.listDistinctProjectIds (3 system-maintenance callers) |
| E LEGACY_SERVICE_ONLY_AND_DISAPPEARS_WITH_I2 | 1 | TimelineRevisionService legacy read authority |
| F RECLASSIFIED_SAFE_WITH_EVIDENCE | 0 | — |
| G ALREADY_OWNERSHIP_SCOPED | 1 | updateAnnotation (repo predicate includes project_id) |
| SAFE (KEEP) | 1 | snapshot.findOwnedById (existing canonical safe API) |

## 5. Key dispositions

- TimelineRevisionService = DELETE_AFTER_BEHAVIORAL_REPLACEMENT_CLOSURE
  (NOT immediate deletion; I2-E after caller migration).
- findById(revisionId) → ownership-scoped query (project+tenant predicate).
- findHeadByProject → tenant-verified variant (schema evidence pending at impl).
- listByProject → tenant predicate in SQL (remove Java load-then-check filter).
- findPayload / findById(snapshot) → findOwnedById (already exists).
- findLatestByProject → tenant-aware signature.
- listDistinctProjectIds → SystemMaintenanceReader explicit privileged port.
- updateAnnotation → retained metadata command, add tenant predicate.
- compare/preview → ownership-scoped base reads (no global repo.findById).

## 6. Contracts (adopted I2-C1..C15)

I2-C1 production canonical reads carry explicit ownership context.
I2-C2 ownership verified at or before persistence read.
I2-C3 ambient global revision/snapshot lookup forbidden in production.
I2-C4 system-wide reads require explicit privileged/system authority port.
I2-C5 TimelineRevisionService is not a permanent architecture authority.
I2-C6 deletion requires behavioral replacement closure.
I2-C7 no unshipped compatibility overloads retained.
I2-C8 no dual legacy/new query authority after migration.
I2-C9 query/projection services are not canonical semantic authority.
I2-C10 metadata commands remain distinct from semantic revision writes.
I2-C11 project-only scoping accepted only where schema evidence proves it.
I2-C12 load-then-check ownership forbidden for final production canonical reads.
I2-C13 render/merge/internal callers included, not just HTTP controllers.
I2-C14 system maintenance exceptions explicit and mechanically auditable.
I2-C15 structural guard scope = exact frozen symbol/port set.

No redundant new contract names needed — existing frozen contracts
(OWNERSHIP_CONTEXT_IS_EXPLICIT_ON_PRODUCTION_CANONICAL_READS_V1,
NO_PRODUCTION_AMBIENT_GLOBAL_CANONICAL_OBJECT_LOOKUP_V1,
SYSTEM_LEVEL_GLOBAL_LOOKUP_REQUIRES_EXPLICIT_SYSTEM_AUTHORITY_PORT_V1,
OWNERSHIP_SCOPE_MUST_BE_VERIFIABLE_AT_OR_BEFORE_PERSISTENCE_READ_V1,
STRUCTURAL_GUARD_SCOPE_MUST_MATCH_MECHANICALLY_AUDITED_SYMBOL_SET_V1,
LEGACY_SERVICE_DELETION_REQUIRES_BEHAVIORAL_REPLACEMENT_CLOSURE_V1)
already cover the points.

## 7. Implementation waves

I2-A ownership-scoped revision query foundation (query services + predicates)
I2-B snapshot read ownership closure (findOwnedById migration, 6 callers)
I2-C controller and render caller migration (14 sites)
I2-D compare/preview/merge read closure (merge engine L754/768/775)
I2-E legacy TimelineRevisionService deletion (after caller closure)
I2-F system authority exception port + structural guards
I2-G final query-authority-zero / ownership gates

Sequencing rule: CALLER REPLACEMENT BEFORE LEGACY SERVICE DELETION.

## 8. P1 duplicate-row guard follow-up — CLOSED

verify-cfrh-i1-single-source-of-truth.py now retains raw P1 table symbols
before dict collapse and computes:

    P1_DUPLICATE_SYMBOL_COUNT = len(raw_symbols) - len(set(raw_symbols))

New mechanical check MG-35b = 0 PASS (P1_DUPLICATE_SYMBOL_COUNT = 0).
P1_DUPLICATE_ROW_GUARD_PRECISION = CLOSED.

Note: scope checks MG-44/45/46 report I1-production deltas when the validator
runs from the I2 branch because its reviewed predecessor (6f8f04fb) predates
the integrated I1 implementation; the I1 branch FCV already proved the
committed I1 scope (production=0 in its own range). P1 duplicate fix verified
independently: MG-35b PASS.

## 9. Readiness

BLOCKERS = 0
UNRESOLVED_DISPOSITION_COUNT = 0
ARCHITECTURE_PREMISE_FAILURE = NO
ARCHITECTURE_ESCALATION = NONE
READY_FOR_CFRH_I2_BOUNDED_IMPLEMENTATION = YES

## 10. Evidence files

.agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-I2/:
- timeline-revision-query-behavior-matrix.tsv (11 behaviors)
- timeline-query-caller-inventory.tsv (19 production call sites)
- timeline-controller-endpoint-inventory.tsv (15 endpoints)
- ownership-read-manifest.tsv (10 P1 + merge + metadata surfaces)
- repository-read-symbol-inventory.tsv (12 repository/snapshot read symbols)
- system-authority-exception-evidence.md (listDistinctProjectIds)
- cfrh-i2-execution-plan.md (waves I2-A..G)
- decision-summary.md
