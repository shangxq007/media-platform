# Roadmap #20 Effect Authority — Option B Contract Closure

Append-forward governance record — contract closure
(ROADMAP20_EFFECT_AUTHORITY_OPTION_B_CONTRACT_CLOSURE) freezing the four
underspecified contracts of the accepted Option B architecture, converting it
from architectural direction to implementation-grade non-forgeable contract.

This is a GOVERNANCE / CONTRACT CLOSURE round.
`PRODUCTION_IMPLEMENTATION_CHANGES = 0`.

## 1. Trigger

- `ROADMAP20_EFFECT_AUTHORITY_BINDING_DECISION_REVIEW = CONTRACT_CLOSURE_REQUIRED`
- `OPTION_B_SELECTION = PASS` — architecture frozen, `REOPEN_A_B_C = NO`
- `ARCHITECTURE_ESCALATION_RESOLUTION = B — ACCEPTED`
- Four contracts remain underspecified: (1) revision semantic commitment to the
  Effect snapshot pin, (2) snapshot as primary typed authority with wire as
  derived projection, (3) exact historical EffectDefinition pinning,
  (4) legacy enabled/applicationRange/mediaType/automation V1 rules.

## 2. Option B accepted — A/B/C closed (not reopened)

Frozen architecture (unchanged):
`EFFECT_SEMANTIC_SNAPSHOT_PINNED_BY_TIMELINE_REVISION_V1`,
`TIMELINE_REVISION_PINS_IMMUTABLE_EFFECT_SEMANTICS_V1`,
`WIRE_EFFECT_IS_PROJECTION_NOT_COMPLETE_TYPED_AUTHORITY_V1`,
`EFFECT_STACK_ORDER_IS_AUTHORED_ORDERED_SEMANTICS_V1`,
`RENDER_CONSUMES_VERIFIED_EFFECT_SNAPSHOT_NOT_CALLER_EFFECT_LISTS_V1`.

No new A/B/C comparison. No reopening of whether EffectSemanticSnapshot is
needed.

## 3. Contract Gap #1 — revision semantic commitment

FROZEN: `EFFECT_SNAPSHOT_REFERENCE_PARTICIPATES_IN_TIMELINE_REVISION_SEMANTIC_IDENTITY_V1`

Repository reality: `TimelineContentDigester` hashes `TimelineDocument` which
carries NO Effect semantic state or pin (E9 gap); the revision-row snapshotId
relation alone is NOT a canonical semantic commitment.

Required invariant:
```
TimelineRevisionSemanticContent =
    TimelineCanonicalContent
    + EffectSemanticSnapshotReference
      (EffectSemanticSnapshotId, Effect semantic ContentDigest,
       Effect semantic contract version)
```
- Changing S1→S2 for the same composition MUST change the revision semantic
  content digest and MUST require a NEW Timeline revision.
- R1:S1 → R1:S2 mutation is FORBIDDEN.
- No DB-only hidden semantic relation (DB row = persistence of the canonical
  relation, never an independent source of semantic truth).
- Commitment shape = bounded higher-level semantic envelope over
  (timelineDigest, effectSnapshotReference) — NOT a universal envelope god
  object.

Legacy: `LEGACY_REVISION_EFFECT_PIN_POLICY_V1` — missing pin ⇒ no authoritative
typed Effect semantics (never "silently load latest", never "caller provides");
legacy command-path wire effects eligible for deterministic hydration;
save-path revisions that dropped effects fail closed for advanced Effect Render
reconstruction; old revisions never rewritten.

## 4. Contract Gap #2 — snapshot is primary typed authority

FROZEN: `EFFECT_SEMANTIC_SNAPSHOT_IS_PRIMARY_TYPED_AUTHORITY_WIRE_EFFECT_IS_DERIVED_PROJECTION_V1`

Canonical direction: Effect authoring → typed semantics → snapshot → revision
pin. Optional projection: snapshot → TimelineClipEffect wire (id/effectKey/
parameters). FORBIDDEN: TimelineClipEffect → caller fills missing fields →
snapshot (wire cannot losslessly supply definitionVersion/enabled/automation/
sub-range/mediaType).

Minting: `EFFECT_SNAPSHOT_MINTING_IS_DOMAIN_AUTHORITY_ONLY_V1` — caller may
REQUEST edits; caller may NOT ISSUE authoritative snapshot identity/digest/
content. No public `new EffectSemanticSnapshot(callerEffects, callerDefs)`
minting path.

