# Roadmap #20 Final Effect Target / Capability / Node Identity Closure — R6

Append-forward governance record — sixth bounded correction
(ROADMAP20_FINAL_EFFECT_TARGET_CAPABILITY_NODE_IDENTITY_CLOSURE_R6) closing the
two material blockers + one major finding raised by ChatGPT independent review
of R5.

## Chain

- R0 implementation `887f0c06` / publication `9538e73e`
- R1 implementation `8e0a11f4` / publication `d6660873`
- R2 implementation `4b6d6843` / publication `760b7e5c`
- R3 implementation `846121af` / publication `8f41caf4`
- R4 implementation `d1ea6c48` / publication `7a2b6a3a`
- R5 implementation `97fa2aa0` / publication `7e81abfb`
- **R6 implementation `082d3f19286eeb86bad05d01d5e6a0d81a34c408`
  (tree `b7997590fdaa65912caec4c2202d9e5f0297e1eb`)**
- R6 implementation parent: `7e81abfb16ca8097e38f1c0203b055c2667f573b`
- R6 publication: (this record, child of R6 implementation)

Append-forward only. R5 preserved untouched as historical evidence. No amend /
rebase / squash / reset / force-push.

## Independent verdict (R6 trigger)

- `ROADMAP_20_R5_INDEPENDENT_REVIEW = CORRECTION_REQUIRED`
- `ARCHITECTURE_PREMISE_FAILURE = NO`, `ARCHITECTURE_ESCALATION_REQUIRED = NO`
- `MATERIAL_BLOCKERS = 3`, `MAJOR_FINDINGS = 1`
- R5 matrix: A authentic ownership = FAIL; B logical effect payload =
  PARTIAL_PASS; B7 capability closure = FAIL; C6 effect node semantic identity
  = FAIL; E digest = PASS; F collection order = PASS; R4 passes all retained;
  BROKEN_PUBLIC_FACTORIES = MAJOR. `FORMAT_VERSION = KEEP_V1`.

## R5 overclaims corrected (append-forward, per instruction)

1. R5's "ownership check makes relabel impossible" — independent review found
   the overlap-based ownership check insufficient (same-time foreign effect
   could pass). Corrected by R6 authentic revision-owned membership.
2. R5's "capabilities resolved from same definition" — the R5 implementation
   actually used only category mapping and ignored
   `EffectDefinition.requiredCapabilities`. Corrected by R6 capability
   lowering (union rule).
3. R5's complete node semantic identity — R6 completes C6 local node identity
   (target + definition version + range + automation + temporal + effective
   capabilities in the node fingerprint).

The R5 historical record is NOT rewritten.

## Blocker A — R6-A: authentic effect target / membership

Root cause: R5's ownership check was `applicationRange overlaps any clip` — a
temporal heuristic. A foreign effect whose range overlapped a clip could be
labeled as belonging to that revision.

### Repository reality (R6-A2/A3)

- Authoritative wire effect membership lives in
  `TimelineCandidate.Track(trackId).Clip(clipId).effects[]` — the
  revision-owned aggregate parsed from the revision's wire JSON.
- `EffectInstance` previously carried NO target; render inferred association
  via `applicationRange().overlaps(clip.timelineRange())`.

### Resolution

- **`EffectTarget`** sealed root + **`ClipEffectTarget(trackId, clipId)`**
  (bounded: repository models clip-scoped effects only).
- **`EffectInstance`** now carries the typed authored target (10-arg
  constructor; legacy 9-arg constructor is @Deprecated and target-less
  instances FAIL CLOSED at the authority).
- **`RevisionOwnedEffectProjection`** — the ONLY membership authority: derived
  from `TimelineCandidate.Track.Clip.effects` (never from a caller label),
  preserving authored effect stack order.
- **`AuthoredEffectSemanticAuthority.issue(revision, projection, effects, defs)`**
  verifies per effect: explicit target; (trackId, clipId, effectInstanceId)
  membership EXISTS in the revision-owned projection; target clip/track exist
  in the revision's canonical timeline; unique effectInstanceId; unique
  (definitionId, version); definition exists + version matches; mediaType
  supported by definition. Any failure → FAIL CLOSED.
- **`effectsForClip`** selects by `effect.target() == (clip.trackId, clip.clipId)`
  — no temporal-overlap association. `applicationRange` remains a WHEN
  (materialization timing) semantic only.
- **R6-F**: typed target participates in the Effect domain canonical digest
  (two effects with identical params but different targets → different digest).

