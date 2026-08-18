# Roadmap #19 — TimedText Presentation Foundation — Correction 1 (ChatGPT Final-Review)

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Correction: ROADMAP_19_TIMEDTEXT_PRESENTATION_FIRST_CORRECTION

## Original Roadmap #19 chain (immutable)

- BASE = 72c148da52c054f90ae59d8d581b66ff5952f70d
- CANDIDATE = db8a05a3ac0ad04f5f388076420fe1621bfb7560
- PUBLICATION = 871acec9ec5c1b811480a2874a752c43f86a8ffe
- PUBLICATION_RECORD = 337fceb03d4b23e4c31eb490bd41752ccaef6e85

## ChatGPT final-review verdict (first correction)

FINAL_REVIEW = FAIL_CORRECTABLE
BLOCKERS = 2:
TT-C1 TEXT_ELEMENT ADD/DELETE operation payload is not a complete local
     semantic payload (ADD afterValue / DELETE beforeValue carried only the id)
TT-C2 TimedText canonical authority still partly implicit/reflective
     (getDeclaredFields/setAccessible; domain objects placed raw into canonical
     maps; Jackson bean shape decided canonical form)

## Correction 1 (append-forward, base 337fceb0)

TT-C1 — complete payload contract:
- ADD: beforeValue empty; afterValue = complete canonical semantic payload
  (fingerprint JSON) of the added TextElement
- MODIFY: before/after = complete canonical payloads (unchanged behavior)
- DELETE: beforeValue = complete canonical payload; afterValue empty;
  safeMetadata.deleted=true
- ID-only add payload FAILS CLOSED (decode throws; no legacy fallback,
  no try-id-then-json, no dual format)
- real TimelineMergeEngine source-only ADD E2E (F7): merged payload reload
  yields EXACT TextElement equality (id/start/duration/StyledText/frame/
  fallbackPolicy/resolvedFontRuns)

TT-C2 — explicit non-reflective canonical schema:
- TimedTextCanonicalSemantics rewritten under
  TIMEDTEXT_CANONICAL_SCHEMA_IS_EXPLICIT_NOT_REFLECTIVE_V1
- toCanonicalNode(element) maps EVERY active authored field explicitly through
  public accessors (TextElement/StyledText/TextContent/TextSemanticRun/
  TextStyleRun/TextStyle/FontSelectionIntent/OpenTypeFeatureIntent/ParagraphStyle/
  TextFrame/FontFallbackPolicy/ResolvedFontRun/ResolvedFontInstance/
  ValidatedFontExecutionReference/FontContentDigest/FaceIndex/VariationCoordinate)
- reflection removed (0 getDeclaredFields / 0 setAccessible in production)
- nullable UNSPECIFIED fields → explicit JSON null (frozen representation)
- semanticFingerprint/encodeElements consume toCanonicalNode
- TimelineMergeEngine.textElementsToJson delegates to
  TimedTextCanonicalSemantics.toCanonicalNode (no direct valueToTree)
- Jackson remains bounded decode plumbing only (7 single-value types switched
  to delegating creators to match explicit canonical String forms)
- JACKSON_METADATA ≠ CANONICAL_SCHEMA_AUTHORITY: canonical field set owned
  explicitly by the local authority

## Correction candidate

- CANDIDATE_SHA = 3c8a6d13ae81f46ed04bc73aff58c9706538440c
- CANDIDATE_TREE = ef8b4ef242d0ee41785dd601cbe1465bfa8f9df7
- Ancestry: 337fceb0 → 3c8a6d13 (single commit, linear)

## Tests

- TimedTextCorrectionOneTest (17): TT-C1-T1..T5, TT-H1..H8 (content/timing/
  style/paragraph/font-selection/resolved-font sensitivity + provider-only
  independence), DET-1/9/10, canonical parity
- TimedTextMergeEngineTest (7 incl. NEW F7 source-only ADD exact round-trip;
  F1-F6 unchanged green)
- Whole repository: 919 suites / 7297 tests / 0 failures / 0 errors /
  43 skipped (Δ vs 918/7280/43: +1 suite +17 tests, all additions)
- Guards: 14 OK (47 checks incl. TT-C1/TT-C2); Modulith PASS; arch drift 224;
  map drift PASS; determinism 3x; bootJar PASS; PFIRR1 PASS; cred 0; residue PASS
- V1_CHANGED = NO; JOOQ_CHANGED = NO; TIMELINE_V3 = 0

## Final FCV

ROADMAP_19_CORRECTION_1_FINAL_FCV = PASS (26/26) — run against the frozen
correction candidate 3c8a6d13 before this publication.

## Deferred non-blocking

- advanced typography beyond current authored contract
- additional shaping/writing-system features
- richer text animation
- derived glyph/layout caches
- renderer implementation / real execution validation
- package cleanup
- #20 Render DAG / Artifact Graph / RenderExtent
- #22 Provider Fabric
