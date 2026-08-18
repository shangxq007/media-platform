# FOURTH CORRECTION — DEFECT AND RESIDUE MATRIX

Base: 2899b7c38e32ad72e4153cb221dac9db53bc3f4b

## BLOCKER 1 — Cross-object aggregate validation not closed

DEFECT: TimelineCanonicalValidator did not enforce Transition endpoint or
Automation target references — dangling references could pass the canonical
gate and persist.

FIX:
- TimelineCanonicalValidator.validateTransitionReferences: every Transition
  outgoingClipId/incomingClipId must reference an existing Clip in the SAME
  aggregate; outgoing != incoming (self-reference rejected). New diagnostic
  codes TIMELINE_TRANSITION_ENDPOINT_MISSING / TIMELINE_TRANSITION_SELF_REFERENCE.
- TimelineCanonicalValidator.validateAutomationTargets: supported target
  universe = Effect instance within the same aggregate (repository reality);
  referenced Effect identity must exist on some Clip. New diagnostic code
  TIMELINE_AUTOMATION_TARGET_MISSING.
- Validation runs at import (before save) AND after merge reload (final safety
  boundary).
- Real tests: xv1/xv2 (dangling endpoints rejected), xv3 (self-reference),
  xv6 (missing automation target), e2eX1 (REAL three-way delete-vs-reference:
  case A consistent delete → merged valid; case B delete vs modified Transition
  → explicit conflict, no silent merge).

## BLOCKER 2 — Effect local semantic ownership unfinished

DEFECT: Effect had no local semantic fingerprint; production diff used
List.toString()/Map.toString() as semantic identity; TimelineClipEffect carried
stale "opaque / NEVER semantically merged / preserved verbatim" assertions.

FIX:
- TimelineClipEffect upgraded to canonical Effect semantic owner with
  semanticFingerprint(): id + effectKey + parameters (TreeMap sorted keys —
  insertion-order independent, deterministic, provider-free).
- CanonicalTimelineDiffCalculator consumes effectFingerprint() (delegates to
  TimelineClipEffect.semanticFingerprint()); beforeValue/afterValue use the
  fingerprint — no List/Map toString signatures, no central field encoding.
- Stale opaque language removed from TimelineClipEffect,
  InternalTimelineCandidateAdapter.mapEffects, TimelineMergeEngine.clipToJson.
- OPAQUE_EFFECT_NEVER_MERGED residue = 0.

## BLOCKER 3 — True end-to-end merge engine proof missing

DEFECT: tests stopped at diff/planner/patch; no proof through the actual
TimelineMergeEngine and final merged payload.

FIX: EffectTransitionEndToEndMergeTest (11 tests) invokes the REAL
TimelineMergeEngine over base/source/target revision rows + snapshot payloads,
then reloads the merged payload through the canonical adapter and re-validates:
- e2eM1/M2/M3: Effect/Transition/Automation source-only changes survive actual
  merge; unrelated semantic families preserved (reloaded state asserted)
- e2eR1/R2: delete-last → merged payload carries canonical empty (field
  absent), reload yields empty collection, unrelated family preserved
- e2eC1: divergent two-sided Effect edit → NO silent merged revision
- e2eX1: REAL three-way delete-Clip vs Transition (case A consistent delete
  merged valid; case B delete-vs-modify explicit conflict)
- xv1/xv2/xv3/xv6: aggregate validation fail-closed

## Greenfield compatibility removal

- TimelineCandidate.Clip legacy 6-arg constructor REMOVED (0 callers after
  migration of 2 production call sites: TimelineCandidate.fromModel,
  TimelineDocumentCandidateMapper.toClip — migrated to full constructor with
  explicit FrameRate.of(30,1) + List.of() effects, behavior identical)
- TimelineCandidate.clip() factory kept (test-only callers) but migrated
  internally to the full constructor
- TimelineRevisionSaveService backward-compatible constructor RETAINED:
  revision-persistence wiring (NOT Timeline semantic state); real existing
  E1 direct-wiring tests depend on it; outside this bounded semantic scope
  (documented, not a semantic compatibility shell)

## RESIDUE (target = 0)

- unsafe snapshot constructor: 0 (removed in third correction)
- legacy TimelineCandidate constructor: 0 (removed)
- legacy Clip constructor: 0 (removed, callers migrated)
- target-side semantic fallback: 0
- dual-read: 0 / dual-write: 0 / fallback parser: 0
- shadow semantic representation: 0
- opaque effect never-merge semantics: 0 (language removed)
- incomplete semantic fingerprint: 0 (Effect/Transition/Automation all complete)
- dangling Transition endpoint accepted: 0 (validator enforced)
- dangling Automation target accepted: 0 (validator enforced)
- provider leakage: 0
- Timeline V3: 0
