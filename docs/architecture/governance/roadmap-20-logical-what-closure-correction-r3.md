# Roadmap #20 Logical WHAT Closure Correction R3

Append-forward governance record — third bounded correction
(ROADMAP20_LOGICAL_WHAT_CLOSURE_CORRECTION_R3) closing the two material
blockers + one major finding raised by ChatGPT independent review of R2.

## Chain

- Original implementation (R0): `887f0c06` / publication `9538e73e`
- Correction implementation R1: `8e0a11f4` (tree `f87fd0ec`) / publication `d6660873` (tree `381aa2c8`)
- Correction implementation R2: `4b6d6843` (tree `0e6b4802`) / publication `760b7e5c` (tree `aab0f259`)
- **Correction implementation R3: `846121afa1ae3463fa9e61300fb28fc39f2908ce`
  (tree `a7c11f1ea67d0d572a5f1b1369c38666d8584058`)**
- R3 implementation parent: `760b7e5cf8cecab25901db3c109a5d94a6901206`
- R3 publication: (this record, child of R3 implementation)

Append-forward only. No amend / rebase / squash / reset / force-push.
`8e0a11f4`, `d6660873`, `4b6d6843`, `760b7e5c` preserved as historical evidence.

## Independent verdict (R3 trigger)

- `ROADMAP20_R2_INDEPENDENT_FINAL_REVIEW = FAIL_CORRECTABLE`
- `ARCHITECTURE_PREMISE_FAILURE = NO`
- `ARCHITECTURE_ESCALATION_REQUIRED = NO`
- `MATERIAL_BLOCKERS = 2`
- `MAJOR_FINDINGS = 1`
- Accepted (not reopened): B2 complete timed-text WHAT, B2 graph connectivity,
  F3 platform capability authority, F5 temporal mapping fail-closed,
  B3 object-identity-free, B3 reconstructed-value determinism.

## R3-B1 — COMPLETE AUTHORED SEMANTIC INTEGRITY BOUNDARY

Finding: R2 verified the Timeline revision (clips/audio/text) but
`RenderPlanningInput` still accepted caller-supplied
`List<EffectInstance>` / `List<EffectDefinition>` — the planner could consume
verified revision R1 + effect authored state from another revision/context.

Effect authority reality review (E1-E10):
- Effects are authored Timeline semantics: the wire timeline JSON carries
  `clip.effects[]`, parsed into canonical `TimelineClipEffect` (canonicalmodel
  package) with `EffectCanonicalSemantics` as the ONE local effect semantic
  codec authority (deep-sorted, typed, collision-resistant encoding).
- `EffectInstance` has stable `effectInstanceId`; `EffectDefinition` has stable
  `definitionId` + `version`.
- The canonical `TimelineDocument` projection does NOT carry effects (E9 =
  incomplete persistence projection, not deliberate architecture); effects are
  therefore modeled as an independently-versioned semantic asset with an
  explicit immutable pin rather than forced into TimelineDocument.
- E10: R3 establishes immutable binding WITHOUT changing domain authority.

Resolution:
- **`VerifiedEffectSemanticSnapshot`** — immutable, private constructor; only
  public path is the factory.
- **`VerifiedEffectSemanticSnapshotFactory.verified(effects, effectDefinitions)`**
  — REAL integrity checks (not caller-trust): every
  `effectDefinitionId` must resolve (else fail closed); every
  `effectDefinitionVersion` must match the definition's version (else fail
  closed); a value-bound content pin (SHA-256 over explicit deterministic
  encoding of the complete typed effect state — instances sorted by id,
  parameters deep-sorted, all authored semantic fields length-prefixed,
  count-framed sections) binds the state. Semantic-equal state → same pin;
  distinct state → distinct pin.
- **`VerifiedRenderSemanticSnapshot`** — ONE immutable integrity-bound authored
  semantic snapshot aggregating the verified Timeline revision projection and
  the verified authored effect snapshot.
