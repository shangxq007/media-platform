# Roadmap #20 Logical WHAT Closure Correction R4

Append-forward governance record — fourth bounded correction
(ROADMAP20_LOGICAL_WHAT_CLOSURE_CORRECTION_R4) closing the two material
blockers + one major finding raised by ChatGPT independent review of R3
(authored effect binding, parameter canonical framing, single effect semantic
authority).

## Chain

- R0 implementation `887f0c06` / publication `9538e73e`
- R1 implementation `8e0a11f4` / publication `d6660873`
- R2 implementation `4b6d6843` / publication `760b7e5c`
- R3 implementation `846121af` (tree `a7c11f1e`) / publication `8f41caf4` (tree `4a940840`)
- **R4 implementation `d1ea6c48c97a9b89618588fb6ff704ee070dc627`
  (tree `57a6e5118196e18e0e48b37c192c23bd66407f2c`)**
- R4 implementation parent: `8f41caf47a7a3acdeb17eb20ebeea3d49cffaff2`
- R4 publication: (this record, child of R4 implementation)

Append-forward only. R3 preserved as historical evidence. No amend / rebase /
squash / reset / force-push of any correction commit.

## Independent verdict (R4 trigger)

- `ROADMAP_20_R3_INDEPENDENT_REVIEW = CORRECTION_REQUIRED`
- `ARCHITECTURE_PREMISE_FAILURE = NO`
- `ARCHITECTURE_ESCALATION_REQUIRED = NO`
- Blockers: (1) authored Effect state binding — effect pin not in final
  RenderPlan identity/provenance; factory could combine timeline R1 +
  unrelated internally-valid Effect state R2. (2) Effect parameter canonical
  framing — `key + ":" + value` / `key + "=" + value` delimiter flattening.
  Major finding: dual Effect semantic grammar (render-side
  `VerifiedEffectSemanticSnapshotFactory` grammar vs Timeline
  `EffectCanonicalSemantics` authority).

## R4-A — AUTHORED EFFECT STATE BINDING

### R4-A1 — authoritative effect binding

New Timeline/Effect domain authority:
- **`EffectSemanticBinding`** (timeline-module `semantics/effect`): immutable
  typed binding of {revisionId, immutable effect state digest, semantic
  contract version `effect-semantics-v1`}. Construction via
  `EffectSemanticBinding.of(revisionId, effects, definitions)` delegates digest
  computation to the single domain authority — it is NOT a caller-made hash.
- **`VerifiedEffectSemanticSnapshotFactory.verified(effects, definitions, binding)`**:
  recomputes the authoritative digest via the domain authority and fails
  closed on mismatch.
- **`VerifiedRenderSemanticSnapshotFactory.verified(...)`**: fails closed when
  `binding.revisionId()` != `timelineRevision.revisionId()` — verified timeline
  R1 + unrelated but internally-valid Effect state R2 cannot pass the boundary.

### R4-A2 — pin enters the FINAL Logical RenderPlan

- **`EffectSemanticReference`** (renderplan package): typed, immutable,
  provider-neutral value carrying the authoritative binding (revision id +
  content digest + contract version). The final `RenderPlan` record now carries
  `effectSemanticReference` (non-null, guard-asserted).

### R4-A3 — pin participates in the fingerprint

`RenderPlanFingerprintCalculator.compute(...)` and
`RenderPlanCanonicalCodec.planFingerprintCanonical(...)` now include
`effectSemanticReference.semanticContractVersion()` and
`effectSemanticReference.effectStateDigest()`. Authored Effect semantic change
(applicationRange, definition version, automation binding, enabled state,
parameters) ALWAYS changes the fingerprint — proven by tests even when the
materialized node structure stays identical (e.g. applicationRange narrowing
still overlapping the same clip; definition version change with identical
category/parameters; automation binding addition; enabled false).

### R4-A4 — provenance is truthful

`RenderPlanProvenance` now = {plannerFormatVersion, timelineRevisionId,
effectSemanticReference}. It EXPLAINS the semantic inputs; it is NOT a
fingerprint participant (PROVENANCE_LINEAGE_V1 preserved). The R3 publication's
claim that provenance carried the verified timeline reference + effect pin is
now true in code, not only in the governance record.

### R4-A5 — logical WHAT completeness

