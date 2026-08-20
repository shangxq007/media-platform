# Roadmap #20 Effect Authority Binding — Decision Recovery

Append-forward governance record — architecture decision recovery
(ROADMAP20_EFFECT_AUTHORITY_BINDING_DECISION_RECOVERY) freezing the final
unresolved foundational architecture question of Roadmap #20: the canonical
authority model for Effect semantics, after the R6 independent review raised
ARCHITECTURE_ESCALATION_REQUIRED = YES with three material blockers.

This round is a DECISION round. `PRODUCTION_IMPLEMENTATION_CHANGES = 0`.

## Trigger

- `ROADMAP_20_R6_INDEPENDENT_REVIEW = CORRECTION_REQUIRED`
- `ARCHITECTURE_PREMISE_FAILURE = NO`
- `ARCHITECTURE_ESCALATION_REQUIRED = YES`
- `MATERIAL_BLOCKERS = 3`:
  1. R6-A_REVISION_PROJECTION_BINDING — the RevisionOwnedEffectProjection is
     not revision-bound (no revisionId/digest/pin inside), so R1 + projection
     from unrelated candidate R2 passes when ids match.
  2. R6-A_WIRE_TO_TYPED_SEMANTIC_BINDING — wire TimelineClipEffect
     (id/effectKey/parameters) does not deterministically/losslessly bind the
     typed EffectInstance surface (version/mediaType/enabled/range/automation/
     target), so a caller can reuse a real wire id with different typed state.
  3. R6-H_AUTHORITATIVE_STACK_ORDER — stack order authority is the
     caller-supplied List, not the authored wire clip.effects[] order.
- Passes retained: R6-A typed target, no-overlap ownership, R6-B capability
  closure, R6-C local node identity (with followup), R6-D factory cleanup,
  R6-E plan-only payload, R6-F target in digest. `FORMAT_VERSION = KEEP_V1`.

## Repository reality (evidence: /tmp/ROADMAP20_EFFECT_AUTHORITY_DECISION_RECOVERY/repository-reality.txt)

- Wire layer: `TimelineClipEffect(id, effectKey, parameters)` — exactly three
  fields, authored via `TimelineImportRequest.ImportClipEffect` (same 3),
  canonicalized by `EffectCanonicalSemantics` (wire-layer codec authority).
  NO enabled / applicationRange / automationBindings / definitionVersion /
  mediaType / explicit target in the wire model.
- Typed layer: `EffectInstance`/`EffectDefinition` have **ZERO production
  construction points** — no catalog, no repository, no version store, no
  content digest, no hydration service.
- Revision persistence is PATH-INCONSISTENT:
  - command-apply path (`RevisionCommandApplyService`) stores the raw wire
    payload as the snapshot → effects[] persisted; contentHash over wire JSON
    → effects covered.
  - save path (`TimelineRevisionSaveService`) serializes `TimelineDocument`
    (which has NO effects field — the E9 projection gap) → effects dropped;
    contentHash over TimelineDocument → effects not covered.
- Diff/merge/semantic-diff operate on the WIRE payloads (`findPayload`), so
  effects participate in merge only when persisted via the command-apply path.
- Automation: composition-level `automations[]` (CanonicalAutomationCurve)
  exists in the wire layer; effect-level automationBindings has no wire
  binding and no immutable automation asset.
- Application range / enabled / mediaType: no wire field, no authoring path —
  caller-supplied in the typed model today.

## End-to-end effect trace (evidence: effect-end-to-end-trace.txt)

Gaussian Blur: import (3-field wire) → candidate projection → serialization
(path-dependent effects retention) → revision creation (path-dependent hash) →
snapshot storage → reload (wire payload) → typed hydration (**does not exist
in production; caller-assembled in tests**) → definition resolution (**no
catalog**) → RenderPlanningInput (caller-assembled) → RenderPlan (derived).

Mutable latest lookups present: none — there is no catalog at all; the typed
state itself has no persisted source. This is the root of the escalation.

## Authority matrix (evidence: authority-matrix.txt)

Caller-owned today: effectDefinitionVersion, target (explicit), mediaType,
enabled, applicationRange, automationBindings, ALL EffectDefinition fields,
stack order. Wire-owned today: effectInstanceId, effectDefinitionId (effectKey),
parameters (path-dependent persistence). Any field whose authority owner is
"caller" fails the decision contract — hence decision recovery is required.

## Option comparison (evidence: option-comparison.txt)

- Option A (TimelineClipEffect becomes complete canonical model): strongest
  natural stack-order fit, but high Timeline coupling (god-object risk),
  wire/canonical schema migration, merge-contract changes, Effect domain
  authority absorbed into Timeline. Fails criteria 8/9.
- Option B (independent immutable Effect semantic snapshot pinned by
  revision): revision authenticity structural (pin), one authority (snapshot),
  stack order snapshot-owned, historical reproducibility, no second revision
  DAG, Timeline pins but does not own Effect schema, Effect domain keeps
  authority, versioned contract. Satisfies criteria 1-12.
- Option C (reduce typed model to wire authority): minimal change but
  discards the R5/R6 Logical WHAT closure and cannot exact-resolve
  definitions/automation historically. Fails criteria 2/4/6.

## Selected architecture

```
ARCHITECTURE_ESCALATION_RESOLUTION = B
```

Frozen decision names:
1. `EFFECT_SEMANTIC_SNAPSHOT_PINNED_BY_TIMELINE_REVISION_V1`
2. `TIMELINE_REVISION_PINS_IMMUTABLE_EFFECT_SEMANTICS_V1`
3. `WIRE_EFFECT_IS_PROJECTION_NOT_COMPLETE_TYPED_AUTHORITY_V1`
4. `EFFECT_STACK_ORDER_IS_AUTHORED_ORDERED_SEMANTICS_V1`
5. `RENDER_CONSUMES_VERIFIED_EFFECT_SNAPSHOT_NOT_CALLER_EFFECT_LISTS_V1`

