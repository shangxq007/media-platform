---
type: architecture-governance-record
milestone: ROADMAP_19
name: ROADMAP_19_MANDATORY_REVIEW_CORRECTION_V1
status: CLOSED
date: 2026-08-16
base: fc3e428d68773f9ac537ba1acb80a869ccff172c
previous_completion_publication: fc3e428d68773f9ac537ba1acb80a869ccff172c
independent_review: NOT_PASS -> correction applied -> re-review pending
---

# ROADMAP_19 MANDATORY REVIEW CORRECTION — PUBLICATION

## CORR-1 — TIMELINEPATCH TEXT PRESERVATION = CLOSED
Independent review found real canonical-state corruption: generic snapshot
rebuilds silently erased TextElement state (textElements = List.of()).
Audit of ALL 10 reconstruction paths in TimelinePatchApplier found 3 remaining
leaks (final apply rebuild, applyMetadata, withTracks) — all fixed to propagate
current.textElements() / s.textElements() exactly. Zero leak paths remain.
New TimelinePatchTextPreservationTest (6 cases): duration/metadata/track/
output-profile/final-rebuild/content-semantics patches preserve a non-empty
TextElement exactly (identity + semantics).
TIMELINE_PATCH_NON_TEXT_OPERATION_TEXT_PRESERVATION = PASS
TIMELINE_PATCH_TEXT_ELEMENT_SILENT_DROP_COUNT = 0

## CORR-2 — FONT SELECTION SOLE AUTHORITY = CLOSED
- TimelineTextOverlay.of() no longer invents FontFamilyName("DejaVu Sans"):
  font selection is an explicit caller input (required, never implicit).
- CaptionTemplateTimelineAdapter: missing template font family FAILS CLOSED.
- SrtSubtitleAdapter/WebVttSubtitleAdapter parse() require explicit import
  font policy; null/blank content returns empty without inventing semantics.
- McpMediaToolsController /import_srt: explicit fontFamily request field,
  missing -> 400 (no implicit default).
- 60+ test/fixture call sites migrated to explicit typed font selection.
FONT_SELECTION_INTENT_SOLE_AUTHORITY = YES
PARALLEL_FONT_SELECTION_AUTHORITY_COUNT = 0
IMPLICIT_FONT_SELECTION_DEFAULT_COUNT = 0
TIMELINE_TEXT_OVERLAY_IMPLICIT_FONT_DEFAULT_COUNT = 0
SRT_INVENTED_FONT_SEMANTICS_COUNT = 0
ASS_CANONICAL_TEXT_STYLE_AUTHORITY_COUNT = 0
PROVIDER_FONT_STRING_CANONICAL_LEAK_COUNT = 0

## TIMELINE AUTHORITY
CANONICAL_TIMELINE_AUTHORING_AUTHORITY = TimelineDocument (TextElement)
CanonicalTimelineSnapshot = deterministic diff/merge/revision projection
TimelineSpec = execution/interchange DTO (projection; not parallel authoring
authority; no invented font semantics)
PARALLEL_TIMELINE_AUTHORING_AUTHORITY_COUNT = 0

## GREENFIELD RESIDUE
COMPATIBILITY_CONSTRUCTOR_COUNT = 0
CANONICAL_TIMELINE_SNAPSHOT_COMPATIBILITY_CONSTRUCTOR_COUNT = 0
COMPATIBILITY_WRAPPER_COUNT = 0
DEPRECATED_ALIAS_COUNT = 0
DUAL_READ_COUNT = 0
DUAL_WRITE_COUNT = 0
SEMANTIC_FALLBACK_COUNT = 0
ACTIVE_PARALLEL_AUTHORITY_COUNT = 0

## REGRESSION
ALL_9_TEXT_OPERATIONS = PASS (TextOperationPlannerTest 10)
TEXT_DIFF_MODEL = PASS; TEXT_MERGE_MODEL = PASS
TIMELINE_MERGE_AUTHORITY = TimelineMergeEngine
PREEXISTING_TIMELINE_CANONICAL_BYTES_STABLE = YES (NON_EMPTY textElements)
TEXT_SEMANTIC_CHANGE_AFFECTS_HASH = YES

## TESTS
FULL_SUITE = all modules GREEN (0 failures, 0 errors; platform-app + render +
font-text + all module suites executed)
ARCHITECTURE_DRIFT = 224/224; MODULITH = PASS; BOOTJAR = PASS; PFIRR1 = PASS
TRACKED_CREDENTIAL_RESIDUE_FINAL_NUMERIC_ZERO = YES (scan 0; UI masking
presentation-only)

## SCOPE
Zero #20 implementation; zero schema changes; zero compatibility code.
ROADMAP_20_START_AUTHORIZED = NO (until re-review passes).

## CHAIN
fc3e428d -> 920958c3 (CORR-1 + CORR-2) -> d008cdb9 (Modulith web->fonttext +
SRT import policy) -> correction publication.
