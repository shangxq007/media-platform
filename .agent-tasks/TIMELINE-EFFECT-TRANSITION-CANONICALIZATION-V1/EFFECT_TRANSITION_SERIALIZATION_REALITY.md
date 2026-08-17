# TIMELINE EFFECT / TRANSITION — SERIALIZATION / DIFF / MERGE REALITY

## Serialization reality
- CanonicalSerializer deterministically serializes:
  - transitions[] (transitionId, definitionId, version, outgoing/incoming, mediaType,
    duration as exact MediaTime, alignment, temporalPolicy, parameters sorted)
  - effects[] (instance fields in stable order, parameters map)
  - automations[] (curve fields, keyframes sorted by time)
- Uses string builders with stable field order + array iteration — deterministic
  (no Map iteration order reliance: parameters maps are copied via Map.copyOf and
  serialized in stable order — verify appendEffect sorts parameter keys)
- Content hash: Timeline revision content_hash computed from canonical serialization
  (already in place; effect/transition/automation changes flow through it)

## Hash reality
- SERIALIZATION_GAP: appendEffect/appendTransition/appendAutomation serialize
  parameter maps — must confirm key sorting for determinism (Map.copyOf does not
  guarantee iteration order; serializer must sort keys explicitly)
- HASH_GAP: none structural — canonical payload → hash chain exists

## Diff reality
- SemanticChangeType: CLIP_EFFECT_CHANGED (coarse), TRANSITION_CHANGED (coarse)
- No typed effect added/removed/reordered/parameter-changed categories
- No automation keyframe diff categories
- DIFF_GAP_COUNT = 2 (effect granularity, automation coverage)

## Merge reality
- TimelineMergeEngine preserves TimelineClipEffect verbatim (CNM1: never
  semantically merged) — documented preservation contract
- No typed EffectInstance/TransitionInstance/AutomationCurve merge semantics in
  the semantic merge path (typed merge gap — bounded cases to add)
- MERGE_GAP_COUNT = 1 (typed semantic merge for new authored state)

## Patch reality
- Timeline patch applies canonical diffs (SemanticChange → operation plan →
  revision command) — effect/transition coarse changes flow through
- PATCH_GAP_COUNT = 0 structural (coarse granularity inherits from diff)

## Counters
SERIALIZATION_GAP_COUNT = 1 (parameter map key ordering verification)
HASH_GAP_COUNT = 0
DIFF_GAP_COUNT = 2
PATCH_GAP_COUNT = 0
MERGE_GAP_COUNT = 1
TRANSITION_AS_CLIP_EFFECT_COUNT = 0 (transition is first-class, never clip.effects)
AUTOMATION_WALL_CLOCK_COUNT = 0
LEGACY_EFFECT_AUTHORITY_COUNT_BEFORE = 1 (TimelineClipEffect opaque as the only
gate/merge representation for effects — bounded to preservation role)
LEGACY_TRANSITION_AUTHORITY_COUNT_BEFORE = 0
