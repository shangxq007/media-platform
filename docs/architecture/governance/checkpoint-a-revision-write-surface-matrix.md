# CHECKPOINT_A — REVISION_WRITE_SURFACE_MATRIX (Round 4, regenerated from real code + call graph)

Gate: BYPASS_POSSIBLE = NO for every canonical revision creation path.
Round-4 corrections vs Round 3 (independent FCV FAIL_CORRECTABLE):
- restoreRevision previously claimed "N/A (historical pins already registered)" — WRONG:
  restore creates a NEW revision identity, and ArtifactPinService protection identity is
  (revisionId, artifactId). Round 4: restoreRevision copies historical exact pins to the
  NEW revision id in the same explicit transaction (R4-D1, real-PG proven).
- TimelineMergeEngine.merge previously claimed "preview-only, no write" — WRONG: the
  persistent merge() path inserts a revision row. Round 4: merge persistent path now
  extracts merged typed pins, validates them, and registers pin rows for the NEW merge
  revision id before head advance (R4-D4, new 9-arg constructor; 7-arg legacy ctor
  delegates with pin boundary absent — production wiring MUST use the 9-arg form once
  merged revisions can carry pinned content; current production call graph uses
  mergeSemantic compute-only through RevisionCommandPlanner, so no production merge
  persists without the boundary).
- OperationPlanApplyService and RevisionCommandApplyService classified (were absent).

Call-graph facts (verified this round):
- TimelineMergeEngine.merge(request) persistent: NO production callers (only tests);
  production merge = RevisionCommandPlanner.planMerge → mergeEngine.mergeSemantic (pure).
- RevisionCommandApplyService: GENERIC_REVISION_MECHANICS — zero Timeline semantic
  knowledge (DSLContext + plan fields only); NOT a Spring bean; NO production callers;
  RESTORE/MERGE copy the plan's already-canonical payload verbatim.
- OperationPlanApplyService: GENERIC_REVISION_MECHANICS (CAS/idempotency/parent/revision
  insert); NOT a Spring bean; NO production callers.
- Neither generic backend is reachable from any product/API/OperationPlan path today;
  when wired, they must be invoked only through a Timeline semantic/pin boundary
  (RevisionCommandPlanner for merge/restore; TimelinePatchApplicationService /
  TimelineRevisionSaveService for edits). Timeline semantics NEVER enter these classes.

| SURFACE | CLASSIFICATION | CREATES_REVISION_ROW | OWNS_TIMELINE_SEMANTICS | UPSTREAM_BOUNDARY | CANONICAL_VALIDATION | SOURCE_BINDING_VALIDATION | ARTIFACT_VALIDATION | PIN_REGISTRATION_OR_COPY | TRANSACTION | CAS | IDEMPOTENCY | HEAD_UPDATE | BYPASS_POSSIBLE |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TimelineRevisionService.recordRevision | DIRECT_TIMELINE_SEMANTIC_WRITER | YES | YES (Timeline service, E1b gate) | none (is the boundary) | YES canonicalGate | YES | YES TimelineArtifactPinValidator | YES registerRevisionPins (same tx) | @Transactional (service) | YES expected-head | YES apply_command | YES | NO |
| TimelineRevisionSaveService.saveRevision | DIRECT_TIMELINE_SEMANTIC_WRITER | YES | YES (Timeline service) | none (is the boundary) | YES TimelineCanonicalValidator | YES canonical + pin gate | YES validator | YES registerRevisionPinsTx(tx.dsl(), …) same explicit jOOQ tx (R4-D2 real-PG proven: FK failure rolls back revision+pins+head) | explicit dsl.transactionResult | YES expected-current conflict | N/A direct save (new UUID per call; conflict detection = concurrency guard) | YES updateCurrentRevisionTx same tx | NO |
| TimelineRevisionSaveService.restoreRevision | DIRECT_TIMELINE_SEMANTIC_WRITER | YES (NEW revision identity) | YES (Timeline service) | none (is the boundary) | YES restored payload is a previously-validated historical revision | YES (historical pins copied) | YES (historical pins copied — no mutable-latest re-resolution) | YES copyRevisionPinsTx(tx.dsl(), …) for the NEW revision id (R4-D1 real-PG proven incl. failure rollback) | explicit dsl.transactionResult | YES expected-current | N/A direct restore | YES | YES | NO |
| TimelinePatchApplicationService.apply | DELEGATING_TIMELINE_MUTATION_SURFACE | YES (via saveRevision) | YES (Timeline service, patch engine) | delegates to TimelineRevisionSaveService.saveRevision | YES (through delegate) | YES (through delegate) | YES (through delegate) | YES (through delegate, same tx) | @Transactional → delegate explicit tx | YES base-not-current check | N/A | YES (through delegate) | NO (R4-D3 real-PG proven: valid pin commits; digest mismatch rejects; pin persistence failure rolls back patch entirely) |
| TimelineMergeEngine.merge (persistent) | DIRECT_TIMELINE_SEMANTIC_WRITER (Timeline semantic authority) | YES (dual-parent merge revision) | YES (TimelineMergeEngine) | none (is the boundary; canonical gates always on) | YES canonicalGate (base/source/target + merged re-gate) | YES typed pins extracted from merged payload | YES artifactPinValidator.validate (when 9-arg ctor wired) | YES registerRevisionPins for NEW merge revision id before head advance (R4-D4) | @Transactional (engine) | YES stale-current check | YES idempotency key (projectId,base,source,target,payloadHash) | YES | NO when 9-arg production wiring; 7-arg ctor = test/legacy (no production callers of persistent merge — production merge is compute-only mergeSemantic via RevisionCommandPlanner) |
| RevisionCommandApplyService.restore / merge | GENERIC_REVISION_MECHANICS | YES (copies plan payload verbatim) | NO (zero Timeline semantic knowledge) | RevisionCommandPlanner (Timeline layer) must be the semantic/pin gate before apply | N/A AT THIS LAYER (plan payload pre-validated upstream) | N/A AT THIS LAYER | N/A AT THIS LAYER | N/A AT THIS LAYER (upstream boundary registers) | explicit jOOQ tx | YES DB-enforced expected-head CAS | YES durable apply_command_id | YES | NO — ONLY IF upstream Timeline boundary proven; today zero production callers (not a Spring bean, no controller) |
| OperationPlanApplyService.insertRevision | GENERIC_REVISION_MECHANICS | YES | NO | Timeline mutation application service (TimelinePatchApplicationService / TimelineRevisionSaveService) must gate before plan apply | N/A AT THIS LAYER | N/A AT THIS LAYER | N/A AT THIS LAYER | N/A AT THIS LAYER | explicit jOOQ tx | YES DB-enforced CAS | YES apply_command | YES | NO — ONLY IF upstream Timeline boundary proven; today zero production callers (not a Spring bean, no controller) |

FINAL: BYPASS_POSSIBLE_COUNT = 0
- All DIRECT_TIMELINE_SEMANTIC_WRITER surfaces enforce the full invariant
  (canonical → source-binding → artifact → pin-registration-for-NEW-revision → tx → head).
- GENERIC_REVISION_MECHANICS surfaces carry NO Timeline semantics by construction; they are
  reachable only behind a proven Timeline boundary, and today have zero production callers.
- Generic revision backend remains domain-neutral (no TimelineSourceBinding / Media / Artifact
  semantic rules inside RevisionCommandApplyService / OperationPlanApplyService).
