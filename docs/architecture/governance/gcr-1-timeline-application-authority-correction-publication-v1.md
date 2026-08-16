# GCR-1 TIMELINE APPLICATION AUTHORITY — CORRECTION PUBLICATION V1

## Status

ORIGINAL_GCR1_PUBLICATION_SHA = f6d5de145ea694ac5a5b3f4b46ce068946c6ab2f
ORIGINAL_FCV_SCOPE_DEFECT = path-based inventory failed to detect active Timeline application/canonical authority under render.app.timeline (TimelineMergeEngine, TimelineSemanticDiffService, TimelineCanonicalizer, InternalTimelineJson, revision persistence services). The original GCR-1 FCV did not cover these; this correction publishes the authority-oriented extraction.

GCR1 = REOPENED_FOR_CORRECTION → CORRECTION_COMPLETE (pending ChatGPT review)
GCR2_START_AUTHORIZED = NO
GCR5_GCR6_START_AUTHORIZED = NO
TIMED_TEXT_PRESENTATION_FOUNDATION_START_AUTHORIZED = NO
CHECKPOINT_A = NOT_READY
ROADMAP20_START_AUTHORIZED = NO

## Correction legal base

CORRECTION_BASE_SHA = f6d5de145ea694ac5a5b3f4b46ce068946c6ab2f
CORRECTION_BASE_TREE = 69e8c2435bef88ffce1fd98aa88482441baab116

## Correction implementation chain (append-forward, linear, history unrewritten)

CORE_CORRECTION_SHA = 80e4bc978a5f9437e68c81cb5d5c2e38f6d5b971 (authority extraction: 25 production types + TimelinePayloadCodec port + consolidation; manifest 72)
WIRING_FIX_SHA = 06b7f9b3f2f1a94bd90a6dc9a4f5b0f24c0f2a53 (composition root: @ComponentScan += timeline/operation packages; root cause = NoSuchBeanDefinitionException TimelinePatchService from test XML chain)
RESCAN_SHA = 3450f5b944b141f33b0e8d2d74b0a4e2c9f3c9d3 (§11 behavior rescan: revisioncommand authority — 9 types moved)
FINAL_CORRECTION_CANDIDATE_SHA = f62ce6b779bd95b4daf41dc135e0126e315f0027
FINAL_CORRECTION_CANDIDATE_TREE = 5672bf86d2259ff8a95beb33f5bc60484823e450
PUBLICATION_SHA = <PUB>
PUBLICATION_TREE = <PUBTREE>

## Authority manifest

AUTHORITY_MANIFEST_PATH = .agent-tasks/GCR1-CORRECTION-V1/GCR1_CORRECTION_AUTHORITY_MANIFEST.tsv
AUTHORITY_MANIFEST_TOTAL = 81
AUTHORITY_MANIFEST_MOVE = 41
AUTHORITY_MANIFEST_KEEP = 40
AUTHORITY_MANIFEST_SPLIT = 0
AUTHORITY_MANIFEST_DELETE = 0
AUTHORITY_MANIFEST_UNCLASSIFIED = 0
GCR1_AUTHORITY_MANIFEST_COMPLETENESS_RESCAN = PASS (behavior-oriented rescan found and corrected the revisioncommand surface; all other hits classified as documented render consumers)

## Timeline authority counters (machine-verified at candidate)

OUTSIDE_TIMELINE_MODULE_CANONICAL_TIMELINE_AUTHORITY_COUNT = 0
RENDER_TIMELINE_CANONICALIZATION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_SERIALIZATION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_CONTENT_HASH_AUTHORITY_COUNT = 0
RENDER_TIMELINE_SEMANTIC_DIFF_AUTHORITY_COUNT = 0
RENDER_TIMELINE_SEMANTIC_MERGE_AUTHORITY_COUNT = 0
RENDER_TIMELINE_PATCH_AUTHORITY_COUNT = 0
RENDER_TIMELINE_REVISION_AUTHORITY_COUNT = 0
RENDER_TIMELINE_AUTHORING_WRITE_AUTHORITY_COUNT = 0
TIMELINE_CANONICALIZATION_AUTHORITY_COUNT = 1
TIMELINE_SEMANTIC_DIFF_AUTHORITY_COUNT = 1
TIMELINE_SEMANTIC_MERGE_AUTHORITY_COUNT = 1
TIMELINE_REVISION_SEMANTIC_AUTHORITY = timeline-module only
TIMELINE_REVISION_PERSISTENCE_AUTHORITY = timeline-module adapter only
RENDER_REVISION_AUTHORITY = 0
RENDER_OWNED_TIMELINE_AUTHORITY_COUNT = 0

## Key ownership decisions (manifest + commit evidence)

