# ROADMAP #19 — FINAL TIMELINE AUTHORING AUTHORITY CANONICALIZATION — PUBLICATION V1

Status: FINAL
Directive: ROADMAP_19_FINAL_TIMELINE_AUTHORING_AUTHORITY_CANONICALIZATION_V2
Execution mode: AGGRESSIVE GREENFIELD (GREENFIELD_CANONICALIZATION_OVER_COMPATIBILITY_V1,
NO_HISTORICAL_COMPATIBILITY_BURDEN_V1, ONE_CANONICAL_MODEL_NO_COMPATIBILITY_TRACKS_V1,
OBSOLETE_UNSHIPPED_DESIGNS_ARE_DELETED_NOT_DEPRECATED_V1,
INTERNAL_HISTORY_LIVES_IN_GIT_NOT_ACTIVE_ARCHITECTURE_V1,
NO_PARALLEL_LEGACY_AND_CANONICAL_AUTHORITY_V1)

## Previous publication

PREVIOUS_PUBLICATION_SHA = 690cfee26ee2653177ff37b59a03524495d81745
PREVIOUS_PUBLICATION_TREE = f032b688afa5fd869c039290c54a35335a54d6ac
INDEPENDENT_RE_REVIEW = NOT_PASS

## Root causes

1. PRODUCTION_IMPLICIT_FONT_SELECTION_DEFAULTS_REMAIN:
   production paths invented `FontFamilyName("DejaVu Sans")` when font
   semantics were absent (BasicTimelineEditor.applyAddCaption, caption
   template render API mapper, FontStyleSpec.defaults, ASS projection
   resolver). Missing font must fail closed, never be invented.

2. TIMELINESPEC_BASICEDITOR_PARALLEL_AUTHORING_AUTHORITY_REMAINS:
   BasicTimelineEditor.apply(TimelineSpec, TimelineEditRequest) performed
   sequential semantic edits and produced a new TimelineSpec — a parallel
   canonical authoring path alongside TimelineDocument + OperationPlan.

## Final canonical resolution

CANONICAL_TIMELINE_AUTHORING_AUTHORITY = TimelineDocument
CANONICAL_MEDIA_MUTATION_BOUNDARY = OperationPlan
FONT_SELECTION_INTENT_SOLE_AUTHORITY = FontSelectionIntent (font-text-module)

Canonical authoring stack (sole path):

    Application / UI / MCP / API / Workflow / Agent / Recipe
        -> typed OperationRequest
        -> resolve
        -> frozen OperationPlan
        -> authorize / preview / atomic apply
        -> TimelineDocument
        -> new immutable Timeline Revision

Allowed non-authoritative projections:

    TimelineDocument -> execution projection / interchange DTO
        -> TimelineSpec (EXECUTION/INTERCHANGE_PROJECTION_DTO)
        -> Render planning / provider adapters

Forbidden (now structurally absent):

    TimelineSpec -> BasicTimelineEditor -> semantic mutation

## Correction chain

690cfee2 (base)
  -> 1ed76404  ROADMAP_19 FINAL TIMELINE AUTHORITY CANONICALIZATION:
               delete parallel authoring path + zero invented font semantics
  -> b210b28d  zero remaining invented font defaults + non-empty patch rebuild
               coverage
  -> 277317c2  caption render contract fails closed without explicit font
               selection
  -> 77507a70  projection-only semantics for TimelineSpec/TimelineTextOverlay
               docs
  -> CANDIDATE 77507a70fc26fc5f5c9459a877c091d1b394e01b
     TREE      068bbb9c3d37705a8e834d8825218f5e4a65bb63
  -> PUBLICATION_SHA (this document, appended below)

## DELETED types

Entire `domain/timeline/editing` package (parallel authoring authority):

- BasicTimelineEditor
- BasicTimelineValidator
- TimelineEditOperation
- TimelineEditOperationType
- TimelineEditRequest
- TimelineEditResult
- TimelineEditResultStatus
- TimelineValidationIssue
- TimelineValidationIssueCode
- TimelineValidationIssueSeverity
- TimelineValidationStatus

Deleted obsolete tests:

