# Roadmap #20 Logical WHAT Closure Correction R2

Append-forward governance record — second bounded correction
(ROADMAP20_LOGICAL_WHAT_CLOSURE_CORRECTION_R2) closing the three material
blockers raised by ChatGPT independent review of R1.

## Chain

- Original implementation (R0): `887f0c06` (candidate)
- Original publication (R0): `9538e73e`
- Correction implementation R1: `8e0a11f4` (tree `f87fd0ec`)
- Correction publication R1: `d6660873` (tree `381aa2c8`)
- **Correction implementation R2: `4b6d6843ffdbe6298df16adfcb1462dec8e7cc70`
  (tree `0e6b48024d377b8ae6c94ac75f1922ac9f5224f9`)**
- R2 implementation parent: `d6660873417682b56ee0214aafa8b3b53452d71e`
- R2 publication: (this record, child of R2 implementation)

Append-forward only. No amend / rebase / squash / reset / force-push.
`8e0a11f4` and `d6660873` preserved as historical evidence.

## Independent verdict (R2 trigger)

- `ROADMAP20_LOGICAL_WHAT_CLOSURE_CORRECTION_INDEPENDENT_REVIEW = FAIL_CORRECTABLE`
- `ARCHITECTURE_PREMISE_FAILURE = NO`
- `ARCHITECTURE_ESCALATION_REQUIRED = NO`
- `MATERIAL_BLOCKERS = 3`

## B1 — TRUE REVISION INTEGRITY / VERIFIED HYDRATION BOUNDARY

Problem: `HydratedTimelineRevision` was a public record permitting arbitrary
assembly (revision R1 identity + clips/effects/audio/text from other
revisions); the previous correction moved the mixing inward without
eliminating it.

Resolution:
- **`VerifiedTimelineRevision`** — immutable final class, **private
  constructor**; the ONLY public construction path is
  `VerifiedTimelineRevisionFactory.verified(TimelineRevision, TimelineContentDigester)`.
- **`VerifiedTimelineRevisionFactory`** — authoritative hydration boundary:
  1. requires a caller-supplied authoritative `TimelineRevision` (the factory
     itself performs zero repository lookup),
  2. computes the canonical content digest of `canonicalTimeline` via the
     timeline-module `TimelineContentDigester`,
  3. **fails closed** on digest mismatch (identity/content mismatch can never
     reach normal planning as valid),
  4. extracts the typed semantic projection (clips, audio mix, text elements)
     from the SAME verified document,
  5. constructs the immutable verified projection.
- `HydratedTimelineRevision` **deleted**.
- `RenderPlanningInput` consumes `VerifiedTimelineRevision`; effects and
  effect definitions are separate explicit planning inputs because the
  authoritative `TimelineDocument` does not carry effects (repository reality).
- Trust boundary documented: cryptographic revalidation happens at the
  application/hydration boundary; the pure render planner never queries
  repositories, never loads mutable "latest", never performs hydration.

Answers: Q1 arbitrary verified construction = NO; Q2 integrity verified in
`VerifiedTimelineRevisionFactory`; Q3 planner repository queries = NO;
Q4 digest mismatch reaching planning = NO.

## B2 — COMPLETE TIMED-TEXT LOGICAL WHAT

Problem: `TimedTextMaterializationRequirement` projected only
`styledText().content()`, losing semantic runs, style runs and paragraph
style — two TextElements with equal text but different styling collapsed.