The Effect domain authority (`EffectSemanticStateCanonicalSemantics`) explicitly
classifies SEMANTIC fields (instanceId, definitionId, definitionVersion,
mediaType, enabled, applicationRange, parameters, automationBindings) vs
PROVENANCE fields (source/sourceId/createdAt — excluded from the canonical
digest, proven by test `effectProvenanceFieldsAreExcludedFromSemanticDigest`).
Future physical planner obtains: effect instance identity, definition identity,
definition version, category (via definition), enabled, application range,
static parameters, automation bindings — from the Logical RenderPlan without
re-reading authored Effect state.

## R4-B — EFFECT PARAMETER CANONICAL FRAMING

Defect: `p.key() + ":" + p.value()` (plan canonical) and
`parameter.key() + "=" + parameter.value()` (node requirement identity) allowed
(`"a:b","c"`) and (`"a","b:c"`) to collapse.

Fix: **`EffectSemanticStateCanonicalSemantics.encodeParameterPair(key, value)`**
— key and value framed independently (length-prefixed), used by BOTH the node
requirement identity path (DefaultRenderMaterializer) and the final plan
canonical serialization (RenderPlanCanonicalCodec). One shared implementation;
no second delimiter grammar. Adversarial tests prove:
- (`"a:b","c"`) != (`"a","b:c"`); (`"a=b","c"`) != (`"a","b=c"`)
- hostile values `: = ; , " \ ünïcödé 🙂 empty` all deterministic, no collision
- unordered parameter maps (semantic collections) → identical canonical bytes
- duplicate keys: parameter maps are Java `Map` (no duplicates possible in the
  typed model); ordering is non-semantic and deep-sorted.

## R4-C — SINGLE EFFECT SEMANTIC AUTHORITY

Timeline/Effect owns authored Effect semantics; render consumes:
- **`EffectSemanticStateCanonicalSemantics`** (timeline-module
  `semantics/effect`): THE one canonical encoder for
  `EffectInstance`/`EffectDefinition` semantic state (semantic vs provenance
  field classification, count-framed sections, independent field framing,
  SHA-256 digest). It is the semantic-layer counterpart of the wire-layer
  `TimelineClipEffect`/`EffectCanonicalSemantics` authority (different layers,
  deliberately not merged — ONE SEMANTIC AUTHORITY, not necessarily ONE JAVA
  TYPE; bridge documented).
- Render-side: `VerifiedEffectSemanticSnapshotFactory` delegates digest
  computation to the authority; the R3 render-side `encodeEffectState` grammar
  was REMOVED (replaced by delegation). `RenderPlanCanonicalCodec` only
  canonicalizes the authoritative reference/digest and derived render
  materialization requirements — it no longer re-decides which EffectInstance
  fields are domain semantics.

## R4-M — SEALED CANONICALIZATION FAIL-CLOSED

- `ColorDescription` canonicalizer: added explicit `else throw` for unknown
  future subtype (previously could return partial/empty encoding).
- `ColorPrimaries` (R3) and `RenderMaterializationRequirement` (R3) fail-closed
  branches retained.
- Audit of all fingerprint-participating sealed roots:
  ColorDescription / ColorPrimaries / RenderMaterializationRequirement /
  RenderArtifactReference / RenderDependency: every root either has an explicit
  fail-closed branch or is a sealed interface whose variants each implement a
  mandatory `variantKey()` (compile-time exhaustive; no generic fallback).

## Guard (R4-M2)

`verifyC20RenderPlanBoundaryGuard` extended with structural assertions:
RenderPlan carries `EffectSemanticReference`; fingerprint includes
`effectSemanticReference.effectStateDigest()`; provenance exposes the
reference; `VerifiedRenderSemanticSnapshotFactory` fails closed on
cross-revision binding; no `key + ":" + value` / `key + "=" + value` in
Effect canonical/identity paths; single shared pair encoder used; ColorDescription
fail-closed branch present. R2/R3 guards retained.

## Format version review

`RENDERPLAN_FORMAT_VERSION_COMPATIBILITY_REVIEW = KEEP_V1`
Repository compatibility audit performed at R4 candidate freeze:
- No package-external consumer of `RenderPlanCanonicalCodec` /
  `planFingerprintCanonical` / `PLAN_FORMAT_VERSION` (0 references outside the
  renderplan package).
- `render_plan_json` DB column belongs to ProjectImportMetadata (import
  metadata; unrelated to RenderPlan fingerprint).