### Attack proofs (T1-T6, all FAIL CLOSED / verified)

- T1 same-time foreign effect (overlaps clip time, not a member) → fail closed
- T2 forged target (target=c1 but no revision-owned membership) → fail closed
- T3 two clips same range (effect authored under c1, assigned c2) → fail closed
- T4 valid revision-owned membership → PASS
- T5 missing target clip → fail closed
- T6 cross-revision (R1-owned effect + R2) → fail closed

## Blocker B — R6-B: capability closure

Root cause: R5 used only `RenderCapabilityVocabulary.forEffect(category)`
(category-level baseline) and ignored `EffectDefinition.requiredCapabilities`.

### Resolution

- **`RenderCapabilityVocabulary.forRequiredCapability(String)`** lowers an
  authored required-capability String through the platform capability
  authority: `CapabilityId.of(...)` validation (invalid → FAIL CLOSED) + exact
  platform contract 1.0 (`ContractVersionRange.exactly(1.0)`) — a documented R6
  bounded lowering rule, no per-provider invention.
- **`RenderCapabilityVocabulary.forEffect(category, definitionRequiredCapabilities)`**
  = category baseline capability UNION definition-required capabilities,
  deduplicated by capability id, deterministic order.
- The materializer consumes `definition.requiredCapabilities()` (R6-B3
  self-consistency: def-v1 → A, def-v2 → B even with same category/params).
- R6-B4: no raw-string capability authority in render; no provider/plugin/
  worker/device identity; dedup/order explicit.

### Tests (C1-C5)

- C1 definition required capability A → typed A in final node (plus baseline)
- C2 def-v2 required B → node has B (version-consistent)
- C3 capability change → node id changes AND plan fingerprint changes
- C4 invalid CapabilityId → FAIL CLOSED
- C5 duplicate capability input → deterministic dedup (chosen rule: first-seen
  order, category baseline first)

## Blocker C — R6-C: effect node semantic identity

Root cause: R5's effect node requirement fingerprint included only
capabilityRequirements + static parameters, so applicationRange / automation /
definition version / temporal / target changes did not change the node id.

### Resolution

- **Single canonical encoder**: `RenderPlanCanonicalCodec.effectMaterializationRequirementCanonical(requirement, capabilities)`
  is THE one grammar used by BOTH the effect node requirements fingerprint and
  the final RenderPlan canonical serialization (R6-C2 — no grammar drift).
- The node identity fingerprint now includes the COMPLETE local Effect WHAT:
  target, instance id, definition id, definition version, category, enabled,
  exact application range, parameters (shared pair encoder), automation
  references, temporal behavior, effective capabilities.
- **R6-C4**: the global `EffectSemanticReference` (whole authored state digest)
  remains in the plan fingerprint (R4-A3 preserved) — GLOBAL plan identity +
  LOCAL node identity, two layers.

### Tests (N1-N9)

- N1 param / N2 definition version / N3 range / N4 automation / N5 temporal
  behavior / N6 target / N7 effective capabilities / N8 category → each
  changes the effect node id.
- **N9 locality**: changing only effect e2 leaves e1's node id EXACTLY SAME
  (node identity is local, not global snapshot identity) — verified.

## Major — R6-D: broken public factories

- `EffectMaterializationRequirement.of(...)` and `.ofSorted(...)` (which passed
  empty strings and necessarily threw) are DELETED.
- The record now requires a typed target; the ONLY public construction path is
  `ofComplete(effect, definition, parameters, automationBindings)`.
- No invalid/incomplete construction path remains (guard + tests).

## R6-E — plan-only closure

`PlanOnlyConsumer` receives ONLY the `RenderPlan` and recovers, for each active
effect: instance id, target track/clip, definition id + version, category,
enabled, exact application start/end, parameters, automation bindings, temporal
behavior, effective capability requirements, the global EffectSemanticReference,
and the producer/input dependency relation — with exact value assertions
(P1). No EffectInstance / EffectDefinition / TimelineRevision / snapshot /
repository is passed.

`PHYSICAL_PLANNER_CAN_CONSUME_LOGICAL_EFFECT_WHAT_WITHOUT_AUTHORED_REREAD = YES`

## R6-H — effect stack order semantics

`EFFECT_STACK_ORDER_SEMANTICS = ORDERED` (audited): the wire
`TimelineCandidate.Clip.effects[]` list order is authored semantics; the
semantic projection and materialization chain preserve it; the canonical Effect
state distinguishes [e1, e2] from [e2, e1] (no instance-id re-sort — the old
R4/R5 id-sort was removed). H1 test: reversed stack → different domain digest +
different plan fingerprint + chain topology reflects authored order.