Legacy hydration: `LEGACY_WIRE_EFFECT_HYDRATION_IS_DETERMINISTIC_OR_FAIL_CLOSED_V1`
(see table in §7).

## 5. Contract Gap #3 — exact EffectDefinition pinning

FROZEN: `EFFECT_DEFINITION_SEMANTICS_ARE_EXACTLY_PINNED_IN_EFFECT_SNAPSHOT_V1`

Model **D1** (snapshot EMBEDS exact definition semantics with deterministic
definition content digest) — chosen because no external immutable definition
store exists (EffectDefinition has zero production construction points, no
version store, no content digest). D2 (external pin: id+version+digest) is a
valid future alternative once such a store exists.

`EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1`: same (definitionId,
version) with different content digest = FAIL CLOSED. Content digest computed
over canonical semantic fields; any semantic change changes the digest;
historical def@1 stays exact after def@2.

## 6. Contract Gap #4 — legacy field rules (V1)

- `LEGACY_EFFECT_ENABLED_DEFAULT_V1 = TRUE`
- `APPLICATION_RANGE_AUTHORITY_V1 = DERIVED_FROM_TARGET_CLIP_EXTENT`
- `EFFECT_MEDIA_TYPE_IS_DERIVED_V1` (track kind ∩ exact definition
  supportedMediaTypes; incompatible = FAIL CLOSED)
- `AUTOMATION_REFERENCE_IS_ALREADY_AUTHORITATIVE = NO`;
  `UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1`:
  V1 automationBindings MUST be EMPTY unless every non-empty binding references
  a revision-owned immutable exact automation object (none exists) ⇒
  non-empty automation = FAIL CLOSED / UNSUPPORTED. No arbitrary
  Map<String,String> caller refs become canonical WHAT. Future direction:
  typed immutable AutomationSemanticReference (recorded only, not implemented;
  no UniversalAutomationAsset).

## 7. Legacy hydration table

| wire field | hydrate to |
|---|---|
| wire.id | effectInstanceId |
| wire.effectKey | effectDefinitionId |
| containing track/clip | ClipEffectTarget(trackId, clipId) |
| wire.parameters | Effect parameters |
| applicationRange | DERIVED = target clip extent |
| enabled | TRUE |
| mediaType | DERIVED (track kind ∩ supportedMediaTypes; else FAIL CLOSED) |
| automationBindings | EMPTY (unless authoritative immutable binding exists) |
| definitionVersion | MUST NOT be invented; unresolvable = FAIL CLOSED |

Never caller input for definitionVersion / automation / enabled /
applicationRange / mediaType.

## 8. EffectDefinition field classification

- AUTHORITATIVE SEMANTIC: definitionId, version, category, supportedMediaTypes,
  parameterSchema, temporalBehavior, deterministicProperties,
  requiredCapabilities.
- EXECUTION/PROVIDER METADATA (NOT canonical authored Effect semantics, NOT
  pinned into snapshot): `supportedBackendCapabilities` — describes which
  backend/provider can execute the Effect; do not pin provider names into
  snapshot identity.
- Provenance: EXPLANATORY / NON-AUTHORITATIVE (R4 preserved) — never affects
  Effect/definition digest, revision semantic identity, or node identity.

## 9. Effect stack order (implementation-grade)

Per-target ordered stacks: track canonical order → clip canonical order →
ordered effect stack. No global semantic list across unrelated clips. No
sort-by-id / set / map normalization; caller-selected order forbidden.
Reorder changes snapshot digest + revision semantic identity + effect
chain/topology. Locality: changing c2 stack never perturbs c1 stack or c1 node
identity (R6 N9 preserved).

## 10. Snapshot identity vs content digest

`EffectSemanticSnapshotId` (stable handle) ≠ `EffectSemanticSnapshotContentDigest`
(semantic commitment). Two snapshots with identical content MAY share digest
and differ in id unless content-addressed identity is chosen. Never conflate
object identity with semantic equality.

## 11. Revision binding authority

Verification derives the expected Effect reference from R. Forbidden:
`verify(R, callerChosenS)` unless `R.expectedEffectSnapshotReference ==
S.reference`. Attack (R1 pins S1, caller provides S2 — same targets/ids/
timeline/ranges) = FAIL CLOSED.

## 12. Transactional atomicity