- `RenderRequestFingerprint` (render dedup) is request-level and does not
  reference the RenderPlan canonical codec.
- No serialized RenderPlan artifact, no cache key dependency on the plan
  fingerprint, no API/GraphQL consumer, no released artifact/library consumer.
- Roadmap #20 remains an unreleased correction chain (not merged to main).
→ KEEP_V1, consistent with R1/R2/R3 governance.

## Test / gate results (final frozen SHA d1ea6c48)

- R4 targeted (R4AcceptanceTest 16): binding integrity (cross-revision fail
  closed, digest mismatch fail closed, same-state → same digest/fingerprint,
  different-state → different), pin retention (final plan + provenance), A3
  fingerprint participation (applicationRange / definition version /
  automation binding / enabled), B framing adversarial (delimiter separation,
  hostile values, order-insensitivity, shared encoder), M fail-closed
  structural, A5 provenance exclusion — **16 / 0 / 0**
- renderplan package: **96 / 0 / 0**
- render-module: **2853 / 0 / 0 / skip 19**
- timeline-module: **771 / 0 / 0**; color-image-module: **20 / 0 / 0**;
  audio-module: **22 / 0 / 0**; font-text-module: **11 / 0 / 0**;
  extension-module: **314 / 0 / 0**; platform-app: **562 / 0 / 0 / skip 20**
- **FULL SUITE (recursive, --rerun-tasks, 176 tasks): 7544 / 0 / 0 / skip 43**
- bootJar: PASS; pfirr1RemediationCheck: PASS (+ all pfirr verify tasks);
  verifyC20RenderPlanBoundaryGuard: PASS (R2+R3+R4 guards, 55 files);
  Modulith: PASS; git diff --check: PASS
- Architecture drift: 222 PASS; CIP2G6 + CIP2DG12 =
  **PRE_EXISTING_BASELINE_FAIL** — signature RE-VERIFIED at R4 (both are
  render-module → `platform.color.` reference via the frozen C14/C8
  RenderOutputRequirement→colorimage contract, identical on baseline
  `9538e73e`; R4 adds no color-image reference; new types reference
  timeline.semantics.effect only, the frozen Render→Timeline direction).
  CORRECTION_REGRESSION = NO.

## Scope audit

- Production: EffectSemanticBinding (+), EffectSemanticStateCanonicalSemantics
  (+), EffectSemanticReference (+), VerifiedEffectSemanticSnapshot /
  Factory (binding delegation + digest recomputation),
  VerifiedRenderSemanticSnapshotFactory (cross-revision fail-closed),
  RenderPlan (reference field), RenderPlanCanonicalCodec (fingerprint
  participant + shared pair encoder + ColorDescription fail-closed),
  RenderPlanFingerprintCalculator, RenderPlanProvenance,
  DefaultRenderPlanner, DefaultRenderMaterializer, build.gradle.kts (R4 guards).
- Tests: R4AcceptanceTest (+), TestPlans + R3AcceptanceTest + 3 graph/scale
  tests (adaptation).
- Scope drift: NONE. No physical planner, no provider/worker/device semantics,
  no capability authority redefinition, no TimelineDocument restructuring, no
  new persistence, no Roadmap #21/22 work.

## #22 forward-compatibility proof

Given only the Logical RenderPlan (+ future PlanningContext), a future physical
planner can resolve effect category + typed parameters, audio gain/mute/balance,
complete TimedText raster WHAT, source artifact/content pin, output
requirements, platform CapabilityRequirements AND the authoritative Effect
semantic reference (identity + content digest + contract version) — WITHOUT
rereading authored Effect state. Effect provenance fields remain explanatory
(not canonical WHAT).

PHYSICAL_PLANNER_CAN_CONSUME_LOGICAL_WHAT_WITHOUT_AUTHORED_REREAD = YES

## Blockers / escalation

- MATERIAL_BLOCKERS = 0
- ARCHITECTURE_ESCALATION = NONE
- NEW_REGRESSIONS = 0
- NEW_ARCHITECTURE_VIOLATIONS = 0
- FORMAT_VERSION_REVIEW_REQUIRED = NO (KEEP_V1 with audited evidence)

## Final recommendation

**READY_FOR_CHATGPT_R4_INDEPENDENT_REVIEW**

Roadmap #20 is NOT closed. Merge to main NOT authorized. Roadmap #21/#22 NOT
started. Closure remains ChatGPT's decision.
