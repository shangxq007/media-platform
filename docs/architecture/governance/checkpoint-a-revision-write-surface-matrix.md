# CHECKPOINT_A — REVISION_WRITE_SURFACE_MATRIX (Round 5 + FINAL_CLOSURE_F1, regenerated from call-graph audit)

Gate: REVISION_WRITE_SURFACE_BYPASS_COUNT = 0
      PUBLIC_UNSAFE_CONSTRUCTOR_COUNT = 0
      NULL_DEPENDENCY_BYPASS_COUNT = 0
      PRODUCTION_CONSTRUCTOR_AMBIGUITY = 0
      EXPLICIT_AUTOWIRED_IN_CORRECTED_PRODUCTION_SURFACES = 0

Matrix (SURFACE | CLASSIFICATION | CREATES_NEW_REVISION | DIRECT_WRITER_OR_DELEGATES |
CANONICAL_GATE | ARTIFACT_PIN_EXTRACTION | ARTIFACT_PIN_VALIDATION | PIN_REGISTRATION_OR_COPY |
CAS_HEAD_PROTECTION | IDEMPOTENCY | TRANSACTION_BOUNDARY | SNAPSHOT_WRITE | HEAD_UPDATE |
PUBLIC_UNSAFE_CONSTRUCTOR | NULL_DEPENDENCY_BYPASS | BYPASS_POSSIBLE):

| SURFACE | CLASSIFICATION | CREATES | WRITES | CANON_GATE | PIN_EXTRACT | PIN_VALIDATE | PIN_REG | CAS_HEAD | IDEMPOTENT | TX_BOUNDARY | SNAP_WRITE | HEAD_UPDATE | UNSAFE_CTOR | NULL_BYPASS | BYPASS |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TimelineRevisionSaveService.saveRevision | DIRECT_TIMELINE_SEMANTIC_WRITER | YES | delegates snapshot/revision/pin/head | YES (always) | YES (extract) | YES (validate) | YES (registerRevisionPinsTx) | YES (updateCurrentRevisionTx) | YES (content hash dedup) | EXPLICIT_JOOQ (transactionResult) | saveTx | updateCurrentRevisionTx | 0 | 0 | NO |
| TimelineRevisionSaveService.restoreRevision | DIRECT_TIMELINE_SEMANTIC_WRITER | YES | delegates snapshot/revision/pin-copy/head | YES (always) | YES (extract) | YES (validate) | YES (copyRevisionPinsTx) | YES (updateCurrentRevisionTx) | NO (new identity) | EXPLICIT_JOOQ (transactionResult) | saveTx | updateCurrentRevisionTx | 0 | 0 | NO |
| TimelinePatchApplicationService | DELEGATING_TIMELINE_MUTATION_SURFACE | YES | via saveRevision/engine | YES (via gate) | via saveRevision | via saveRevision | via saveRevision | via saveRevision | via saveRevision | EXPLICIT_JOOQ (via saveRevision) | via saveRevision | via saveRevision | 0 | 0 | NO |
| TimelineMergeEngine.merge | DIRECT_TIMELINE_SEMANTIC_WRITER (persistent) | YES | delegates snapshot/revision/pin/head | YES (merged payload gate) | YES (extract pre-tx) | YES (validate pre-tx) | YES (registerRevisionPinsTx in-tx) | YES (updateCurrentRevisionTx CAS in-tx) | YES (merge hash dedup) | EXPLICIT_JOOQ (dsl.transactionResult; proxy-independent) | saveTx (tx.dsl) | updateCurrentRevisionTx (tx.dsl) | 0 | 0 | NO |
| TimelineMergeEngine.mergeSemantic | PREVIEW_ONLY | NO | none | YES | N/A | N/A | N/A | N/A | N/A | NONE (compute only) | none | none | 0 | 0 | NO |
| RevisionCommandApplyService | UNUSED_INTERNAL_MECHANICS | YES (mechanically inserts TIMELINE_REVISION rows if invoked) | none (NO production callers) | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 0 | 0 | NO |
| OperationPlanApplyService | UNUSED_INTERNAL_MECHANICS | YES (mechanically inserts revision rows + advances head if invoked) | none (NO production callers) | N/A | N/A | N/A | N/A | DB-enforced head CAS (conditional UPDATE, rows==1) | N/A | EXPLICIT_JOOQ | tx-aware | tx-aware CAS | 0 | 0 | NO |
| RevisionGraphService / other repository readers | READ_ONLY / GENERIC_REVISION_MECHANICS | NO | none | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 0 | 0 | NO |

FINAL_CLOSURE_F1 TimelineMergeEngine.merge row detail (required by spec section 12):