Future implementation: persist S1 + persist R1 committing S1 + relation/index +
canonical head move — atomic. No observable accepted canonical state with
R1-without-S1, pin mismatch, or S1-without-R1-commitment. Reuse existing
transaction-scoped repository patterns.

## 13. Persistence implementation freedom

Physical storage direction free (A: embed in revision snapshot payload;
B: dedicated immutable snapshot row; C: content-addressed store) — invariants
mandatory: immutable, versioned, digest-verifiable, exact historical read,
atomic with revision, not Artifact data plane, no second revision DAG. Physical
choice finalized during implementation after repository inspection.

## 14. Timeline wire dual-path retirement

`ONE_CANONICAL_EFFECT_REVISION_PERSISTENCE_PATH_V1`: the command-apply
(effects survive) vs save-path (TimelineDocument serialization drops effects)
inconsistency MUST be eliminated. Every canonical creation path produces/
resolves the exact EffectSemanticSnapshot, pins it into revision semantic
identity, persists atomically, reloads exactly. No compatibility wrapper
solely to preserve the broken dual path.

## 15. Render boundary

Render receives `VerifiedRenderSemanticSnapshot(VerifiedTimelineRevision,
VerifiedEffectSemanticSnapshot)` — snapshot exactly the one pinned by revision
semantic identity. Never List<EffectInstance>/List<EffectDefinition>/
RevisionOwnedEffectProjection/caller binding as authority. R6 capability
closure (category baseline UNION exact pinned requiredCapabilities) and node
locality preserved. Materialized derived fields in RenderPlan are valid.

Capability encoder unification (§33): one canonical CapabilityRequirement
encoder = CapabilityId + ContractVersionRange for BOTH local node identity and
RenderPlan serialization — latent dual grammar confirmed in current code
(codec encodes id@range; node-id helper encodes id only) and MUST be unified.

## 16. Schema evolution

Snapshot contract `effect-semantics-v1` (future v2). Old snapshots readable;
no silent latest normalization; semantic migration creates a new Timeline
revision; old snapshots and definition content pins immutable; never rewrite
old historical pins.

## 17. Cross-cutting roadmap non-interference

OPERATION_ALGEBRAIC_CONTRACT_V1 / SEMANTIC_ANALYSIS_FOUNDATION_V1 /
SEMANTIC_REWRITE_SYSTEM_V1 and their principles are ADOPTED cross-cutting
foundations. `ROADMAP_20_SCOPE_CHANGE = NO` — no Operation Algebra, no
optimizer, no DSL here. Effect semantic authority is made clean enough to
support future Operation semantic analysis.

## 18. Future acceptance matrix (frozen, not implemented)

RP1-5 (revision pin), SA1-5 (snapshot authority), D1-6 (definition), SO1-4
(stack order), L1-5 (legacy), R1-5 (render) — 26 attacks. See test-matrix.txt.

## 19. Next bounded implementation correction (frozen, NOT executed)

Workstreams A-P: snapshot model+codec+digest; definition snapshot (D1); snapshot
reference; revision semantic commitment; atomic persistence/hydration; legacy
deterministic hydration; retire caller authority APIs; verified factory from
pin only; Render verified boundary; ordered stacks; automation empty/fail-closed;
R6 capability/node preservation; capability identity audit; structural guard;
targeted+module+full FCV; append-forward publication.

Explicit non-goals: plugin marketplace, full definition catalog/marketplace,
generic asset framework, automation subsystem, physical planner, FFmpeg/GPU
provider, Operation Algebra/DSL, user compute network, generic workflow, second
Effect revision DAG, Artifact data-plane conflation.

## 20. Governance state

- Production changes: NONE. Scope drift: NONE.
- Merge status: NOT MERGED (main 07de0092 unchanged).
- Roadmap #20: NOT CLOSED. Roadmap #21/#22: NOT STARTED.
- No claim of ROADMAP_20_PASS / CLOSED / MERGE_AUTHORIZED.

## Evidence

`/tmp/ROADMAP20_EFFECT_AUTHORITY_OPTION_B_CONTRACT_CLOSURE/` — contract-summary,
revision-semantic-commitment, legacy-hydration-table, definition-pinning,
definition-field-classification, automation-rule, stack-order-contract,
render-boundary, test-matrix, implementation-scope, repository-state,
commands.log, MANIFEST.sha256. Governance evidence, not FCV (zero-code commit).

**READY_FOR_CHATGPT_OPTION_B_CONTRACT_CLOSURE_REVIEW**
