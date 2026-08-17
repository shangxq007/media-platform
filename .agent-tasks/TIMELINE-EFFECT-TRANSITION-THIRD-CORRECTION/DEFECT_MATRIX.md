# THIRD CORRECTION — DEFECT MATRIX & RESOLUTION

Base: ace41ec86afa3f075a8527775ac3393e62515818

## BLOCKER 1 — Snapshot copy / patch state loss

DEFECT: CanonicalTimelineSnapshot retained an incomplete convenience constructor
(defaulting transitions/automations to List.of()). TimelinePatchApplier used it
in 12 reconstruction paths (withTracks, applyDuration, caption/watermark/
template/workflow/output/metadata paths) — any Track/Clip/Effect operation
silently erased unrelated Transition/Automation state.

FIX: Removed the convenience constructor (full 12-field constructor is the only
one). Added full-state copy helpers: withTracks/withDuration/withTransitions/
withAutomations/withMetadata — each preserves every unrelated field. Migrated
all 160 call sites (7 timeline test files + 1 render test file).

## BLOCKER 2 — Transition/Automation delete semantics

DEFECT: diff could represent existing→absent but apply treated TRANSITION_CHANGED/
AUTOMATION_CHANGED as add-or-replace; toInternalPayload used `if (!isEmpty())`
— deleting the last item silently resurrected target state.

FIX:
- diff emits explicit delete ops: safeMetadata["deleted"]="true",
  afterValue=empty (beforeValue=complete fingerprint of deleted state)
- patch applier handles "deleted" → removes the object (fails if absent)
- toInternalPayload writes MERGED result as authority: empty merged collection
  REMOVES the composition field (canonical convention: absent == empty); never
  resurrects target-authored semantics

## BLOCKER 3 — Incomplete merge semantic signature

DEFECT: transitionOp afterValue was durationTicks:durationTimeScale:alignment —
parameter/policy/definition/participant divergent edits misclassified as
BOTH_IDENTICAL (false SAFE). Automation afterValue was keyframes only — missing
parameterPath/valueType/extrapolation/targetEntityId.

FIX: canonical component records own complete deterministic semanticFingerprint():
- CanonicalTimelineTransitionSnapshot: definition/version/outgoing/incoming/
  mediaType/duration(ticks/scale)/alignment/temporalPolicy/parameters (TreeMap
  sorted — insertion-order independent)
- CanonicalTimelineAutomationSnapshot: targetEntityId/parameterPath/valueType/
  extrapolation/ordered keyframes (id/time/value/interpolation)
- localSemanticsEquals delegates to fingerprint (ONE authority)
- diff afterValue = fingerprint (complete; different semantics → different
  afterValue → planner conflict)

## RESIDUE (target = 0)

- unsafe incomplete snapshot constructors: 0 (removed)
- target-side semantic fallback: 0 (merged result is authority)
- dual-write: 0
- fallback read: 0
- shadow representation: 0
- old opaque-never-diff rule: 0 (removed in second correction)
- unhandled semantic delete: 0 (deleted flag handled)
- incomplete semantic signature: 0 (complete fingerprints)
- provider leakage: 0
- Timeline V3: 0
