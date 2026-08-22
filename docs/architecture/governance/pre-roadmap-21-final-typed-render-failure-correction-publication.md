# PRE-ROADMAP-21 FINAL TYPED RENDER FAILURE CORRECTION — PUBLICATION

## Trigger

PRE_ROADMAP_21_INDEPENDENT_REREVIEW closed all prior blockers except:
TYPED_RENDER_FAILURE_ALGEBRA_EXACTNESS — failure identity was behaviorally
fail-closed but semantically free-text (boolean success + String hitReason).

## PRIOR_CLAIM_CORRECTION

The previous correction publication stated "declared-extent jobs without
achieved evidence fail closed (typed failure via success() factory)". That was
behaviorally fail-closed, but the failure semantic identity remained free-text.
This final correction adds actual typed semantic failure authority.

## Failure algebra inventory (existing, pre-correction)

| Type | Module/package | Authority | Suitable for runtime render failure |
|---|---|---|---|
| RenderExecutionPlanFailureReason | render/domain/compile/executionplan | plan rejection (UNBOUND_CAPABILITY_NODE, ...) | NO — plan-execution policy, not result failure |
| LocalExecutionPlanFailureReason | render/app/timeline/compile | local plan run failure | NO — timeline compile flow, not orchestrator result |
| ProviderBindingFailureReason | render/domain/compile/binding | binding failure | NO |
| IrErrorCode | render/ir | IR validation | NO |
| FailureClassificationEngine/FailureType | render/infrastructure/providerruntime/fallback | provider fallback classification | NO — provider runtime, not result identity |

No existing type semantically owned orchestrator-level execution result
failure with extent categories. New module-local type introduced:
RenderResultFailureReason (render-module/infrastructure, the RenderResult layer).

## Production correction

- New enum RenderResultFailureReason: RENDER_EXTENT_UNPROVEN,
  RENDER_EXTENT_NOT_ACHIEVED, FONT_PREFLIGHT_FAILED, STEP_FAILED,
  ORCHESTRATION_ERROR — typed semantic failure identity (module-local, not a
  global mega enum; no provider-native imports).
- RenderResult gains `RenderResultFailureReason failureReason`; `hitReason`
  remains human-readable explanation ONLY (never semantic branching).
- failed() factories now typed: failed(jobId, reason, detail).
  LEGACY_STRING_FAILURE_FACTORY_CALL_COUNT=0 (3 production + 1 test callers
  migrated), DEFINITION_COUNT=0, FAILURE_COMPATIBILITY_WRAPPER_COUNT=0,
  DUAL_FAILURE_AUTHORITY_COUNT=0 (CLEAN FORWARD).
- Compact constructor invariants: success=true → failureReason absent;
  success=false → failureReason present (IllegalArgumentException otherwise).
- Extent fail-closed: RENDER_EXTENT_UNPROVEN (achieved missing) /
  RENDER_EXTENT_NOT_ACHIEVED (semantic mismatch), retaining typed extent
  context on the failed result.

## Extent semantics (unchanged, preserved)

- requested missing → ordinary non-authoritative success allowed
- requested present + achieved missing → typed RENDER_EXTENT_UNPROVEN, not authoritative
- requested present + achieved mismatch → typed RENDER_EXTENT_NOT_ACHIEVED, not authoritative
- requested == achieved → authoritative success
- achieved extent is NEVER fabricated (PROVIDER_EXECUTION_EVIDENCE_SOURCE=NONE)

## RED mutation evidence (executed this run)

| ID | Mutation | Detector | FAILED | Restored |
|---|---|---|---|---|
| RED-TYPED-FAILURE | extent failure reason → null (free-text only) | RenderResultExtentFailClosedTest + Pre21ErrorAlgebraGuardTest | 5 | YES |
| RED-4 (rerun) | extent validation bypass | RenderResultExtentFailClosedTest | 8 | YES |
| RED-5 (rerun) | authoritative without achieved proof | RenderResultExtentFailClosedTest | 3 | YES |
| RED-6 | prior PASS_DETECTED — guarded surface unchanged (provider-leak detector) | Pre21ErrorAlgebraGuardTest | — | prior |
| RED-7 | prior PASS_DETECTED — guarded surface unchanged | Pre21CapabilityAuthorityGuardTest | — | prior |

## Tests / guards

RenderResultExtentFailClosedTest 11/11 (typed reason per case, semantic reason
without parsing detail, direct-constructor bypass, real orchestrator path,
ctor invariants). Pre21ErrorAlgebraGuardTest 7/7 (+extent-failure-typed guard,
+no-legacy-free-text-factory guard, +no-provider-import-in-typed-reason guard).
render-module full suite PASS.

## Candidate / FCV

FINAL_CORRECTION_IMPLEMENTATION_SHA=117f3963576a875dc91b7ffd4b1d3fcf8e4d19f8
FINAL_CORRECTION_CANDIDATE_SHA=117f3963576a875dc91b7ffd4b1d3fcf8e4d19f8
PUBLICATION_SHA=2a6cbc16ad3d40e3d2ebf4c9d12c6d31488ff697
FULL_SUITE=7744 tests / 0 failures / 0 errors / 43 skipped / 40 modules
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
CANONICAL_MAIN_CHANGED=NO
ROADMAP_21_STARTED=NO
READY_FOR_PRE_ROADMAP_21_FINAL_INDEPENDENT_REVIEW=YES