PUBLIC_ENTRYPOINT = SAFE
TRANSACTION_ENTRYPOINT = merge(request, resolutions) (same code path as merge(request) → merge(request, Map.of()) — the WRITE PHASE is explicit jOOQ in BOTH; no self-invocation-sensitive @Transactional on either)
TRANSACTION_MECHANISM = EXPLICIT_JOOQ (dsl.transactionResult(tx -> ...))
SNAPSHOT_WRITE_API = snapshotService.saveTx(tx.dsl(), ...)
REVISION_WRITE_API = revisionRepository.insertTx(tx.dsl(), row) + nextRevisionNumberTx(tx.dsl(), ...)
PIN_WRITE_API = artifactPinService.registerRevisionPinsTx(tx.dsl(), ...)
HEAD_CAS_API = currentRevisionService.updateCurrentRevisionTx(tx.dsl(), ...)
SELF_INVOCATION_TRANSACTION_BYPASS = NO (0 — no @Transactional anywhere in TimelineMergeEngine)
SAME_PHYSICAL_TRANSACTION_PROVEN = YES (real-PG: merge revision/snapshot/pins/head all roll back on pin FK failure)
BYPASS_POSSIBLE = NO

Call-graph safety (spec section 10):
- RevisionCommandApplyService: NOT a Spring bean, NO production caller, domain-neutral generic mechanics; cannot create a Timeline semantic bypass.
- OperationPlanApplyService: NOT a Spring bean, NO production caller, domain-neutral generic mechanics; cannot create a Timeline semantic bypass.
- Invariant: Timeline semantic mutation authority → canonical validation → artifact/source invariant → revision mechanics (never the reverse).

Audit method: repository search for every TIMELINE_REVISION writer + call-graph
search for each surface's production callers (grep across timeline-module /
render-module / platform-app main sources, excluding the defining file and
imports; test-only references excluded from production reachability).

## Surfaces
> RETIRED 2026-08-19 (POST_FINAL_REVIEW evidence cleanup): an earlier duplicate
> "Surfaces" matrix below this line contained stale rows (e.g.
> TimelineMergeEngine.merge described with "@Transactional (Spring)" and
> "registerRevisionPins", and 9-arg constructor counts) that contradicted the
> authoritative matrix at the top of this document. One authoritative matrix is
> retained — see the top of this file. Current frozen source (2fdd95c6):
> TimelineMergeEngine has a SINGLE 10-arg constructor (dsl added in
> FINAL_CLOSURE_F1), the persistent merge write phase is an EXPLICIT jOOQ
> transaction (dsl.transactionResult) with tx-aware writes
> (saveTx / insertTx / registerRevisionPinsTx / updateCurrentRevisionTx), and
> head mutation is a DB-enforced CAS (expected revision in UPDATE predicate).

## Call-graph safety confirmation (section 10)

- RevisionCommandApplyService (timeline-module/adapter): NO production callers in
  timeline-module / render-module / platform-app main sources (grep verified);
  not annotated @Service/@Component; instantiated ONLY by tests
  (RevisionCommandConcurrencyIT). It copies a pre-built plan payload with zero
  Timeline semantic parsing — it cannot learn TimelineSourceBinding/Artifact
  semantics because it never inspects payload content beyond revision mechanics.
- OperationPlanApplyService (render-module/app/plan): NO production callers;
  not annotated; test-only (OperationPlanConcurrencyIT). Same domain-neutral
  mechanics.
- Invariant holds: Timeline semantic mutation authority → canonical validation →
  artifact/source invariant → revision mechanics. Generic backends never learn
  Timeline/Artifact semantics.

## Round-5 corrections vs Round 4 (historical record — R5-era state)
> Historical note (2026-08-19): this section records the ROUND-5 corrections as
> they were at Round-5 freeze. POST-FINAL-REVIEW source (2fdd95c6) extends it:
> TimelineMergeEngine now has a SINGLE 10-arg constructor (FINAL_CLOSURE_F1
> added the root DSLContext for the explicit jOOQ write transaction), and the
> persistent merge transaction boundary is EXPLICIT_JOOQ (no @Transactional).
- TimelineMergeEngine: 7-arg public constructor REMOVED (was null-forwarding pin
  boundary). Single production constructor (9-arg at R5 freeze; 10-arg at
  FINAL_CLOSURE_F1) with requireNonNull on ALL dependencies. @Autowired removed
  (constructor injection via sole constructor). Nullable pin-skip
  (`if (artifactPinValidator != null && artifactPinService != null)`) removed —
  unconditional.
- TimelineRevisionSaveService: single 6-arg constructor with requireNonNull on
  ALL six dependencies. @Autowired removed. Nullable skips removed in
  saveRevision (validator null check), tx pin registration
  (artifactPinService != null), and restoreRevision (copyRevisionPinsTx
  conditional).
- PUBLIC_UNSAFE_CONSTRUCTOR_COUNT = 0
- NULL_DEPENDENCY_BYPASS_COUNT = 0
- PRODUCTION_CONSTRUCTOR_AMBIGUITY = 0
- EXPLICIT_AUTOWIRED_IN_CORRECTED_PRODUCTION_SURFACES = 0
- REVISION_WRITE_SURFACE_BYPASS_COUNT = 0
