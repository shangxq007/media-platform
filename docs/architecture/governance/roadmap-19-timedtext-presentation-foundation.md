# Roadmap #19 — TimedText Presentation Foundation (ChatGPT Final-Review)

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Milestone: ROADMAP_19_TIMEDTEXT_PRESENTATION_FOUNDATION / TIMED_TEXT_PRESENTATION_FOUNDATION_V1

## Base / Candidate

- BASE_SHA = 72c148da52c054f90ae59d8d581b66ff5952f70d
- BASE_TREE = 1d1be2182c8300b6ab54cd12086975b9569e664c
- CANDIDATE_SHA = db8a05a3ac0ad04f5f388076420fe1621bfb7560
- CANDIDATE_TREE = dfcf64bd814543980c48bf6187894e65eeb9e987
- Ancestry: 72c148da → db8a05a3 (single commit, linear; no merge/rebase/squash)

## Frozen architecture contract

TIMED_TEXT_PRESENTATION_BOUNDED_ARCHITECTURE_CONTRACT_V1 (decision recovery PASS):
- TextElement + StyledText + exact FontRational time
- StyledText owns content + semanticRuns + styleRuns + paragraphStyle
- overlapping timing valid; duplicate TextElement IDs fail closed
- font: FontFamilyName = selection intent only; execution uses immutable
  ResolvedFontRun/ValidatedFontExecutionReference (historical never re-resolves)
- renderer/provider/runtime excluded from canonical semantics

## Repository reality findings (Phase A)

- Partial implementation existed: TextElement/TextElementId/TimelineDocument
  textElements + hash + diff detection + conflict detector
- Defects closed:
  D1 diff used content().hashCode() as semantic identity → replaced with
     TimedTextCanonicalSemantics fingerprint
  D2 TimelinePatchApplier had NO TEXT_ELEMENT_CHANGED application path → added
     (delegates decode to local authority)
  D3 no single local TimedText authority → NEW TimedTextCanonicalSemantics
  D4 no duplicate TextElement ID validation → TimelineDocument constructor
     fail-closed
  D5 TimelineCandidate (canonicalmodel) had no textElements field → engine
     pipeline could not see TimedText → added field + adapter extraction +
     snapshot converter wiring + merge write-back (absent==empty convention)

## Ownership model

- TextElement canonical value / fingerprint / encode/decode:
  TimedTextCanonicalSemantics (single local authority)
- Timeline collection/hash orchestration: TimelineDocument + TimelineContentDigester
- Diff orchestration: CanonicalTimelineDiffCalculator (delegates fingerprint)
- Patch orchestration: TimelinePatchApplier (delegates decode)
- Merge orchestration: TimelineMergeEngine (orchestrates; no StyledText internals)
- Cross-object validation: TimelineDocument duplicate-ID + TextElement invariants
- Renderer/provider lowering: operational adapters (unchanged)

## Font execution decision

- FONT_FAMILY_NAME_IS_SELECTION_INTENT_NOT_EXECUTION_IDENTITY_V1 = PASS
- FONT_EXECUTION_REQUIRES_IMMUTABLE_RESOLVED_FONT_RESOURCE_V1 = PASS
- TextShapingRequest consumes ResolvedFontInstance (not FontFamilyName)
- resolved runs frozen per TextElement contract ("historical never re-resolves")
- no silent host-font fallback; fallbackPolicy explicit enum

## Jackson codec review

- 24 font-text value types + TextElementId received @JsonCreator/@JsonProperty
  (bounded decode plumbing; param names verified == existing field names;
  no new authored fields; equals/hash unchanged; existing tests green)
- JACKSON_METADATA ≠ CANONICAL_SCHEMA_AUTHORITY: canonical TimedText semantics
  owned explicitly by TimedTextCanonicalSemantics.canonicalValue
- encode→decode→encode stable; validation enforced after decode

## Real TimelineMergeEngine E2E (F1-F6)

- f1SourceOnlyTimedTextMerge → MERGED with THEIRS semantics
- f2IdenticalBilateralNoFalseConflict → MERGED (no false conflict)
- f3DivergentSameElementConflict → CONFLICT
- f4DeleteVsModifyFailsClosed → fail-closed
- f5DeleteLastEmptyState → MERGED + canonical empty state (no resurrection)
- f6MixedSemanticFamiliesPreserved → TimedText + Effect + Transition +
  Automation all survive independently

## Decomposition gate

DECOMPOSITION_GATE = PASS
- TimedText local field semantics live in TimedTextCanonicalSemantics
- diff/patch central code delegate; no duplicated StyledText grammar
- MergeEngine orchestrates without understanding StyledText internals
- Effect/Transition/Automation local semantics untouched
- mixed E2E proves family preservation
- no generic SemanticComponent abstraction introduced

## Verification

- Targeted: TimedTextCanonicalSemanticsTest 15/15; TimedTextMergeEngineTest 6/6
- Whole repository: 918 suites / 7280 tests / 0 failures / 0 errors / 43 skipped
  (Δ vs 916/7260/43: +2 suites +20 tests, all additions)
- Guards: GCR1/GCR2/GCR2-CORRECTION/GCR5-GCR6/verifyTimelineEffectTransition
  (40 checks incl. G-R19-1..15)/jooqFoundation = 14 OK; Modulith PASS
- Gates: arch drift 224 PASS; map drift PASS; map determinism 3x byte-identical;
  bootJar PASS; pfirr1RemediationCheck PASS; credential scan 0; residue PASS
- V1_CHANGED = NO; JOOQ_CHANGED = NO; TIMELINE_V3 = 0

## Final FCV

ROADMAP_19_TIMEDTEXT_PRESENTATION_FINAL_FCV = PASS (38/38) — run against the
frozen candidate db8a05a3 before this publication.

## Deferred non-blocking

- advanced typography beyond current authored contract
- additional shaping/writing-system features
- richer text animation
- derived glyph/layout caches
- renderer implementation / execution validation
- package cleanup (canonicalmodel/app layering)
- #20 Render DAG / Artifact Graph / RenderExtent
- #22 Provider Fabric