Resolution: `TimedTextMaterializationRequirement` now carries the **complete
authoritative `StyledText`** (content + semantic runs + style runs + paragraph
style) plus TextFrame, FontFallbackPolicy, ResolvedFontRuns, timing, identity.
Font resolution is consumed (Roadmap #19 authority), never recomputed; no
provider-specific raster command; Render consumes FontText/Timeline authority
and redefines no typography.

Answers: Q5/Q6/Q7 style/paragraph/semantic collapse = NO (each change alters
fingerprint); Q8 future physical planner recovers full text WHAT without
rereading TextElement = YES.

## B3 — VALUE-DETERMINISTIC CANONICAL FINGERPRINTING

Problem: codec used `frame().toString()` / `run.toString()` for types without
a canonical textual contract (Object identity could enter fingerprint bytes).

Resolution: `RenderPlanCanonicalCodec` now explicitly encodes:
- `styledTextCanonical` — content, semantic runs (range/language/script/
  direction), style runs (range + full TextStyle: font selection, size,
  tracking, OpenType features), paragraph style (alignment, justification,
  line-height form/value, wrap policy, base direction, line-break policy);
- `textFrameCanonical` — width/height constraints, horizontal/vertical
  alignment, wrap behavior, overflow behavior;
- `resolvedFontRunCanonical` — TextRange + complete ResolvedFontInstance
  (source/validated content digests, security state, format, face index,
  variation coordinates);
- `fontFallbackPolicyCanonical` — ordered default/script/language/emoji chains;
- `colorDescriptionCanonical` + `rasterSampleCanonical` — **repairs a latent
  identity leak** (`Object::toString` on sealed ColorDescription and
  RasterSampleDescription);
- `fontRationalCanonical`, `textRangeCanonical`, `colorPrimariesCanonical`,
  `chromaticityCanonical` — explicit field encoding.

Remaining `.toString()` uses are limited to types with a **documented
canonical textual contract**: MediaTime, FrameRate, ContentDigest,
FontContentDigest, FaceIndex, VariationAxisTag, OpenTypeFeatureTag,
FontFamilyName, LanguageTag, ScriptTag, TextRange, FontRational,
AudioGain/AudioMute/StereoBalance.

Answers: Q9 Object.toString in fingerprint path = NO (explicit codec functions;
documented-canonical types enumerated above); Q10 identity-dependent
fingerprint divergence = NO (reconstructed-equal inputs produce identical
fingerprints); Q11 ordering deterministic = YES (sorted collections,
explicit field order).

## Guard strengthening

`verifyC20RenderPlanBoundaryGuard` extended with B1/B2/B3 structure
assertions: VerifiedTimelineRevision + factory present, HydratedTimelineRevision
absent, RenderPlanningInput consumes verified projection,
TimedTextMaterializationRequirement carries StyledText, codec contains explicit
styledText/textFrame/resolvedFontRun canonical encoders.

## Test / gate results (final frozen SHA 4b6d6843)

- Correction targeted tests (LogicalWhatClosureAcceptanceTest 30,
  TemporalMappingFailClosed 2, FirstBoundedPlanningE2ETest 3,
  RenderPlanDeterminismTest 8, ExactTimeTest 4, CapabilityBoundaryTest 5):
  **52 tests / 0 failures / 0 errors**
- render-module: **2824 / 0 / 0 / skip 19**
- font-text-module: **11 / 0 / 0**
- timeline-module: **771 / 0 / 0**
- audio-module: **22 / 0 / 0**
- extension-module: **314 / 0 / 0**
- platform-app: **562 / 0 / 0 / skip 20**
- **FULL SUITE (recursive, --rerun-tasks, 176 tasks executed): 7515 / 0 / 0 / skip 43**
- bootJar: PASS
- pfirr1RemediationCheck: PASS
- verifyC20RenderPlanBoundaryGuard: PASS (R2 B1/B2/B3 strengthened, 50 files)
- Modulith (ModularityTest + ModulithDocumentationGenerationTest): PASS
- Architecture drift: 222 PASS; CIP2G6 + CIP2DG12 =
  **PRE_EXISTING_BASELINE_FAIL** (identical on baseline `9538e73e`;
  RenderOutputRequirement→colorimage C14/C8 contract path; no new violation;
  correction regression = NO)
- git diff --check: PASS

## FCV first-attempt note

Initial `test --rerun-tasks` attempt exited early (19s, 3 tasks) without
assertion failures — classified **INFRASTRUCTURE_FLAKE** (Gradle daemon/worker
transient). A clean full re-run on the SAME frozen SHA completed
**BUILD SUCCESSFUL in 19m 24s, 176 tasks executed, EXIT=0**. Both attempts
recorded; no source change between attempts.

## Scope audit

- Production: VerifiedTimelineRevision (+), VerifiedTimelineRevisionFactory (+),
  RenderPlanningInput (verified projection), TimedTextMaterializationRequirement
  (full StyledText), RenderPlanCanonicalCodec (explicit value encoding),
  DefaultRenderMaterializer (verified API adaptation), HydratedTimelineRevision
  (−), build.gradle.kts (guard strengthening).
- Tests: TestPlans (verified fixtures), LogicalWhatClosureAcceptanceTest,
  RenderPlanDeterminismTest, ExactTimeTest, RenderGraphValidationNegativeTest
  (adaptation).
- Scope drift: NONE. No physical planner, no provider/worker/device semantics,
  no capability authority redefinition, no new persistence, no Roadmap #21/22
  work, no rewriting of prior evidence.

## #22 forward-compatibility proof

Given only the Logical RenderPlan (+ future PlanningContext), a future physical
planner can resolve: effect category + typed parameters, audio gain/mute/
balance, complete TimedText raster WHAT (content, semantic/style runs,
paragraph style, frame, fallback policy, resolved fonts), source artifact/
content pin, output requirements, platform CapabilityRequirements — WITHOUT
rereading MediaClip/EffectInstance/EffectDefinition/AudioRoute/AudioMix/
TextElement, and WITHOUT adding physical/runtime fields into the logical plan.

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
