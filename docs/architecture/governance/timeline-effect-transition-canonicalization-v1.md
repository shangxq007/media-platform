# Timeline Effect / Transition Canonicalization V1 — Publication

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Milestone: TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1

## Base

- BASE_SHA = bc15576f34434f5aeee73b8080285bb91147f9ff
- BASE_TREE = e6f1d93447f854332e1780905723ae615d73446d

## Repository reality

- timeline-module already carried strong typed semantics: EffectInstance /
  EffectDefinition / ParameterSchema (semantics.effect), TransitionInstance
  (semantics.transition — first-class relationship with typed outgoing/incoming
  participants, MediaTime duration, alignment, temporalPolicy), Automation
  (semantics.automation — MediaTime keyframes, deterministic ordering,
  duplicate-time rejection, HOLD/LINEAR bounded)
- CanonicalSerializer deterministically serialized transitions[]/effects[]/
  automations[] — BUT omitted effect/transition parameter state
- Render EffectMappingService registers an effect catalog keyed by providerKey
  (ofx/javacv/ffmpeg) with entitlement tiers; particle_overlay uses a storage-URI
  parameter — classified execution-layer (not authored semantics)
- effect_pack tables = package/distribution metadata (never semantic authority)
- 24-type inventory classified; UNCLASSIFIED = 0; conflicts adjudicated (0 unresolved)

## Authority adjudication

- EFFECT_SEMANTIC_AUTHORITY = timeline (sole)
- TRANSITION_SEMANTIC_AUTHORITY = timeline (sole)
- AUTOMATION_SEMANTIC_AUTHORITY = timeline (sole)
- RENDER_CANONICAL_EFFECT_AUTHORITY_COUNT = 0 (render catalog demoted to
  implementation-availability view)
- PROVIDER_CANONICAL_EFFECT_AUTHORITY_COUNT = 0 (providerKey is catalog key,
  not semantic identity)
- Timeline authored semantics carry 0 provider command fragments
  (CANONICAL_TIMELINE_PROVIDER_COMMAND_COUNT = 0, TIMELINE_FFMPEG_COMMAND_FRAGMENT_COUNT = 0)

## Key defect fixed

CanonicalSerializer omitted EffectInstance.parameters/automationBindings and
TransitionInstance.parameters from canonical serialization — authored parameter
changes did NOT change the Timeline content hash (§30 violation). Fixed:
stringMapField with sorted keys now serializes typed parameter state and
automation bindings for effects and transitions (C12/§30/§29).

## Canonical model (preserved + hardened)

- EffectInstance: typed semantic state (definition ref, media type, enabled,
  application range, parameters, automation bindings)
- TransitionInstance: first-class relationship (typed participants, exact MediaTime
  duration > 0, alignment, temporal policy) — never clip.effects
- AutomationCurve/Keyframe: exact MediaTime, deterministic ordering,
  duplicate-time rejection, HOLD/LINEAR bounded, no wall clock
- TimelineClipEffect opaque payload: preserved for gate/merge (CNM1) — typed
  semantics are the canonical authored representation

## Serialization / hash

- Deterministic serialization (sorted map keys, stable field order) — repeated
  serialization byte-identical
- EFFECT_PARAMETER_CHANGE_AFFECTS_HASH = YES (new tests)
- EFFECT_ORDER_CHANGE_AFFECTS_HASH = YES
- AUTOMATION_CHANGE_AFFECTS_HASH = YES
- TRANSITION_CHANGE_AFFECTS_HASH = YES
- PROVIDER_SELECTION_AFFECTS_TIMELINE_HASH = NO (no provider state in canonical payload)

## Diff / patch / merge

- Coarse typed categories (CLIP_EFFECT_CHANGED / TRANSITION_CHANGED) preserved;
  round-trip flows through canonical payload (diff → patch → revision)
- Merge preserves opaque effect payloads (CNM1 design decision — effects never
  semantically merged; documented preservation contract)

## Render / provider boundary

