# ROADMAP #19 — TIMEDTEXT REALITY AND DECISION MATRIX

Base: 72c148da52c054f90ae59d8d581b66ff5952f70d

## Repository reality (text representations)

| Representation | Location | Classification |
|---|---|---|
| timeline/canonical TextElement | timeline-module | A. CANONICAL TimedText semantic authority (partial) |
| TextElementId | timeline-module | A. canonical identity |
| font-text StyledText | font-text-module | A. canonical styled-text semantics (content + semanticRuns + styleRuns + paragraphStyle) |
| TextStyleRun / TextSemanticRun | font-text-module | A. canonical style/span semantics |
| TextFrame | font-text-module | A. canonical presentation frame (width/height/alignment/wrap/overflow) |
| FontFallbackPolicy | font-text-module | A. canonical explicit fallback policy |
| ResolvedFontRun | font-text-module | A. canonical historical-frozen font resolution (never re-resolves) |
| ImportTextOverlay / TextOverlay | render-module, TimelineImportRequest | B. application/import DTO (external interchange) |
| CaptionTemplate / NormalizedCaptionLayer / AutoCaptions | render-module | C. render/execution projection |
| TimelineDocument.textElements | timeline-module | A. aggregate collection + hash source |
| CanonicalTimelineSnapshot.textElements | timeline-module (diff) | A. snapshot projection for diff/merge |

## Existing partial implementation (verified)

- TextElement: id/start/duration/styledText/frame/fallbackPolicy/resolvedFontRuns,
  equals on all fields, duration>0 construction invariant
- TimelineContentDigester: sha256 over canonical TimelineDocument JSON; textElements
  participate; element ordering deterministic by id
- diff: TEXT_ELEMENT_CHANGED detection exists
- conflict detector: TEXT_ELEMENT_CHANGED → TEXT_ELEMENT_CONFLICT / DIVERGENCE
- tests: TextElementTimelineIntegrationTest (hash/order), TextElementDiffMergeTest
  (detector-level only)

## Defects this milestone closes (bounded)

1. D1 — CanonicalTimelineDiffCalculator.summary() uses
   `content().value().hashCode()` as semantic identity (forbidden by directive §5).
   → replace with TimedText local canonical fingerprint.
2. D2 — TimelinePatchApplier has NO TEXT_ELEMENT_CHANGED branch → merge pipeline
   cannot APPLY TimedText changes; TextElementDiffMergeTest only exercises the
   conflict DETECTOR, never the real merge application.
   → add patch branch delegating to local decode; prove with real
   TimelineMergeEngine E2E.
3. D3 — No single local TimedText semantic authority (canonical value /
   fingerprint / encode / decode) — diff independently knows field grammar.
   → NEW TimedTextCanonicalSemantics (timeline-module canonical package).
4. D4 — No aggregate TextElement ID uniqueness validation.
   → validator contribution (duplicate id fail-closed).

## Frozen decision contract

TIMED_TEXT_PRESENTATION_BOUNDED_ARCHITECTURE_CONTRACT_V1
- C1 identity: TextElementId (value type)
- C2 authored fields: id, start, duration, styledText, frame, fallbackPolicy,
  resolvedFontRuns — ALL active authored semantics
- C3 StyledText owns: content + semanticRuns + styleRuns + paragraphStyle
- C4 runs: TextStyleRun (typography) + TextSemanticRun (language/script/direction)
- C5 exact time: FontRational (rational) start + duration — no doubles
- C6 overlapping timing: VALID (no invented prohibition)
- C7 duplicate TextElementId: fail-closed (aggregate validation)
- C8 canonical representation: TimedTextCanonicalSemantics.canonicalValue —
  deep-sorted deterministic typed JSON
- C9 fingerprint: canonical JSON (NO Java hashCode/toString)
- C10 hash: all active authored fields participate (digester covers via JSON)
- C11 diff: fingerprint comparison; afterValue = fingerprint
- C12 patch: decode fingerprint → TextElement replacement (single authority)
- C13 merge: path-grouped planner — source-only SAFE, identical SAFE,
  divergent CONFLICT, add/delete SAFE, delete-vs-modify CONFLICT
- C14 cross-object references: NONE (independent collection)
- C15 font identity: semantic family request (FontFamilyName) + ResolvedFontRun
  historical freeze (never re-resolves)
- C16 existence validation: runtime/preflight deferred; semantic identity
  deterministic regardless of host availability
- C17 fallback: explicit FontFallbackPolicy enum only; no silent host fallback
- C18 provider exclusion: TextElement carries no provider commands; renderer is
  operational only
- C19 legacy: ImportTextOverlay/TextOverlay = import DTO (B); Caption models =
  render projection (C); TextElement = single canonical authority (A)
- C20 preview consistency: deterministic provider-neutral presentation
  projection contract; current render consumers derive from canonical

READY_FOR_TIMEDTEXT_PRESENTATION_IMPLEMENTATION = YES
ARCHITECTURE_ESCALATION_REQUIRED = NO

## Bounded implementation

1. NEW TimedTextCanonicalSemantics (timeline-module/src/main/.../timeline/canonical):
   canonicalValue(TextElement) → deep-sorted typed structure;
   semanticFingerprint(TextElement) → canonical JSON;
   encodeElements(List<TextElement>) / decodeElements(String) → lossless typed.
2. CanonicalTimelineDiffCalculator: replace summary() with fingerprint for
   TEXT_ELEMENT_CHANGED before/after values; delete summary().
3. TimelinePatchApplier: add TEXT_ELEMENT_CHANGED branch — decode canonical
   payload → replace element in textElements collection (id-matched);
   deletion when afterValue is null.
4. TimelineCanonicalValidator (aggregate): duplicate TextElementId fail-closed
   (new diagnostic TIMELINE_TEXT_ELEMENT_ID_DUPLICATE).
5. Tests: TimedTextCanonicalSemanticsTest (A/B matrix), patch-level lossless
   test, real TimelineMergeEngine E2E (source-only/divergent/delete-vs-modify/
   delete-last), hash sensitivity, provider-plan independence, legacy
   classification documented.
