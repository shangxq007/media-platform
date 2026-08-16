# GCR-1 TIMELINE IMPORT / VALIDATION / AUTHORING AUTHORITY — CORRECTION PUBLICATION V2

## Status

ORIGINAL_GCR1_PUBLICATION_SHA = f6d5de145ea694ac5a5b3f4b46ce068946c6ab2f
GCR1_CORRECTION_V1_PUBLICATION_SHA = fa58312c53bb006a5fd519d2479cf3b087d4c604

GCR1 = REOPENED → CORRECTION_V2_COMPLETE (pending ChatGPT review)
GCR2_START_AUTHORIZED = ONLY_AFTER_CHATGPT_REVIEW
GCR5_GCR6_START_AUTHORIZED = NO
TIMED_TEXT_PRESENTATION_FOUNDATION_START_AUTHORIZED = NO
CHECKPOINT_A = NOT_READY
ROADMAP20_START_AUTHORIZED = NO

## Why V2 was required

Independent final review of the V1 correction found residual Timeline ingress
authority still implemented under render-module, and governance inconsistencies
between the V1 authority manifest (which said MOVE) and the V1 final
code/publication (which said KEEP):

1. `InternalTimelineValidationService` (render app.timeline) validated Internal
   Timeline Schema 1.0 documents — canonical/internal Timeline validation
   authority outside timeline-module. V1 manifest said MOVE; V1 publication
   said KEEP.
2. `InternalTimelineWriter` (render app.timeline) constructed canonical
   internal-1.0 JSON from render interchange types (TimelineSpec) — canonical
   authoring/write authority outside timeline-module.
3. `TimelineConversionService` (render app.timeline) converted editor/OTIO/
   legacy into canonical internal-1.0 through the render writer — canonical
   import/conversion authority outside timeline-module.
4. `TimelineValidationService` (render app) was the render-side validation
   entry used by the MCP controller.

Therefore the V1 claims

    OUTSIDE_TIMELINE_MODULE_CANONICAL_TIMELINE_AUTHORITY_COUNT = 0
    RENDER_TIMELINE_AUTHORING_WRITE_AUTHORITY_COUNT = 0

could not be accepted, and the V1 publication's self-referential
`PUBLICATION_SHA = <PUB>` placeholders were governance defects this V2
correction does not repeat.

## Legal base

CORRECTION_V2_BASE_SHA = fa58312c53bb006a5fd519d2479cf3b087d4c604
CORRECTION_V2_BASE_TREE = 61beb188c4c0b802b32637db20c824bba9c2d066

## Resolution design (frozen contract)

TIMELINE_INGRESS_AUTHORITY_CONTRACT_V1 (see
`.agent-tasks/GCR1-CORRECTION-V2/TIMELINE_INGRESS_AUTHORITY_CONTRACT_V1.md`):

External / Editor / OTIO / Legacy
  → render interchange parse (TimelineSpecResolver / TimelineScriptParser / OTIO adapters)
  → TimelineSpec + TimelineExtensions
  → TimelineSpecImportAdapter (render boundary; mechanical mapping only)
  → TimelineImportRequest (typed Timeline-owned contract)
  → TimelineImportService (timeline-module; canonical construction + E1b gate)
  → Canonical Timeline (internal-1.0 JSON)

- timeline-module now owns the SOLE production canonical validator
  (`InternalTimelineValidationService`, running the E1b canonical gate) and the
  SOLE canonical constructor (`TimelineImportService`, deepCanonicalize + write
  + E1b gate before returning).
- render keeps a boundary adapter (`TimelineSpecImportAdapter`, mechanical
  field mapping) and the `TimelinePayloadCodec` port implementation
  (`RenderTimelinePayloadCodec`); both delegate canonical construction to
  timeline-module.
- `TimelineConversionService` (render) remains as an application boundary
  coordinator: interchange resolution + delegation; RENDER_TO_CANONICAL_
  TIMELINE_CONVERSION_AUTHORITY = 0.
- No timeline-module → render-module dependency (verified by guard).
- No compatibility path, no dual validator, no dual writer, no V1/V2 parallel
  model (greenfield policy).

## Implementation chain (append-forward, linear, history unrewritten)

CANDIDATE_SHA = f740c55b3a28c5ab1e310b96a0367dec0eacc612
CANDIDATE_TREE = 0ec09d3adc126b852e6438eccae4b0725e966430

PUBLICATION_SHA = see gcr-1-timeline-import-validation-authority-correction-v2-publication-record.md
  (recorded append-only in the publication-record commit; not self-referenced here)

## Resolved residual authorities

InternalTimelineValidationService = MOVE → timeline-module `InternalTimelineValidationService`
  (E1b canonical gate; sole production canonical validator; render wrapper deleted)
