# ROADMAP #21 — EXECUTION GRAPH PLANNING — BOUNDED CORRECTION 4 PUBLICATION

STATUS=PENDING_CHATGPT_FINAL_REVIEW (publication cannot self-declare closure)

CORRECTION_3_IMPLEMENTATION_SHA=ebdc6262c97742a79cfbaeb28d9f4ffc9025814f
CORRECTION_3_PUBLICATION_SHA=cf8b36f456f15473e4729dbea91b9dbb2d461ac7
PREVIOUS_CHATGPT_REVIEW=FAIL (ROADMAP_21_CORRECTION_3_FINAL_REVIEW=CORRECTION_REQUIRED)
PREVIOUS_ARCHITECTURE_ESCALATION=TIMED_TEXT_TO_RENDER_EXECUTION_COVERAGE_TEMPORAL_BRIDGE_ONLY
PREVIOUS_BLOCKERS=B1 TIMED_TEXT extent coverage / B2 REUSE exact semantics / B3 canonical digest completeness
CORRECTION_4_BASE_SHA=cf8b36f456f15473e4729dbea91b9dbb2d461ac7

## PHASE A — TIMED_TEXT TEMPORAL BRIDGE (FROZEN T2)

TIMED_TEXT_ARCH_CORRECTION_SHA=099b756c33be52420fc13c735e8f4354f58cfdbe
SELECTED_OPTION=T2 (checked exact #20-owned projection with bounded-representability guard)

FONT_RATIONAL_UNIT_SEMANTICS=exact rational (BigInteger num/den), authored
timeline-time quantities (fixture: TextElement.whole(0)/whole(5) on [0,10) clip)
MEDIA_TIME_UNIT_SEMANTICS=exact rational seconds (long ticks/timeScale)
LOSSLESS_CONVERSION_EXISTS=YES (same exact-rational seconds semantic)
LOSSLESS_CONVERSION_RANGE=BOUNDED (BigInteger -> long guard required)
OVERFLOW_RULE=FAIL_CLOSED (PLANNING_UNSUPPORTED; no rounding/clamp)
ROUNDING_ALLOWED=NO
CONVERSION_AUTHORITY=#20 renderplan (ExactTextTimelineTimeProjection +
DefaultRenderMaterializer); #21 never sees FontRational
TEXT/TIMELINE/RENDER_EXECUTION_COVERAGE/#21 AUTHORITY=UNCHANGED
ARCHITECTURE_ESCALATION_PHASE_A=RESOLVED_FOR_TIMED_TEXT_BRIDGE

## PHASE B1 — TIMED_TEXT COVERAGE (CLOSED)

DefaultRenderMaterializer assigns TIMED_TEXT coverage
[exact(start), exact(start+duration)]; out-of-extent TIMED_TEXT now
mechanically prunable; composite graph closure preserved (TT10-TT13).
TT01-TT14 tests PASS (inside/outside/partial/boundary half-open/fractional
exact 1/3s/overflow fail-closed/end=start+duration).

## PHASE B2 — EXACT REUSE_AS_CANONICAL (CLOSED, vs 99aa4162)

ExecutionPlanSchemaVersion: frozen surface restored — record(int value)
implements Serializable, V1 constant, of(int), toString=value (value>=1).
ExecutionCreationContext: frozen surface restored — Serializable, 7 fields
(parentPlanId=String — NOT ExecutionPlanId), createdAt REQUIRED (requireNonNull),
minimal(Instant)/forUser factories, Optional accessors.
All 5 ID types (ExecutionPlanId/EdgeId/InputId/OutputId/StepId): Serializable +
toString=value + blank guards restored. Typed usages retained (edgeId/inputId/
outputId/stepId/List<ExecutionOutputId> in ExecutionProvider).

REUSE_AS_CANONICAL_COUNT_EXPECTED=7 ACTUAL=7

## PHASE B3 — CANONICAL CLOSURE (CLOSED)

