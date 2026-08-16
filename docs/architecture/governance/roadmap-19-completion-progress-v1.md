---
type: architecture-governance-record
milestone: ROADMAP_19
name: ROADMAP_19_FONT_TEXT_FOUNDATION_COMPLETION_V1
status: IN_PROGRESS (2/3 blockers closed; BLOCKER B pending)
date: 2026-08-16
base: 401f4d90df39e1166d893028544d1556a535cdca
branch: agent/font-text-foundation-completion
---

# ROADMAP_19 COMPLETION — HONEST PROGRESS RECORD

## BLOCKER A — NINE TEXT OPERATIONS = CLOSED
- OperationDefinition.V1 15 → 24 (TargetKind.TEXT); 9 frozen typed operations:
  ADD_TEXT_ELEMENT, REMOVE_TEXT_ELEMENT, REPLACE_TEXT_CONTENT,
  SET_TEXT_STYLE_RANGE, SET_PARAGRAPH_STYLE, SET_FONT_SELECTION,
  SET_FONT_FALLBACK_POLICY, SET_VARIABLE_FONT_AXIS, SET_TEXT_LAYOUT.
- Typed OperationParameters records (zero Map<String,Object>); TextElementTargetRequest.
- TextOperationPlanner: typed request → plan-phase font resolution (catalog +
  opsz policy injected ONLY at plan) → frozen OperationPlan (materialized
  candidate Timeline + exact ResolvedFontRuns) → preview = frozen plan → atomic
  apply consumes candidate only. FONT_CATALOG_LOOKUP_DURING_APPLY_COUNT = 0 by
  structure (resolution inputs absent from apply path).
- AUTO opsz: fails closed without policy (AUTO_OPTICAL_SIZING_UNRESOLVED);
  resolves exact coordinate with policy. Invalid scalar ranges rejected;
  failed plans produce no partial mutation.
- Tests: TextOperationPlannerTest 9 PASS (typed contracts, frozen resolution,
  AUTO opsz, no-mutation-on-failure, hash change, preview immutability).

## BLOCKER C — TIMELINE TEXT DIFF/MERGE = CLOSED
- CanonicalTimelineSnapshot + textElements (backward-compatible convenience ctor).
- CanonicalTimelineDiffCalculator.diffTextElements → TEXT_ELEMENT_CHANGED
  (added/removed/changed; summary includes duration + content hash so divergent
  edits are never mistaken for identical).
- TimelineMergeConflictDetector: TEXT_ELEMENT_CONFLICT + TEXT_ELEMENT_DIVERGENCE;
  same-id divergent change → BLOCKING conflict; independent changes → merge;
  identical changes → merge; remove-vs-modify → conflict. TimelineMergeEngine
  remains sole merge authority (no parallel Text merge engine).
- Tests: TextElementDiffMergeTest 6 PASS (add/remove/change detection,
  independent merge, divergent conflict, remove-vs-modify, identical merge,
  U+00E9 vs U+0065 U+0301 distinct authored sequences).

## BLOCKER B — 77 RAW fontFamily RETIREMENT = IN_PROGRESS (NOT closed)
- Baseline verified: 77 occurrences / 38 files (render-module + platform-app).
- Classification completed (Phase A evidence):
  A. canonical/Timeline authority: TimelineTextOverlay, NormalizedCaptionLayer,
     InternalTimelineWriter:391 (hard-coded "DejaVu Sans" style write),
     TimelineNormalizationService, AutoCaptionsService/Controller,
     AssStyleMapper/AssStyleParams, CaptionTemplateTimelineAdapter (unshipped
     caption domain).
  B. provider external payload observation (Remotion/OTIO/font infrastructure:
     BasicFontStackResolver, RemotionInputPropsValidator/Generator/Serializer,
     OTIOTimelineCompiler, OTIOFontRef, FontAsset/FontMetadata,
     InMemoryFontAssetRepository, DefaultFontManifestResolver, etc.): raw family
     strings required at the external renderer boundary; must remain adapter
     observations, never canonical authority.
  C. test fixtures (CaptionedVideoExportE2ETest, OTIOTimelineCompilerTest,
     AssStyleMapperTest, etc.): migrate with target model.
- Migrated/deleted so far: 0 of 77 (this execution window exhausted before
  Blocker B migration; see next section for exact remaining work).

## REMAINING WORK (next execution)
1. TimelineTextOverlay.fontFamily String → FontFamilyName (typed; render-module
   already depends on font-text-module); migrate callers (TimelineNormalization
   Service, CaptionTemplateTimelineAdapter, fixtures).
2. NormalizedCaptionLayer.fontFamily → FontFamilyName.
3. InternalTimelineWriter:391: remove hard-coded "DejaVu Sans" canonical write.
4. AutoCaptionsService/Controller: String → FontFamilyName / FontSelectionIntent.
5. AssStyleMapper/AssStyleParams: typed FontFamilyName at domain boundary, raw
   ASS FontName only at the ASS adapter output (provider observation).
6. Provider adapters (Remotion/OTIO/font infra): annotate observation boundary,
   gate excludes provider payload keys, zero canonical leak.
7. Retire semantic aliases (String preferredFontFamily / fontName / family) —
   gate must detect aliases, not just literal fontFamily.
8. Final scan: RAW_FONT_FAMILY_STRING_AUTHORITY_FINAL = 0 (authority scope),
   PROVIDER_FONT_STRING_CANONICAL_LEAK_COUNT = 0, COMPATIBILITY_CODE_COUNT = 0.

## STATUS
- ROADMAP_19_BLOCKERS: 2 of 3 closed (A, C). B = IN_PROGRESS.
- FINAL_FCV = NOT_YET_VALID (honest; Blocker B not zero).
- No main integration performed in this cycle; branch + worktree preserved.
- No compatibility code; no Roadmap #20 implementation; zero schema changes.
