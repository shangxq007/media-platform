# SIXTH CORRECTION — REALITY AND PLAN

Base: f897c803383a7c5a6fea24acb1c901290bf9fe96

## S1 — Whole-Effect fingerprint collision (reproduced)

Current (fifth) semanticFingerprint built a manual envelope:

    "id=" + effect.id() + ";key=" + effect.effectKey() + ";params=" + canonicalJson

id/effectKey are NOT escaped. Reachable collision:

    A: id="a;key=b", key="c"  -> id=a;key=b;key=c;params={}
    B: id="a", key="b;key=c"  -> id=a;key=b;key=c;params={}

Identical raw envelope for distinct semantics.

FIX (implemented): manual envelope REMOVED. semanticFingerprint(effect) =
writeValueAsString(canonicalEffectValue(effect)) where canonicalEffectValue is a
typed JSON structure {id, effectKey, parameters=deepSorted(parameters)}.
id/effectKey are JSON-escaped fields — ";", "=", ",", quotes, backslash safe.

## S2 — encodeEffects bypassed deep canonical parameters (reproduced)

Current (fifth) encodeEffects put `e.parameters()` (raw Map) directly into the
node while semanticFingerprint used deepSorted — two representations.

FIX (implemented): encodeEffects elements now come from the SAME
canonicalEffectValue(effect) used by semanticFingerprint — one deep canonical
Effect representation, one field-knowledge location.

## S3 — Effect-delete × Automation-modify three-way E2E (implemented)

e2eS3EffectDeleteVsAutomationModifyFailsClosed in EffectTransitionEndToEndMergeTest:
- BASE: c1 + Effect fx1 + Automation auto1(target fx1, value 0.5) — valid
- OURS: deletes fx1 AND dependent auto1 (locally consistent, valid)
- THEIRS: retains fx1, modifies auto1 (value 0.8) — valid
- Real TimelineMergeEngine; outcome must not persist a dangling automation
  (conflict/blocked, or merged payload without auto1 + canonical reload valid)

## Production consumers of fingerprint/encode/decode

- CanonicalTimelineDiffCalculator.effectFingerprint (before/after op values)
- CanonicalTimelineDiffCalculator meta["effects"] (EFFECT_CHANGED + CLIP_ADDED)
- TimelinePatchApplier.parseEffects → decodeEffects
- TimelineClipEffect.semanticFingerprint (public API, tests)
- EffectCanonicalSemanticsFifthCorrectionTest / SixthCorrectionTest

## Tests mapped

S1-T1..T8 (adversarial collision, delimiters, quotes/backslash, same/diff state,
nested order) — EffectCanonicalSemanticsSixthCorrectionTest
S2-T1..T6 (top-level/nested byte determinism, list order, type distinctions,
single canonical value path, encode-decode-encode stability)
S3 E2E — e2eS3EffectDeleteVsAutomationModifyFailsClosed
Regression: fifth-correction suite (A-K + P/M), fourth-correction E2E 12,
closure suites, guards 14 OK (25 checks), Modulith.

## Non-blocking (per directive)

- canonicalmodel→app InternalTimelineJson import layering: NOT touched (deferred)
- EFFECT_NULL_PARAMETER_VALUE_AUTHORED_SUPPORT = NO (TimelineClipEffect
  Map.copyOf rejects null values; codec null capability is incidental)