- BasicTimelineEditingModelTest
- VS0 Stage 1 TimelineEditStage (edit path)
- timelineEditNoRawCommands

## MIGRATED callers

- InternalScenarioDefinition: removed `editOperations` field (100% dead —
  every scenario definition carried an empty edit list).
- InternalScenarioRunner: removed edit branch + BasicTimelineValidator call.
- FFmpegLibassBasicRenderPlanner: removed validator dependency (structural
  invariants enforced by TimelineSpec compact constructor).
- Vs0VerticalSliceIntegrationTest / VS0VerticalSliceIntegrationTest: full
  vertical slice flow migrated to caption-template projection path only.
- CaptionTemplateRenderApiMapper / CaptionTemplateRenderContractValidator /
  AssStyleMapper / FontStyleSpec: missing font family now FAILS CLOSED at the
  contract boundary (400) instead of inventing a platform font.

## KEPT projection DTOs

- TimelineSpec = EXECUTION_PROJECTION_DTO / INTERCHANGE_PROJECTION_DTO
  (render planning, provider adapters, OTIO interchange; JavaDoc and
  semantics updated — NOT a canonical authoring authority, not persisted as
  canonical Timeline revision state, not revision/merge/hash authority, no
  font-selection or mutation semantics).
- TimelineTextOverlay = RENDER_PROJECTION_DTO
  (fontFamily is derived projection data from authored FontSelectionIntent,
  never an independent authoring font-selection authority).

## Zero residue counters (final)

CANONICAL_TIMELINE_AUTHORING_AUTHORITY_COUNT = 1
PARALLEL_TIMELINE_AUTHORING_AUTHORITY_COUNT = 0
PARALLEL_TIMELINE_MUTATION_PATH_COUNT = 0
TIMELINE_SPEC_CANONICAL_AUTHORITY_COUNT = 0
TIMELINE_TEXT_OVERLAY_AUTHORING_AUTHORITY_COUNT = 0
BASIC_TIMELINE_EDITOR_PRODUCTION_REFERENCE_COUNT = 0
LEGACY_TIMELINE_EDIT_AUTHORING_TYPE_COUNT = 0
PRODUCTION_INVENTED_FONT_SELECTION_COUNT = 0
IMPLICIT_FONT_SELECTION_DEFAULT_COUNT = 0
INTERNAL_TIMELINE_ADAPTER_INVENTED_FONT_SEMANTICS_COUNT = 0
COMPATIBILITY_CONSTRUCTOR_COUNT = 0
CANONICAL_TIMELINE_SNAPSHOT_COMPATIBILITY_CONSTRUCTOR_COUNT = 0
COMPATIBILITY_WRAPPER_COUNT = 0
DEPRECATED_ALIAS_COUNT = 0
LEGACY_API_COUNT = 0
DUAL_READ_COUNT = 0
DUAL_WRITE_COUNT = 0
SEMANTIC_FALLBACK_COUNT = 0
ACTIVE_PARALLEL_AUTHORITY_COUNT = 0

## Validation summary

ROADMAP_19_FINAL_AUTHORITY_CORRECTION_CANDIDATE_SHA =
77507a70fc26fc5f5c9459a877c091d1b394e01b
ROADMAP_19_FINAL_AUTHORITY_CORRECTION_CANDIDATE_TREE =
068bbb9c3d37705a8e834d8825218f5e4a65bb63

FULL_SUITE_TEST_COUNT = 7169
XML_FAILURES = 0
XML_ERRORS = 0
ARCHITECTURE_DRIFT = ALL PASS
MODULITH = PASS
BOOTJAR = PASS
PFIRR1 = PASS
CI_EQUIVALENT = PASS (podman-hermetic cleanTest test, 16m34s)
TRACKED_CREDENTIAL_RESIDUE_FINAL_NUMERIC_ZERO = YES

ROADMAP_19_FINAL_AUTHORITY_CORRECTION_FCV = PASS
ROADMAP_19_BLOCKERS = 0
ARCHITECTURE_ESCALATION = NONE
GOVERNANCE_ESCALATION = NONE
ROADMAP_20_START_AUTHORIZED = NO

Next action only: MANDATORY_CHATGPT_FINAL_REVIEW_AFTER_ROADMAP_19