- TimelineMergeEngine / TimelineSemanticDiffService / TimelineSemanticDiffV1Service / TimelineCanonicalizer / InternalTimelineJson / TimelineContentHasher / TimelineDocumentJsonSerializer / TimelineEntityIndex / TimelinePatchApplicationService / TimelinePatchOpsJson / TimelineRevisionService / TimelineRevisionSaveService / TimelineRevisionDiffService / TimelineRevisionRepository / ProductCurrentRevisionService / TimelineSourceReferenceValidator / TimelineCanonicalRejectionException / PatchApplyResult / PatchPreviewResult / TimelineRevisionLabelsJson / InternalTimelineCandidateAdapter / TimelineDocumentCandidateMapper / TimelinePatchService → timeline-module app (jOOQ: adapter)
- TimelineSnapshotService / TimelineRevisionRepository → timeline-module adapter (jOOQ; typed-schema adapter-layer dependency documented per directive §9; domain packages Spring/jOOQ-free)
- revisioncommand authority (§11 catch): RevisionGraphService / RevisionCommandApplyService / RevisionCommandPlanner + RevisionRef / RevisionCommandPlan(Digest) / RevisionCommandDefinitionId / RevisionCommandErrorCode / RevisionCommandException → timeline-module (adapter/app/revisioncommand)
- TimelinePatchService: split-consolidated (TimelineSpec projection conversion removed — zero callers proven; validation via TimelineCanonicalizer; no compatibility wrapper; no dual authority)
- TimelineRevisionService: depends on timeline-owned TimelinePayloadCodec port; RenderTimelinePayloadCodec implements at render boundary
- TimelineEditorSyncService: KEEP render (editor projection bridge, non-authoritative)
- InternalTimelineWriter / InternalTimelineAdapter / InternalTimelineValidationService / InternalTimelineMetadataEnricher / TimelineConversionService / BaseJobTimelineLoader: KEEP render (interchange/legacy-input layer; consume timeline-module)
- Render retained consumers (verified callers only): TimelineRevisionRenderService, RenderImpactAnalyzer, TimelineSpecResolver, RenderJob revision pinning, RenderCache consumers, RenderJob mapping, Segment/incremental render consumers, compile/execution services
- render/ir/CanonicalSerializer: KEEP render (Render IR canonical JSON, not Timeline canonical serialization)

## Tests

WHOLE_REPOSITORY_TEST_SUITES = 906
WHOLE_REPOSITORY_TESTS = 7155
WHOLE_REPOSITORY_FAILURES = 0
WHOLE_REPOSITORY_ERRORS = 0
WHOLE_REPOSITORY_SKIPPED = 43
TEST_DELTA_FROM_7155 = 0
TEST_DELTA_EXPLANATION = Pure authority migration: 14 tests moved render-module → timeline-module (timeline app/adapter unit suites; 3 reverted for render fixture/interchange deps; 1 revision-command IT kept in render for jOOQ test infra). No test deleted, no assertion weakened; global count identical.
TIMELINE_MODULE_TEST_RESULT = PASS (41 suites)
RENDER_MODULE_TEST_RESULT = PASS (331 suites)
PLATFORM_APP_CONTEXT_TEST_RESULT = PASS (incl. C1CrrMergeAuthorityCompositionTest, EnabledAdminSecurityTest)

## Gates

ARCHITECTURE_DRIFT = PASS (224)
MODULITH = PASS
MAP_DRIFT = PASS
MAP_DETERMINISM_RUN_1 = PASS
MAP_DETERMINISM_RUN_2 = PASS
MAP_DETERMINISM_RUN_3 = PASS
BOOTJAR = PASS
PFIRR1_REMEDIATION_CHECK = PASS
CREDENTIAL_SCAN = PASS (0 findings)
VERIFICATION_GUARDS = PASS (7/7: C1-TMC, CNM1-RED-01..14, CRR payload, R1-REISSUE-RED-01..06, P1, constructor-injection)
timeline-module → render-module dependency count = 0
timeline-module → provider runtime dependency count = 0
canonical Timeline semantic types under render authority = 0

## Semantic preservation

PREEXISTING_CANONICAL_SERIALIZATION_CHANGED = NO
TIMELINE_CONTENT_HASH_SEMANTICS_CHANGED = NO
SOURCE_BINDING_IMMUTABLE_PINNING = PASS
EXACT_MEDIA_TIME = PASS
TEXT_ELEMENT_SEMANTICS = PASS
SEMANTIC_DIFF = PASS
SEMANTIC_MERGE = PASS
TIMELINE_PATCH_BEHAVIOR = PASS (canonicalizer-gated validation)
9_TEXT_OPERATIONS = PASS
REVISION_GRAPH_BEHAVIOR = PASS
CURRENT_REVISION_CAS_BEHAVIOR = PASS
RENDER_REVISION_PINNING_BEHAVIOR = PASS

## Scope containment

GCR2_IMPLEMENTATION_CHANGE_COUNT = 0 (storage ContentDigest dependency retained as documented temporary GCR-2 debt)
GCR5_GCR6_IMPLEMENTATION_CHANGE_COUNT = 0
WORKFLOW_CANONICALIZATION_CHANGE_COUNT = 0
TIMED_TEXT_IMPLEMENTATION_CHANGE_COUNT = 0
ROADMAP20_IMPLEMENTATION_CHANGE_COUNT = 0
DB_SCHEMA_CHANGE = NO

## Blockers / escalation

GCR1_BLOCKERS = 0
ARCHITECTURE_ESCALATION = NONE

## Success criteria

GCR1_TIMELINE_DOMAIN_MODEL_EXTRACTION = PASS
GCR1_TIMELINE_APPLICATION_AUTHORITY_EXTRACTION = PASS
GCR1_TIMELINE_CANONICALIZATION_OWNERSHIP = PASS
GCR1_TIMELINE_SEMANTIC_DIFF_OWNERSHIP = PASS
GCR1_TIMELINE_MERGE_OWNERSHIP = PASS
GCR1_TIMELINE_REVISION_OWNERSHIP = PASS
GCR1_TIMELINE_REVISION_PERSISTENCE_OWNERSHIP = PASS
GCR1_CORRECTION_FINAL_FCV = PASS
GCR1 = CLOSED (pending ChatGPT review)