- **`RenderPlanningInput`** now consumes `VerifiedRenderSemanticSnapshot` +
  transient inputs (RenderRequest, SourceResolutionInput, CapabilityContext)
  only. There is NO parameter accepting a bare `List<EffectInstance>` — the
  record has exactly four components (guard-asserted).

Answers: R3-B1 Q1 verified authored snapshot hydrates = YES; Q2 mismatched
effect state fails closed = YES; Q3 arbitrary effect list injection = 0 paths;
Q4 definition/version mismatch fails closed = YES; Q5 pure planner repository
lookups = 0; Q6 effect WHAT recoverable = YES; Q7 fingerprint deterministic =
YES; Q8 provenance accurately represents all authored semantics = YES
(provenance includes verified timeline reference + effect content pin).

## R3-B2 — STRUCTURALLY UNAMBIGUOUS CANONICAL ENCODING

Finding: several adjacent variable-length sections were emitted as scalar
streams without explicit list/section boundaries/counts — theoretically
permitting distinct semantic values to share canonical bytes (e.g.
`defaultChain=[A,B,C], scriptOverrides=[]` vs
`defaultChain=[A], scriptOverrides=[{script=B, chain=[C]}]`).

Resolution — `RenderPlanCanonicalCodec`:
- New `counted(...)` (count-prefixed element list) and `countedSorted(...)`
  helpers applied to EVERY variable-length semantic section:
  - StyledText semanticRuns / styleRuns (adjacent sections — framed)
  - TextStyle OpenType feature settings
  - FontSelectionIntent familyPreferences / explicitAxisOverrides
  - FontFallbackPolicy defaultChain / scriptOverrides / languageOverrides /
    emojiChain (four adjacent sections — framed, the collision class from the
    finding)
  - resolved font run variation coordinates
  - effect typed parameters (sorted by key)
- Existing `list(...)` framing (`[...]` structural open/close + length-prefixed
  elements) retained for node/edge/requirements lists; all scalar values remain
  length-prefixed so delimiter characters inside values cannot collide.

Canonical invariant now satisfies BOTH directions: semantic-equal → equal
canonical bytes; semantic-distinct → distinct canonical bytes (adversarial
collision tests added: fallback boundary, semantic/style run boundary, family
preference boundary, parameter cardinality).

## R3-M1 — SEALED VARIANT CANONICALIZATION FAIL-CLOSED

Finding: `ColorPrimaries` unknown future variant collapsed to `"UNKNOWN_VARIANT"`
— multiple future variants could share one canonical value.

Resolution:
- `colorPrimariesCanonical` throws `IllegalArgumentException` on any unknown
  variant (fail closed).
- `materializationRequirementCanonical` added an explicit `else` throw branch
  for any future `RenderMaterializationRequirement` variant.
- Guard (R3-G2) forbids the `UNKNOWN_VARIANT` token in the codec.
- Audit of all canonical paths: no default switch fallback, no generic unknown
  strings, no silent null synthesis; the only sealed variants are exhaustive
  (ColorDescription, ColorPrimaries, RenderMaterializationRequirement,
  RenderArtifactReference, RenderDependency).

## Format version review

`RENDERPLAN_FORMAT_VERSION_COMPATIBILITY_REVIEW = KEEP_V1`
- RenderPlan fingerprints are NOT persisted externally; no consumer outside the
  renderplan package reads canonical bytes; `renderplan-format-v1` has never
  been released as a compatibility contract.
- R1/R2/R3 are completion corrections of the same unreleased v1 encoding
  (governance precedent: R1 and R2 also changed canonical bytes under v1 and
  passed independent review).
- `rendergraph-format-v1` unchanged (graph canonical encoding semantics
  unchanged by R3).

## Guard strengthening