Conceptual contract:
```
TimelineRevision
  → (pins) EffectSemanticSnapshotReference(snapshotId, contentDigest,
          semanticContractVersion)
      → (resolves to) immutable EffectSemanticSnapshot
          (ordered target-bound Effect semantic entries; exact version-pinned
           definition references; typed fields; deterministic digest)
Render consumes: VerifiedRenderSemanticSnapshot(VerifiedTimelineRevision,
  VerifiedEffectSemanticSnapshot) hydrated ONLY from the revision-pinned
  snapshot.
```
Invariant: the caller CANNOT pair TimelineRevision R1 + EffectSnapshot from R2
and obtain a verified RenderPlanningInput — R1→S1 pin is verified, not chosen.

## Sub-decisions (evidence: definition-authority.txt, automation-authority.txt,
application-range-authority.txt, stack-order.txt)

- EffectDefinition authority = A: immutable versioned canonical definition
  asset. `EFFECT_DEFINITION_PINNING_V1`: definitionId + definitionVersion
  required; definitionContentDigest required once content may evolve. Today no
  definition store exists → exact CONTENT resolution recorded as a bounded
  limitation until the catalog exists.
- Automation: `AUTOMATION_REFERENCE_IS_ALREADY_AUTHORITATIVE = NO`. #20
  bounded semantics = typed reference string carried in the snapshot
  (reproducible REFERENCE, not curve content). Limit recorded.
- Application range: `APPLICATION_RANGE_AUTHORITY_V1 = DERIVED` (default =
  target clip extent) until explicit persisted effect-range authoring exists.
- enabled = persisted snapshot field; mediaType = DERIVED
  (supportedMediaTypes ∩ track kind).
- Stack order: `EFFECT_STACK_ORDER_IS_AUTHORED_ORDERED_SEMANTICS_V1` —
  authority = snapshot ordered entries (derived from wire clip.effects[]
  order); digest preserves order; caller-supplied order forbidden; merge
  semantics owned by Timeline/Effect domain.

## Persistence / transaction direction (evidence: storage-options.txt,
transaction-boundary.txt)

Preferred storage direction: canonical serialized Effect document bound to the
revision snapshot payload (TimelineSnapshotService-aligned) or a dedicated
immutable snapshot row keyed by EffectSemanticSnapshotId, content-addressed.
NOT an Artifact data-plane object. Transaction boundary: create revision R1 +
persist snapshot S1 + bind R1→S1 ATOMIC (aligned with the existing
transactionResult/tx paths). No revision-without-snapshot or
snapshot-without-pin states.

## Schema evolution / merge authority

Effect semantic contract versioned (effect-semantics-v1; future v2); old
snapshots remain interpretable; semantic migration creates a NEW revision
(never rewrite historical canonical state; never silently normalize).
`TIMELINE_OWNS_REVISION_GRAPH_AND_COMPOSITION_MERGE`;
`EFFECT_DOMAIN_OWNS_EFFECT_SEMANTIC_EQUALITY_DIFF_MERGE`. The snapshot is an
immutable semantic asset — NOT a second revision DAG.

## Historical reproducibility (2026 R100 / 2027 blur v2 scenario)

YES under B: R100 pins S100 (blur@1, radius=4, automation ref, stack #2);
2027 v2 changes do not mutate S100; exact WHAT reconstructable from S100 +
versioned definition (content digest) + automation reference — once the
future snapshot + definition catalog implementation exists.

## Render boundary

Render receives the verified snapshot pair; never issues Effect authority,
never constructs arbitrary snapshots, never rereads mutable authored state.
Render responsibility: verify reference, derive Logical Effect WHAT,
capability lowering, plan canonicalization, node identity, graph projection.

## Next bounded implementation correction scope (defined, NOT executed)

A. introduce immutable EffectSemanticSnapshot / reference contract
B. bind exact snapshot to Timeline revision semantic context
C. hydrate typed Effect semantics only from snapshot authority
D. remove public/caller assembly of revision+projection+effects+definitions
E. make stack order snapshot-owned
F. pin exact EffectDefinition semantics/version (+ content digest when catalog
   exists)
G. wire Render to verified snapshot
H. preserve R6 capability/node identity/Logical WHAT passes
I. append-forward publication + FCV

Expected modules: timeline-module (snapshot contract + revision pin + hydration),
render-module (consume verified snapshot), tests, guard, governance docs.
Explicit non-goals: no universal asset abstraction; no provider/physical
planner; no second revision DAG; no Artifact data-plane conflation.

## Governance

- DECISION_RECOVERY_BLOCKED = NO
- ARCHITECTURE_ESCALATION_RESOLVED = YES (Option B selected with evidence)
- SCOPE_DRIFT = NONE (production implementation changes = 0)
- MERGE STATUS = NOT MERGED (main 07de0092 unchanged)
- ROADMAP_20 STATUS = NOT CLOSED; #21/#22 = NOT STARTED
- No claim of ROADMAP_20_PASS / CLOSED / MERGE_AUTHORIZED.

## Evidence

- `/tmp/ROADMAP20_EFFECT_AUTHORITY_DECISION_RECOVERY/` (13 files +
  MANIFEST.sha256): repository-reality, effect-end-to-end-trace,
  authority-matrix, option-comparison, definition-authority,
  automation-authority, application-range-authority, stack-order,
  revision-binding (in final-decision), storage-options,
  transaction-boundary, schema-evolution, final-decision, commands.log.

**READY_FOR_CHATGPT_EFFECT_AUTHORITY_BINDING_DECISION_REVIEW**
