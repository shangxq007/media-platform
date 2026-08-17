# EFFECT_TRANSITION_SEMANTIC_OWNERSHIP_MAP.md

SECOND CORRECTION — production merge semantic ownership (base a017a0dc + correction).

## Authority before (first correction end)

- Timeline authored state: clip.effects[] / composition.transitions[] /
  composition.automations[] in timeline JSON (import authoring added)
- Hash: timeline JSON → sha256 (covers all three)
- JSON-level diff: TimelineSemanticDiffService (CLIP_EFFECT_CHANGED /
  TRANSITION_CHANGED / AUTOMATION_CHANGED)
- Production merge path: canonical gate → TimelineCandidate (NO transitions/
  automations) → TimelineSnapshotConverter → CanonicalTimelineSnapshot (NO
  transitions/automations; effects opaque) → CanonicalTimelineDiffCalculator
  ("Effects are OPAQUE (CNM1): never diffed") → conflict detector → planner →
  TimelinePatchApplier (no semantic cases) → toInternalPayload (deep-copy
  target, replace tracks only)
- Defect: production merge could NOT observe effect/transition/automation
  local semantic edits; two-sided divergent edits never conflicted; one-sided
  changes silently lost (target preservation).

## Authority after (second correction)

Effect local semantics:
- Canonical state: clip.effects[] (id/effectKey/parameters) — unchanged
- Local equality: TimelineClipEffect record equality (id/effectKey/parameters)
- Production diff: CanonicalTimelineDiffCalculator emits EFFECT_CHANGED op
  (path timeline.tracks.<track>.clips.<clip>.effects; after-state in safeMetadata)
- Production patch: TimelinePatchApplier.applyEffectChanged materializes from
  safeMetadata
- Merge: planner classifies one-sided SAFE / two-sided divergent CONFLICT by
  path + afterValue signature

Transition local semantics:
- Canonical state: composition.transitions[] — unchanged
- Canonical component: CanonicalTransition (canonicalmodel) →
  CanonicalTimelineTransitionSnapshot (diff.calculation) with
  localSemanticsEquals (definition/version/participants/mediaType/duration/
  alignment/temporalPolicy/parameters)
- Production diff: TRANSITION_CHANGED op (path timeline.transitions.<id>;
  afterValue = duration+alignment semantic signature)
- Production patch: applyTransitionChanged
- Merge: planner by path + signature

Automation local semantics:
- Canonical state: composition.automations[] — unchanged
- Canonical component: CanonicalAutomationCurve/Keyframe →
  CanonicalTimelineAutomationSnapshot/Keyframe with localSemanticsEquals
  (target/path/valueType/extrapolation/keyframes)
- Production diff: AUTOMATION_CHANGED op (path timeline.automations.<id>;
  afterValue = keyframes signature)
- Production patch: applyAutomationChanged
- Merge: planner by path + signature

Timeline still owns structurally: tracks/clips/placement/duration/cross-object
invariants/merge orchestration/conflict detection orchestration.

## Cross-object

- Transition participants are validated by the canonical gate (dangling refs
  rejected at gate; adapter mapTransition requires nonblank outgoing/incoming).
- delete-vs-modify cases flow through existing structural conflict analysis
  (CLIP_REMOVED vs EFFECT_CHANGED/TRANSITION_CHANGED on related paths).
