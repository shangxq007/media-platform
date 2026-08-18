# FIFTH CORRECTION — DEFECT AND RESIDUE MATRIX

Base: 1210b71ca4c8cceb73b4134b19990b852f7b4a17

## BLOCKER F1 — Automation target validation incomplete

DEFECT (TimelineCanonicalValidator.validateAutomationTargets):
- `if (candidate.automations() == null || clipsById.isEmpty()) return;` —
  a Timeline with zero Clips/Effects but Automations BYPASSED target
  validation entirely
- no Effect instance ID uniqueness invariant (duplicate Effect IDs across
  Clips created ambiguous Automation references)

FIX:
- early-return now keys on `candidate.automations().isEmpty()` only — zero
  Clips no longer bypasses validation (target cannot resolve → REJECT)
- new validateEffectIdUniqueness: aggregate invariant, runs ALWAYS (regardless
  of Automation presence); duplicate non-null Effect IDs → fail closed with
  TIMELINE_EFFECT_ID_DUPLICATE
- supported target universe unchanged: Effect instance in the same aggregate

## BLOCKER F2 — Effect fingerprint not deep/type-preserving

DEFECT: TimelineClipEffect.semanticFingerprint() sorted only the top-level map
and appended Object.toString() values — number 9 vs string "9", boolean vs
"true", null vs "", nested maps, and delimiter collisions
({"a":"1,b=2"} vs {"a":"1","b":"2"}) were not distinguished.

FIX: EffectCanonicalSemantics (canonicalmodel) — the single local Effect
semantic codec:
- deepSorted: recursive TreeMap normalization (nested Maps key-sorted)
- encodeValue: typed JSON encoding (number/string/boolean/null distinct)
- semanticFingerprint: id + effectKey + deep-canonical parameters
- TimelineClipEffect.semanticFingerprint() delegates here

## BLOCKER F3 — diff/patch independent Effect grammar, lossy reconstruction

DEFECT:
- CanonicalTimelineDiffCalculator encoded id/effectKey/parameters with custom
  \u001f/\u001e delimiters and unsorted k=v,k=v streams
- TimelinePatchApplier.parseEffects() independently split on "," and "=" and
  converted every parameter VALUE to String (radius 9 → "9")

FIX:
- diff meta["effects"] = EffectCanonicalSemantics.encodeEffects (JSON array,
  typed, lossless) in both EFFECT_CHANGED and CLIP_ADDED paths
- patch parseEffects() delegates to EffectCanonicalSemantics.decodeEffects —
  no independent grammar; values keep types (integer/string/boolean/nested)
- CLIP_ADDED decode strips the "effects=" label prefix
- no dual parser / no legacy delimiter decoder retained

## BLOCKER F4 — greenfield zero-compatibility cleanup

F4.1: InternalTimelineCandidateAdapter.mapEffects — blank effectKey previously
substituted "opaque"; now FAILS CLOSED (new adapter code
TIMELINE_EFFECT_KEY_INVALID). No "opaque"/"unknown" fallback.

F4.2: TimelineCandidate structural-only 4-arg constructor + of() factory
REMOVED (1 production caller TimelineDocumentCandidateMapper migrated to full
6-arg form; fromCanonical retained — normal production method, no semantic
defaulting). TimelineCandidate.Clip legacy 6-arg constructor already removed in
fourth correction.

F4.3: TimelineImportRequest backward-compatible 17-arg constructor REMOVED
(2 production callers migrated: TimelineSpecImportAdapter (render), tests).

F4.4: TimelineRevisionSaveService 3-arg constructor RETAINED (verified):
revision-persistence wiring only; does not default any authored semantic field;
no alternate semantic representation; existing E1 direct-wiring tests depend on
it; outside the bounded semantic scope.

## Callers migrated (constructor removals)

- TimelineDocumentCandidateMapper.toCandidate: 4-arg → 6-arg (+List.of(),List.of())
- TimelineSpecImportAdapter (render-module): 17-arg → 19-arg (+transitions,automations)
- TimelineConversionServiceDelegationTest (render): 17-arg → 19-arg
- TimelineImportServiceTest: 3 × 17-arg → 19-arg
- TimelineCanonicalNormalizerTest / ProductionBoundaryTest / ValidatorTest:
  10 × TimelineCandidate.of() → 6-arg constructor

## Residue classification (F4.5)

- "Legacy constructor": 0 in Timeline semantic scope (TimelineRevisionSaveService
  3-arg retained = persistence wiring, documented)
- "Backward-compatible": 0 semantic (SaveService constructor is the only
  remaining "backward" wording; persistence wiring, documented)
- "opaque": 0 as effect fallback; remaining "opaque" wording is neutral
  payload description (documented, non-semantic)
- "fallback": 0 semantic (parseFrameRate fallback = structural parsing helper)
- dual-read/write/shadow/V2/V3: 0
