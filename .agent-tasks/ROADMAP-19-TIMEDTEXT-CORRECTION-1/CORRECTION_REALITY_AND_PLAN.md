# ROADMAP #19 — CORRECTION 1 — REALITY AND PLAN

Base: 337fceb03d4b23e4c31eb490bd41752ccaef6e85

## TT-C1 — ADD/DELETE payload not a complete local semantic payload (reproduced)

- diff ADD emitted afterValue = `addedElement.id().value()` (id only)
- diff DELETE emitted beforeValue = `deletedElement.id().value()` (id only)
- patch applyTextElementChanged decodes `"[" + afterValue + "]"` as a complete
  canonical TextElement — an id-only payload cannot be reconstructed (REAL
  production-path defect)

FIX:
- ADD afterValue = TimedTextCanonicalSemantics.semanticFingerprint(after)
  (complete canonical payload; fingerprint IS canonical JSON)
- DELETE beforeValue = semanticFingerprint(before); afterValue empty;
  safeMetadata.deleted=true
- ID-only payload now fails closed (decode of `["t1"]` throws — no legacy
  fallback, no try-id-then-json)

## TT-C2 — canonical authority partly implicit/reflective (reproduced)

- runValue() used `run.getClass().getDeclaredFields()` + `field.setAccessible(true)`
  — private JVM field names became canonical schema by accident
- domain objects (TextContent/ParagraphStyle/FontFallbackPolicy/ResolvedFontRun)
  placed directly into canonical maps; Jackson bean shape decided canonical form

FIX:
- TimedTextCanonicalSemantics fully rewritten:
  TIMEDTEXT_CANONICAL_SCHEMA_IS_EXPLICIT_NOT_REFLECTIVE_V1
- toCanonicalNode(element): explicit mapping through public accessors for every
  active authored field (TextElement/StyledText/TextContent/TextSemanticRun/
  TextStyleRun/TextStyle/FontSelectionIntent/OpenTypeFeatureIntent/ParagraphStyle/
  TextFrame/FontFallbackPolicy/ResolvedFontRun/ResolvedFontInstance/
  ValidatedFontExecutionReference/FontContentDigest/FaceIndex/VariationCoordinate)
- nullable UNSPECIFIED fields (language/script) → explicit JSON null
- rationals → {numerator, denominator} string form (deterministic)
- semanticFingerprint/encodeElements consume toCanonicalNode

## Production consumers migrated

- CanonicalTimelineDiffCalculator: ADD/DELETE payloads → fingerprint
- TimelineMergeEngine.textElementsToJson → TimedTextCanonicalSemantics.toCanonicalNode
  (no direct valueToTree(TextElement))
- TimelinePatchApplier: unchanged delegation (decode via local authority)
- InternalTimelineCandidateAdapter: unchanged delegation (fromCanonicalNode)

## Codec decode plumbing

- 5 single-value types switched to DELEGATING creators to match the explicit
  canonical String forms: TextContent, LanguageTag, ScriptTag, VariationAxisTag,
  OpenTypeFeatureTag, FontFamilyName, FontContentDigest (7 total; TextElementId
  already delegating)
- Jackson remains decode plumbing; canonical field set owned by
  TimedTextCanonicalSemantics explicit mapping

## Tests

- TimedTextCorrectionOneTest (17): TT-C1-T1..T5 (add/delete payloads, patch add,
  delete-last, ID-only fail-closed), TT-H1..H8 (content/timing/style/paragraph/
  font-selection/resolved-font sensitivity + provider independence), DET-1/9/10,
  parity
- TimedTextMergeEngineTest F7: source-only ADD real engine exact round-trip
  (F1-F6 unchanged, all still green)

## Host font search

- No production canonical TimedText/shaping path performs FontFamilyName host
  lookup: TextShapingRequest consumes ResolvedFontInstance; canonical state
  carries semantic selection + historical-frozen resolved runs; fallback is the
  explicit FontFallbackPolicy. No fontconfig/AWT/drawtext-fontfile hits in the
  canonical path.
