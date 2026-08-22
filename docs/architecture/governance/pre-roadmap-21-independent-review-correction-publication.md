# PRE-ROADMAP-21 INDEPENDENT-REVIEW EXACTNESS CORRECTION — PUBLICATION

## Trigger

PRE_ROADMAP_21_INDEPENDENT_REVIEW=CORRECTION_REQUIRED (three closure blockers):
1. C2 Frozen Planning Context exactness (incomplete planning-input contract)
2. C10/C11 typed RenderExtent exactness (String dual authority + unchecked real path)
3. RED-4/5/6/7 real mutation evidence (prior claims were "covered by tests", not executed)

## PREVIOUS_PUBLICATION_CORRECTION

### Prior overstatement 1
The initial publication recorded RED-4/5 as PASS_DETECTED "covered by factory
semantics" and RED-6 as "structural evidence only". These were NOT real
mutations. Corrected in this run by executed mutations (see RED evidence).

### Prior overstatement 2
The initial publication called ModulithDocumentationGenerationTest "active
verification". Corrected: that test executes Spring Modulith Documenter
documentation GENERATION (not ApplicationModules.verify()). Actual state:
MODULITH_DOCUMENTATION_GENERATION=ACTIVE,
MODULITH_ARCHITECTURE_VERIFICATION=NONE (no live violation gate);
PRE-#21 boundary invariants are enforced by Pre21 structural guards.

### Prior implementation gap 3
String-based requestedExtent/achievedExtent was never accepted as final
C10/C11 closure. Corrected: String extent authority retired; typed
RenderExtent is the single authority.

## Initial candidate status

INITIAL_CANDIDATE_SHA=0897d5eabcbe8ce7b9a34a69264b717e9d1a5031
= SUPERSEDED as final candidate by this independent-review correction.
Remains first-pass evidence (not rewritten, not deleted).

## Correction implementation

| Field | Value |
|---|---|
| CORRECTION_IMPLEMENTATION_SHA | ae4c6f6fff13ecd55864145df8be441f32105750 |
| CORRECTION_CANDIDATE_SHA | ae4c6f6fff13ecd55864145df8be441f32105750 |
| CORRECTION_CANDIDATE_TREE | (see git) |
| CORRECTION_PUBLICATION_SHA | (set after commit) |

## CORR-A — C2 FrozenPlanningContext exactness

FROZEN_CONTEXT_FIELD_INVENTORY:
- semantic_request: targetProductId + targetProductType (explicit request identity)
- declared_capability_requirements: List<CapabilityRequirement> (W2 authority — consumer declares, never invented)
- resolved_capability_facts: Map<String, CapabilityResolutionFact> (pre-resolved by resolution layer)
- requested_render_extent: RenderExtent (typed, nullable — render-extent operations only)
- dependency_facts: Map<String, DependencyFact> (frozen readiness facts)
- ownership_context: tenantId + projectId
- deterministic_policy_config: NOT_CURRENTLY_CONSUMED_BY_LOGICAL_PLANNER (no existing typed repository concept consumed by the current planner; context design does not contradict C2)
- immutable_source_refs: NOT_CURRENTLY_CONSUMED_BY_LOGICAL_PLANNER (no logical planner consumer; no speculative over-modeling per review instruction)

Immutability: all collection inputs defensively copied (List.copyOf / Map.copyOf;
null → empty immutable). Tests prove caller mutation cannot alter frozen state.

## CORR-B — C10/C11 typed RenderExtent closure

REQUESTED_RENDER_EXTENT_SOURCE=RenderJob.requestedExtent (typed RenderExtent added to the real job surface; 40 construction sites updated)
REQUESTED_RENDER_EXTENT_TYPE=render.domain.renderplan.RenderExtent (single extent authority)
ORCHESTRATION_ENTRY=DefaultRenderOrchestrator.execute(RenderJob) → RenderResult.success(..., job.requestedExtent(), achieved, ...)
PROVIDER/EXECUTION_EVIDENCE_SOURCE=NONE CURRENTLY — no provider path reports execution-evidenced achieved extent
ACHIEVED_RENDER_EXTENT_SOURCE=NONE (honest: no fake achieved; never copies requested)
ACHIEVED_RENDER_EXTENT_TYPE=render.domain.renderplan.RenderExtent (nullable)
VALIDATION_METHOD=typed semantic equality (RenderExtent.equals: start, end, frameRate)
AUTHORITATIVE_SUCCESS_CONDITION=success && requested != null && achieved != null && requested.equals(achieved)
NO_PROOF_BEHAVIOR=ordinary success (operationally) with authoritativeSuccess()=false; declared-extent jobs without achieved evidence fail closed (typed failure via success() factory)

STRING_REQUESTED_EXTENT_FIELD_COUNT=0
STRING_ACHIEVED_EXTENT_FIELD_COUNT=0
STRING_EXTENT_COMPARISON_AUTHORITY_COUNT=0
DUAL_RENDER_EXTENT_AUTHORITY_COUNT=0
AUTHORITATIVE_FRAME_OUTPUT_SILENT_PARTIAL_COUNT=0
AUTHORITATIVE_FRAME_OUTPUT_UNCHECKED_RENDER_EXTENT_COUNT=0
AUTHORITATIVE_SUCCESS_WITHOUT_EXTENT_PROOF_COUNT=0

## RED mutation evidence (executed)

| ID | Mutation | Detector | FAILED count | Restored |
|---|---|---|---|---|
| RED-4 | extentProven=true (bypass validation) | RenderResultExtentFailClosedTest | 7 | YES |
| RED-5 | authoritativeSuccess weakened (no achieved proof) | RenderResultExtentFailClosedTest (direct-constructor case) | 3 | YES |
| RED-6 | provider import into IrErrorCode | Pre21ErrorAlgebraGuardTest (new detector) | 3 | YES |
| RED-7 | resolve(String) resurrected | Pre21CapabilityAuthorityGuardTest | 3 | YES |

All mutations restored; candidate contains no mutation code.

## Modulith final status

MODULITH_DOCUMENTATION_GENERATION=ACTIVE (ModulithDocumentationGenerationTest)
MODULITH_ARCHITECTURE_VERIFICATION=NONE (no ApplicationModules.verify() gate)
STATIC_SNAPSHOT_AUTHORITY=RETIRED (91 files removed; generated output gitignored)
PRE21_ACTIVE_BOUNDARY_GUARDS=Pre21PlannerPurityGuardTest, Pre21CapabilityAuthorityGuardTest, Pre21ModuleBoundaryGuardTest, Pre21ErrorAlgebraGuardTest, RenderResultExtentFailClosedTest

## FCV / gates

(completed in the run; see final report)
BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
ROADMAP_21=NOT_STARTED
CANONICAL_MAIN_CHANGED=NO
READY_FOR_INDEPENDENT_REREVIEW=YES
