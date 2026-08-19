# CHECKPOINT_A — REVISION_WRITE_SURFACE_MATRIX (Round 5, regenerated from call-graph audit)

Gate: REVISION_WRITE_SURFACE_BYPASS_COUNT = 0
      PUBLIC_UNSAFE_CONSTRUCTOR_COUNT = 0
      NULL_DEPENDENCY_BYPASS_COUNT = 0
      PRODUCTION_CONSTRUCTOR_AMBIGUITY = 0
      EXPLICIT_AUTOWIRED_IN_CORRECTED_PRODUCTION_SURFACES = 0

Audit method: repository search for every TIMELINE_REVISION writer + call-graph
search for each surface's production callers (grep across timeline-module /
render-module / platform-app main sources, excluding the defining file and
imports; test-only references excluded from production reachability).

## Surfaces

| SURFACE | CLASSIFICATION | CREATES_NEW_REVISION | DIRECT_WRITER_OR_DELEGATES | CANONICAL_GATE | ARTIFACT_PIN_EXTRACTION | ARTIFACT_PIN_VALIDATION | PIN_REGISTRATION_OR_COPY | CAS_HEAD_PROTECTION | IDEMPOTENCY | TRANSACTION_BOUNDARY | SNAPSHOT_WRITE | HEAD_UPDATE | PUBLIC_UNSAFE_CONSTRUCTOR | NULL_DEPENDENCY_BYPASS | BYPASS_POSSIBLE |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TimelineRevisionSaveService.saveRevision | DIRECT_TIMELINE_SEMANTIC_WRITER | YES | DIRECT | YES (canonical gate + normalize) | YES (extractPinsFromDocument) | YES (artifactPinValidator.validate) | YES (registerRevisionPinsTx same explicit jOOQ tx) | YES (expectedCurrentRevisionId CAS) | YES (new UUID per call; parent chain) | explicit dsl.transactionResult | YES (snapshotService.saveTx in tx) | YES (updateCurrentRevisionTx in tx) | NO (single 6-arg ctor, requireNonNull all) | NO (requireNonNull; no nullable skip) | NO |
| TimelineRevisionSaveService.restoreRevision | DIRECT_TIMELINE_SEMANTIC_WRITER | YES (new revision id) | DIRECT | YES | N/A (copies historical pins) | N/A (historical pins already validated) | YES (copyRevisionPinsTx for new id, same tx) | YES (CAS) | YES (new UUID) | explicit dsl.transactionResult | YES | YES | NO (same ctor) | NO (copyRevisionPinsTx unconditional) | NO |
| TimelinePatchApplicationService.apply | DELEGATING_TIMELINE_MUTATION_SURFACE | YES (via saveRevision delegate) | DELEGATES to TimelineRevisionSaveService | YES (delegate) | YES (delegate) | YES (delegate) | YES (delegate) | YES (delegate) | YES | delegate tx | YES (delegate) | YES (delegate) | NO | NO | NO |
| TimelineMergeEngine.merge (persistent) | DIRECT_TIMELINE_SEMANTIC_WRITER | YES (dual-parent merge revision) | DIRECT | YES (canonical gates ×4: base/source/target/merged) | YES (TimelineArtifactPinExtractor.extract on merged payload) | YES (artifactPinValidator.validate) | YES (registerRevisionPins for NEW merge revision id, same @Transactional) | YES (head CAS via currentRevisionService) | YES (accepted-duplicate hash check) | @Transactional (Spring) | YES | YES | NO (single 9-arg ctor, requireNonNull all) | NO (no nullable pin skip; pin boundary by construction) | NO |
| RevisionCommandApplyService | GENERIC_REVISION_MECHANICS | YES (copies plan payload verbatim) | DIRECT (mechanic) | N/A AT LAYER (upstream boundary; no Timeline semantic parsing) | N/A (no Timeline semantics) | N/A | N/A | YES (CAS) | YES | explicit tx | N/A | YES | NO (single ctor, no Spring annotation) | NO | NO (zero production callers; not a Spring bean; test-only instantiation) |
| OperationPlanApplyService | GENERIC_REVISION_MECHANICS | YES (copies operation plan payload) | DIRECT (mechanic) | N/A AT LAYER | N/A | N/A | N/A | YES (CAS) | YES | explicit tx | N/A | YES | NO (single ctor, no Spring annotation) | NO | NO (zero production callers; not a Spring bean; test-only instantiation) |

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

## Round-5 corrections vs Round 4
- TimelineMergeEngine: 7-arg public constructor REMOVED (was null-forwarding pin
  boundary). Single 9-arg production constructor with requireNonNull on ALL nine
  dependencies. @Autowired removed (constructor injection via sole constructor).
  Nullable pin-skip (`if (artifactPinValidator != null && artifactPinService !=
  null)`) removed — unconditional.
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