InternalTimelineWriter = SPLIT → timeline-module `TimelineImportService` (canonical
  construction + gate) + render `TimelineSpecImportAdapter` (boundary mapping)
TimelineConversionService = KEEP render as BOUNDARY_COORDINATOR (delegates canonical
  construction to TimelineImportService; zero canonical authority)
TimelineValidationService = DELETE (McpMediaToolsController uses timeline-owned validator)
InternalTimelineAdapter = KEEP render (downstream canonical→TimelineSpec projection)
InternalTimelineMetadataEnricher = KEEP render (representation-level metadata enrichment;
  E1b non-semantic fields; timeline-owned serializer)
InternalTimelineToEditorConverter = KEEP render (canonical→editor v2 projection)
TimelineEditorSyncService = KEEP render (editor boundary coordinator; delegates conversion,
  revision mutation, snapshots to timeline-owned services)
BaseJobTimelineLoader = KEEP render (RENDER_DOWNSTREAM_TIMELINE_CONSUMER)
TimelineSpecResolver = KEEP render (interchange resolution; no canonical construction)
TimelineScriptParser = KEEP render (external/interchange parser)
RenderTimelinePayloadCodec = KEEP render (port implementation at boundary; delegates
  canonical construction to TimelineImportService)
TimelineSpecImportAdapter = NEW render (boundary adapter, mechanical mapping)
TimelineImportRequest = NEW timeline-module (typed Timeline-owned import contract)
TimelineImportService = NEW timeline-module (sole canonical constructor + E1b gate)

## Authority manifest

AUTHORITY_MANIFEST_PATH = .agent-tasks/GCR1-CORRECTION-V2/GCR1_CORRECTION_V2_AUTHORITY_MANIFEST.tsv
AUTHORITY_MANIFEST_TOTAL = 57
AUTHORITY_MANIFEST_KEEP = 54
AUTHORITY_MANIFEST_MOVE = 1
AUTHORITY_MANIFEST_SPLIT = 1
AUTHORITY_MANIFEST_DELETE = 1
AUTHORITY_MANIFEST_UNCLASSIFIED = 0
AUTHORITY_MANIFEST_FINAL_LOCATION_MISMATCH = 0
AUTHORITY_MANIFEST_FINAL_AUTHORITY_MISMATCH = 0
MANIFEST_FINAL_LOCATION_VERIFIED = PASS (all non-deleted rows resolve to final files;
  DELETE rows verified absent)
MANIFEST_FINAL_AUTHORITY_VERIFIED = PASS (CANONICAL_AUTHORITY=YES rows all in timeline-module
  or deleted; render rows all CANONICAL_AUTHORITY=NO)

## Authority counters (machine-verified at candidate)

OUTSIDE_TIMELINE_MODULE_CANONICAL_TIMELINE_AUTHORITY_COUNT = 0
OUTSIDE_TIMELINE_MODULE_INTERNAL_TIMELINE_VALIDATION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_CANONICALIZATION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_SERIALIZATION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_CONTENT_HASH_AUTHORITY_COUNT = 0
RENDER_TIMELINE_VALIDATION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_AUTHORING_WRITE_AUTHORITY_COUNT = 0
RENDER_TO_CANONICAL_TIMELINE_CONVERSION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_SEMANTIC_DIFF_AUTHORITY_COUNT = 0
RENDER_TIMELINE_SEMANTIC_MERGE_AUTHORITY_COUNT = 0
RENDER_TIMELINE_PATCH_AUTHORITY_COUNT = 0
RENDER_TIMELINE_REVISION_AUTHORITY_COUNT = 0
TIMELINE_CANONICALIZATION_AUTHORITY_COUNT = 1
TIMELINE_SEMANTIC_DIFF_AUTHORITY_COUNT = 1
TIMELINE_SEMANTIC_MERGE_AUTHORITY_COUNT = 1
TIMELINE_TO_RENDER_DEPENDENCY_COUNT = 0
DEEP_CANONICALIZE_OUTSIDE_TIMELINE_MODULE_COUNT = 0

## Tests

WHOLE_REPOSITORY_TEST_SUITES = 905
WHOLE_REPOSITORY_TESTS = 7157
WHOLE_REPOSITORY_FAILURES = 0
WHOLE_REPOSITORY_ERRORS = 0
WHOLE_REPOSITORY_SKIPPED = 43
TEST_DELTA = +2 tests / -1 suite (vs V1 baseline 906 suites / 7155 tests)
TEST_DELTA_EXPLANATION = 4 deleted render tests (-7 test methods: render writer x3 +
  render TimelineValidationServiceTest) replaced by 3 new authority suites (+9 test
  methods: TimelineImportServiceTest x4, InternalTimelineValidationServiceTest x3
  [moved with the authority], TimelineConversionServiceDelegationTest x2).
  No test deleted without accounting; no assertion weakened.