- Length-prefixed scalar framing (len:value) — injective over arbitrary
  supported strings (delimiter-collision test PASS; no delimiter-only grammar)
- materialization canonicalization DELEGATED to the #20 authoritative
  RenderPlanCanonicalCodec (narrow public exposure added; single canonical
  owner; FAIL CLOSED unknown variants) — no #21 reimplementation, no record
  toString semantics for Effect/AudioProcess/TimedText/EffectTarget
- RenderNodeKind / RenderComponentPath explicit sealed encodings
- physical digest: actual formatVersion + schemaVersion.value + planFingerprint
  + plan-level extent + PER-UNIT propagatedExtent + all unit fields
- ExecutionPlanId / provenance / createdAt excluded (X01-X03 + R-C4-15/16)

OBJECT_TOSTRING_CANONICAL_SEMANTIC_USAGE_COUNT=0
CANONICAL_LENGTH_PREFIX_OR_EQUIVALENT=YES
CANONICAL_DELIMITER_COLLISION_TEST=PASS

## EVIDENCE (mechanical)

CONTRACT_BEHAVIOR_TESTS=26
GRAPH_CLOSURE_TESTS=11
DIGEST_TESTS=11
IO_CANONICAL_TESTS=17
CORRECTION_4_TESTS=14 (TT bridge + framing + REUSE + unit extent)
GUARD_TESTS=26
TOTAL_TARGETED_TESTS=105
TARGETED_FAILURES=0
MATERIALIZER_INTEGRATION_TESTS=3 (render-module, real DefaultRenderMaterializer)

RED_MUTATIONS_TOTAL=14 (R-C4-01..16: text null, double bridge, window-vs-extent,
APE, V1 removed, schema major/minor, parentPlanId type, createdAt nullable,
mat toString, framing removed, unit extent omitted, planId in digest,
provenance in digest)
RED_MUTATIONS_FAIL_DETECTED=14
RED_RESTORED_GREEN=YES

FULL_SUITE_TESTS=7694 FAILURES=0 ERRORS=0 SKIPPED=43 MODULES=40
GATES: verifyGcr2ArtifactAuthority PASS, pfirr1RemediationCheck PASS,
verifyC1Cnm1RedGates PASS, jooqFoundationCheck PASS,
verifyTimelineEffectTransitionCanonicalization PASS,
verifyC20RenderPlanBoundaryGuard PASS, bootJar PASS
MODULITH_GATE=N/A (no ApplicationModules.verify in repository — verified)

## SHAs

TIMED_TEXT_ARCH_CORRECTION_SHA=099b756c33be52420fc13c735e8f4354f58cfdbe
TIMED_TEXT_ARCH_CORRECTION_TREE=(see git)
CORRECTION_4_IMPLEMENTATION_SHA=bd311993ce8d9b41b254f4d9d73ac366635d7c20
CORRECTION_4_IMPLEMENTATION_TREE=c9290c4123834c28e93c89c84c2e50615190b943
FINAL_CANDIDATE_SHA=bd311993ce8d9b41b254f4d9d73ac366635d7c20
FINAL_CANDIDATE_TREE=c9290c4123834c28e93c89c84c2e50615190b943
FCV_BUILD_INPUT_SHA=bd311993ce8d9b41b254f4d9d73ac366635d7c20
FCV_BUILD_INPUT_TREE=c9290c4123834c28e93c89c84c2e50615190b943
PUBLICATION_PARENT_SHA=bd311993ce8d9b41b254f4d9d73ac366635d7c20
PUBLICATION_SHA=(docs-only, appended after FCV)

## GOVERNANCE

BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE (Phase A T2 resolved within envelope)
ROADMAP_21_CORRECTION_4_CANDIDATE=READY_FOR_CHATGPT_FINAL_REVIEW
ROADMAP_21_CANONICAL_INTEGRATION=NO_GO
ROADMAP_21_CLOSED=NO
ROADMAP_22=NOT_STARTED
NEXT_ACTION=CHATGPT_ROADMAP_21_CORRECTION_4_FINAL_REVIEW
