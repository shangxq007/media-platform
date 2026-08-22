# ROADMAP #21 — C12/C13 TEMPORAL-EXTENT ARCHITECTURE CORRECTION V1

STATUS=FROZEN_ARCHITECTURE_CORRECTION

TRIGGER=CHATGPT_ROADMAP_21_CORRECTION_FINAL_REVIEW
SCOPE=C12_C13_TEMPORAL_COORDINATE_BOUNDARY_ONLY
BASE_SHA=59120486430c268f70cbb56b81d57e42bfeeb507

---

## 1. PROBLEM (B1)

The first bounded correction compared RenderSampleWindow intervals directly
against RenderExtent intervals. This is an architecture bug: the two live in
DIFFERENT coordinate domains.

- RenderExtent = RenderRequest / timeline-domain execution extent
  (coordinates: render/timeline time)
- RenderSampleWindow = source-media sampling coordinates for nodes that
  consume source content (e.g. DECODE). Repository evidence:
  DefaultRenderMaterializer derives RenderSampleWindow from
  MediaClip.sourceRange + TemporalMapping (ConstantRateTemporalMapping /
  FreezeTemporalMapping.sourcePosition) — i.e. SOURCE coordinates.

Example (repository-real):

    Timeline clip range  = [0s, 10s]   (timeline domain)
    Source range         = [100s, 110s] (source domain)
    RenderExtent         = [0s, 10s]   (timeline domain)
    RenderSampleWindow   = [100s, 110s] (source domain)

Direct comparison would classify [100,110] vs [0,10] as DISJOINT and prune a
node that fully contributes. FORBIDDEN.

## 2. SELECTED_OPTION

OPTION A — typed #20 execution coverage concept.

RENDER_EXTENT_COORDINATE_DOMAIN=timeline / render request domain
RENDER_SAMPLE_WINDOW_COORDINATE_DOMAIN=source-media sampling domain
EXECUTION_COVERAGE_AUTHORITY=#20 RenderExecutionCoverage (new bounded typed hook in renderplan domain)

RenderExecutionCoverage:

- record (MediaTime start, MediaTime end, FrameRate frameRate)
- coordinate domain = RenderExtent / timeline execution domain
- semantics: declares which requested render interval the RenderNode
  contributes to
- exact rational time (MediaTime), deterministic, provider-neutral
- NOT a new Timeline authority; NOT TemporalMapping redefinition; NOT a
  universal time-interval god type
- a node may carry BOTH ExecutionCoverage (timeline coords) and
  RenderSampleWindow (source coords); they are different typed projections
- null coverage = node has no single coverage interval (e.g. OUTPUT / MUX /
  multi-input composites) => NEVER pruned by coverage reasoning

OWNER=#20 (DefaultRenderPlanner sets coverage from clip timeline placement;
renderplan domain owns the type). #21 CONSUMES ONLY.

PRUNING_AUTHORITY=#21 LogicalExecutionGraphBuilder (deterministic pure
mechanic): prune a node iff its OWN typed ExecutionCoverage is provably
disjoint from requested RenderExtent (coverage.end <= extent.start OR
coverage.start >= extent.end, exact rational). Nodes with null coverage are
never pruned.

ALL_PRODUCERS_ELIMINATED_RULE=FORBIDDEN (no typed dataflow law exists proving
a consumer has no independent extent contribution; removing the rule removes
the unsound inference).

TEMPORAL_MAPPING_AUTHORITY=UNCHANGED (TemporalMapping stays #20/timeline
domain authority; #21 never recomputes it, never reads authored Timeline state)
ROADMAP_20_AUTHORITY=UNCHANGED_EXCEPT_BOUNDED_TYPED_COVERAGE_HOOK (RenderExecutionCoverage type + planner assignment only)
ROADMAP_21_AUTHORITY=UNCHANGED (structural planning consumes coverage)
ROADMAP_22=NOT_STARTED

## 3. FORBIDDEN PATTERNS

- comparing RenderSampleWindow vs RenderExtent anywhere in #21
- converting both to "seconds" and comparing
- boolean isInExtent flags
- free-text coordinateSpace field
- universal MediaInterval god type erasing domain meaning
- recomputing TemporalMapping inside #21
- reading Timeline authored state inside #21
- letting provider/runtime decide pruning
- optimizer-inferred necessity
- ALL_PRODUCERS_ELIMINATED as out-of-extent proof

## 4. MANDATORY GUARDS

DIRECT_RENDER_SAMPLE_WINDOW_VS_RENDER_EXTENT_COMPARISON_COUNT=0
OBJECT_TOSTRING_CANONICAL_SEMANTIC_USAGE_COUNT=0

## 5. MANDATORY TESTS (coordinate-space cases)

nonZeroSourceOffsetDoesNotFalsePrune
constantRateMappingUsesCorrectCoordinateDomain
reverseMappingDoesNotFalsePrune
freezeMappingDoesNotFalsePrune
extentBoundaryExactness
extentPruningDeterministic
overlappingCoveragePreserved
outOfCoverageNodePruned

Example (Option A):

    coverage  = [0,10]   (timeline coords)
    sample    = [100,110] (source coords)
    extent    = [0,10]   (timeline coords)
    RESULT: node MUST survive

---

ARCHITECTURE_ESCALATION=RESOLVED (scoped to C12/C13 temporal-coordinate
boundary; no other authority reopened)

This correction does NOT reopen: #20 RenderPlan/RenderGraph overall authority,
#21 LogicalExecutionGraph overall model, #21 PhysicalExecutionPlan boundary,
#22 runtime architecture, Capability/Timeline/TemporalMapping authority.