## R6-I — integrity

Duplicate effectInstanceId / duplicate (definitionId, version) / mediaType not
in definition supportedMediaTypes / definition version mismatch / unknown
definition → ALL FAIL CLOSED at the authority (I1-I5).

## Guard

`verifyC20RenderPlanBoundaryGuard` upgraded with 12 R6 structural assertions
(EffectTarget/ClipEffectTarget exist; RevisionOwnedEffectProjection exists;
authority verifies projection membership and never uses overlap; EffectInstance
carries target; target in domain canonical; materializer selects by target not
overlap; requirement carries target; of()/ofSorted() absent; requiredCapabilities
consumed by lowering; single canonical encoder used for node identity;
no instance-id re-sort; plan retains EffectSemanticReference). R2-R5 guards
retained (incl. R4 shared pair encoder no-delimiter, sealed fail-closed,
ColorDescription fail-closed, collection deep-sort).

## Format version

`RENDERPLAN_FORMAT_VERSION_COMPATIBILITY_REVIEW = KEEP_V1` — re-audited at R6
candidate freeze: 0 package-external planFingerprintCanonical /
RenderPlanCanonicalCodec consumers; render_plan_json = ProjectImportMetadata
(unrelated); 0 cache-key / API / GraphQL / serialized-artifact / fixture
contract / workflow-persistence consumers. Roadmap #20 remains unreleased.

## Test / gate results (final frozen SHA 082d3f19)

- R6 targeted (R6AcceptanceTest 30): T1-T6, C1-C5, N1-N9, P1, K1/K2/K4, H1,
  I1-I5 — **30 / 0 / 0**
- R5 regression (R5AcceptanceTest 14): PASS; R4 regression (16): PASS;
  LogicalWhatClosureAcceptanceTest: PASS; renderplan package: **139 / 0 / 0**
- render-module: **2897 / 0 / 0 / skip 19**; timeline-module: **771 / 0 / 0**;
  color-image: **20 / 0 / 0**; audio: **22 / 0 / 0**; font-text: **11 / 0 / 0**;
  extension: **314 / 0 / 0**; platform-app: **562 / 0 / 0 / skip 20**
- **FULL SUITE (recursive, --rerun-tasks, 176 tasks): 7588 / 0 / 0 / skip 43**
- bootJar: PASS; pfirr1RemediationCheck: PASS (+ verify tasks); C20 guard: PASS
  (55 files); Modulith: PASS; git diff --check: PASS
- Architecture drift: 222 PASS; CIP2G6 + CIP2DG12 =
  **PRE_EXISTING_BASELINE_FAIL** — signature re-verified at R6 (both triggered
  by `RenderOutputRequirement` importing `platform.colorimage`
  ColorDescription/RasterSampleDescription — the frozen C14/C8 contract; file
  NOT modified by R6; zero new color-image references; exact rule/path/count
  unchanged). CORRECTION_REGRESSION = NO.

## Scope audit

- Production: EffectTarget/ClipEffectTarget/RevisionOwnedEffectProjection (+),
  AuthoredEffectSemanticAuthority (membership verification), EffectInstance
  (typed target), EffectSemanticStateCanonicalSemantics (target in digest,
  stack order), RenderCapabilityVocabulary (lowering/union),
  EffectMaterializationRequirement (target, factories removed),
  DefaultRenderMaterializer (target selection, capability union, single-encoder
  node identity), RenderPlanCanonicalCodec (single encoder + target),
  build.gradle.kts (R6 guards).
- Tests: R6AcceptanceTest (+30), TestPlans + R3/R4/R5/LogicalWhat adaptation.
- Scope drift: NONE. No provider/physical-planner/worker/device semantics, no
  Timeline schema migration, no persistence/GraphQL changes, no Roadmap #21/#22.

## Blockers / escalation

- MATERIAL_BLOCKERS = 0; MAJOR_FINDINGS = 0
- ARCHITECTURE_ESCALATION = NONE; NEW_REGRESSIONS = 0
- FORMAT_VERSION_REVIEW_REQUIRED = NO (KEEP_V1 with audited evidence)

## Final recommendation

**READY_FOR_CHATGPT_R6_INDEPENDENT_REVIEW**

Roadmap #20 is NOT closed. Merge to main NOT authorized. Roadmap #21/#22 NOT
started. Closure remains ChatGPT's decision.