`verifyC20RenderPlanBoundaryGuard` extended with R3-G1 (RenderPlanningInput
consumes VerifiedRenderSemanticSnapshot, exposes no List<EffectInstance>;
VerifiedRenderSemanticSnapshot + VerifiedEffectSemanticSnapshotFactory present),
R3-G2 (no UNKNOWN_VARIANT token), R3-G3 (counted framing helper present).
R2 guards retained.

## Test / gate results (final frozen SHA 846121af)

- R3 targeted (R3AcceptanceTest 13): B1 snapshot integrity / effect mismatch /
  version mismatch / no arbitrary injection / pin determinism / recoverability /
  fingerprint; B2 adversarial collisions (fallback, run sections, family
  preferences, parameter cardinality); M1 fail-closed — **13 / 0 / 0**
- renderplan package: **80 / 0 / 0**
- render-module: **2837 / 0 / 0 / skip 19**
- font-text-module: **11 / 0 / 0**; timeline-module: **771 / 0 / 0**;
  audio-module: **22 / 0 / 0**; extension-module: **314 / 0 / 0**;
  platform-app: **562 / 0 / 0 / skip 20**
- **FULL SUITE (recursive, --rerun-tasks, 176 tasks): 7528 / 0 / 0 / skip 43**
- bootJar: PASS; pfirr1RemediationCheck: PASS (+ all pfirr verify tasks);
  verifyC20RenderPlanBoundaryGuard: PASS (R2 + R3 guards, 54 files);
  Modulith: PASS; git diff --check: PASS
- Architecture drift: 222 PASS; CIP2G6 + CIP2DG12 =
  **PRE_EXISTING_BASELINE_FAIL** (identical on baseline `9538e73e`;
  RenderOutputRequirement→colorimage C14/C8 contract path; no new violation;
  correction regression = NO)

## Scope audit

- Production: VerifiedEffectSemanticSnapshot (+), VerifiedEffectSemanticSnapshotFactory (+),
  VerifiedRenderSemanticSnapshot (+), VerifiedRenderSemanticSnapshotFactory (+),
  RenderPlanningInput (authored snapshot boundary), RenderPlanCanonicalCodec
  (count framing + fail-closed sealed variants), DefaultRenderMaterializer
  (snapshot API adaptation), build.gradle.kts (R3 guards).
- Tests: R3AcceptanceTest (+), StyledTextHelper (+), TestPlans +
  LogicalWhatClosureAcceptanceTest + RenderPlanDeterminismTest + ExactTimeTest +
  RenderGraphValidationNegativeTest (adaptation).
- Scope drift: NONE. No physical planner, no provider/worker/device semantics,
  no capability authority redefinition, no new persistence, no Roadmap #21/22
  work, no rewriting of prior evidence.

## #22 forward-compatibility proof

Given only the Logical RenderPlan (+ future PlanningContext), a future physical
planner can resolve effect category + typed parameters, audio gain/mute/balance,
complete TimedText raster WHAT, source artifact/content pin, output
requirements, platform CapabilityRequirements — WITHOUT rereading
MediaClip/EffectInstance/EffectDefinition/AudioRoute/AudioMix/TextElement, and
WITHOUT adding physical/runtime fields. ALL authored WHAT is bound inside the
verified authored semantic snapshot; provenance carries the verified timeline
reference + effect content pin.

PHYSICAL_PLANNER_CAN_CONSUME_LOGICAL_WHAT_WITHOUT_AUTHORED_REREAD = YES

## Blockers / escalation

- MATERIAL_BLOCKERS = 0
- ARCHITECTURE_ESCALATION = NONE
- NEW_REGRESSIONS = 0
- NEW_ARCHITECTURE_VIOLATIONS = 0

## Final recommendation

**READY_FOR_CHATGPT_INDEPENDENT_REVIEW**

Roadmap #20 is NOT closed. Merge to main NOT authorized. Roadmap #21/#22 NOT
started. Closure remains ChatGPT's decision.