- AdvancedEffectsPipeline = derived execution consumer (lowering only)
- EffectMappingService = implementation-availability catalog; providerKey and
  assetPath (storage URI) are execution-layer — deferred to provider fabric (#22)
  and artifact-backed parameter typing
- FFmpeg lowering one-way in render services; no reverse mapping authority

## Temporal / artifact

- TemporalMapping authority preserved (semantics/temporal intact)
- Timeline MediaTime preserved everywhere (automation/transition use exact rational time)
- Artifact authority preserved (no storage URI in timeline authored semantics)

## Schema

- TIMELINE_V3_INTRODUCED_COUNT = 0
- V1_CHANGED = NO (revision payload carries semantics; no DB change needed)
- JOOQ_CHANGED = NO
- FLYWAY_SCRIPT_COUNT = 1, PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT = 0

## GCR regression

GCR1_GUARD = PASS, GCR2_GUARD = PASS, GCR2_CORRECTION_V1_GUARD = PASS,
GCR5_GCR6_GUARD = PASS, JOOQ_FOUNDATION = PASS, MODULITH = PASS

## Tests

- WHOLE_REPOSITORY = 910 suites / 7178 tests / 0 failures / 0 errors / 43 skipped
- Delta vs baseline (909/7167/43): +1 suite, +11 tests — all additions
  (EffectTransitionCanonicalSemanticsTest: deterministic serialization, parameter/
  order/automation/transition hash changes, map key sorting, duplicate-keyframe
  rejection, first-class transition, exact MediaTime, typed parameter serialization)
- NO_UNEXPLAINED_TEST_DELETION = PASS; NO_ASSERTION_WEAKENING = PASS

## Gates

TIMELINE_EFFECT_TRANSITION_GUARD = PASS (verifyTimelineEffectTransitionCanonicalization)
ARCHITECTURE_DRIFT = PASS (224) / MAP_DRIFT = PASS (41/23/3) /
MAP_DETERMINISM = PASS (3x byte-identical 4a5f7a9f…) / BOOTJAR = PASS /
PFIRR1 = PASS / CREDENTIAL_SCAN = 0 / GREENFIELD_RESIDUE = PASS

## Manifest

TIMELINE_EFFECT_TRANSITION_MANIFEST.tsv — 14 rows; UNCLASSIFIED = 0,
FINAL_AUTHORITY_MISMATCH = 0, FINAL_LOCATION_MISMATCH = 0,
FINAL_VERIFIED_PENDING = 0, DECISION_EVIDENCE_CONTRADICTION_COUNT = 0

## Candidate

- CANDIDATE_SHA = ad3c097b87e5e1dd38ab64fb1e262385497dc817
- CANDIDATE_TREE = 9ec518f82f324df5d9069fa49e537fc97837fbfd
- Ancestry: bc15576f → ad3c097b (single commit, linear, no merge/rebase/squash)
- Candidate contains NO publication / FCV PASS claim

## Final FCV

TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1_FINAL_FCV = PASS (26/26) — run
against the frozen candidate ad3c097b before this publication.

## Deferred findings

- DEFERRED_FINDING: Render EffectMappingService providerKey identity + assetPath
  storage-URI parameter (execution catalog). Owner: render/capability. Risk: low
  (execution-layer only; not canonical). Target: provider fabric (#22),
  artifact-backed effect parameter typing.
- DEFERRED_FINDING: Typed semantic merge for EffectInstance/TransitionInstance/
  AutomationCurve beyond CNM1 opaque preservation. Owner: timeline. Risk: low.
  Target: future milestone.
- DEFERRED_FINDING: Typed diff granularity (EffectAdded/Removed/Reordered,
  AutomationKeyframe*) beyond coarse CLIP_EFFECT_CHANGED. Owner: timeline.
  Risk: low. Target: future milestone.
- DEFERRED_FINDING: Full typed effect parameter value objects (Color, vector,
  Artifact reference) beyond schema-validated strings. Owner: timeline. Risk: low.
  Target: future milestone.