TIMELINE_MODULE = PASS (incl. new TimelineImportServiceTest,
  InternalTimelineValidationServiceTest — construction/validation authority tests moved
  from render with the authority)
RENDER_MODULE = PASS (incl. TimelineConversionServiceDelegationTest — behavioral proof
  that conversion reaches the timeline-owned gate; writer tests moved to timeline-module)
PLATFORM_APP_CONTEXT = PASS (incl. C1CrrMergeAuthorityCompositionTest)

## Gates

ARCHITECTURE_DRIFT = PASS (all checks)
MODULITH = PASS (ModulithDocumentationGenerationTest green; 91 generated map files)
MAP_DRIFT = PASS (41 modules / 23 containers / 3 deployment units; 0 failures)
MAP_DETERMINISM = PASS (RUN_1/RUN_2/RUN_3 byte-identical, SHA f3ae8d60f42cdaac467d9f930d692452b3733e755b60f38b4f72f0ae9a530ee1)
BOOTJAR = PASS (platform-app.jar built)
PFIRR1 = PASS (pfirr1RemediationCheck; OIDC-only production auth verified)
CREDENTIAL_SCAN = PASS (0 findings on full staged change set)
GCR1_V2_AUTHORITY_GUARDS = PASS (verifyGcr1CorrectionV2IngressAuthority + existing
  verifyC1* guards; render validator/writer absent; timeline sole constructor+validator;
  no deepCanonicalize outside timeline-module; no timeline→render dep)
MANIFEST_REALITY_CHECK = PASS (57 rows; 0 unclassified; 0 location mismatch; 0 authority mismatch)

## Semantic preservation

PREEXISTING_CANONICAL_SERIALIZATION_CHANGED = NO
TIMELINE_CONTENT_HASH_SEMANTICS_CHANGED = NO
SOURCE_BINDING_IMMUTABLE_PINNING = PASS
EXACT_MEDIA_TIME = PASS
TEXT_ELEMENT_SEMANTICS = PASS
AUDIO_MIX_SEMANTICS = PASS
SEMANTIC_DIFF = PASS
SEMANTIC_MERGE = PASS
TIMELINE_PATCH_BEHAVIOR = PASS
REVISION_GRAPH_BEHAVIOR = PASS
CURRENT_REVISION_CAS_BEHAVIOR = PASS
RENDER_REVISION_PINNING_BEHAVIOR = PASS
9_TEXT_OPERATIONS = PASS

## Scope containment

DB_SCHEMA_CHANGE = NO
GCR2_IMPLEMENTATION_CHANGE_COUNT = 0
GCR5_GCR6_IMPLEMENTATION_CHANGE_COUNT = 0
WORKFLOW_CANONICALIZATION_CHANGE_COUNT = 0
TIMED_TEXT_IMPLEMENTATION_CHANGE_COUNT = 0
ROADMAP20_IMPLEMENTATION_CHANGE_COUNT = 0

## Blockers / escalation

GCR1_BLOCKERS = 0
ARCHITECTURE_ESCALATION = NONE

## Final FCV

GCR1_TIMELINE_DOMAIN_MODEL_EXTRACTION = PASS
GCR1_TIMELINE_APPLICATION_AUTHORITY_EXTRACTION = PASS
GCR1_TIMELINE_CANONICALIZATION_OWNERSHIP = PASS
GCR1_TIMELINE_VALIDATION_OWNERSHIP = PASS
GCR1_TIMELINE_IMPORT_CONVERSION_OWNERSHIP = PASS
GCR1_TIMELINE_AUTHORING_WRITE_OWNERSHIP = PASS
GCR1_TIMELINE_SEMANTIC_DIFF_OWNERSHIP = PASS
GCR1_TIMELINE_SEMANTIC_MERGE_OWNERSHIP = PASS
GCR1_TIMELINE_REVISION_OWNERSHIP = PASS
GCR1_TIMELINE_REVISION_PERSISTENCE_OWNERSHIP = PASS
GCR1_REVISION_COMMAND_OWNERSHIP = PASS
GCR1_CORRECTION_V2_FINAL_FCV = PASS (25/25 machine checks at candidate f740c55b)

## Proposed status — NOT self-authorized

GCR1 = CLOSED (pending ChatGPT final review)
GCR2_START_AUTHORIZED = ONLY_AFTER_CHATGPT_REVIEW
GCR5_GCR6_START_AUTHORIZED = NO
TIMED_TEXT_PRESENTATION_FOUNDATION_START_AUTHORIZED = NO
CHECKPOINT_A = NOT_READY
ROADMAP20_START_AUTHORIZED = NO
