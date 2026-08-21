# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I1 RESTORE RESPONSE SEMANTICS CORRECTION

## Publication record

| Field | Value |
|---|---|
| PREDECESSOR_SHA | 70851ff85b920d198352828006733c58569eaf9c |
| PREVIOUS_IMPLEMENTATION_SHA | 6dc38ca4e45591f8638d344411987bc27b7b7b6f |
| RESPONSE_CORRECTION_SHA | def02b7d485d2fa0e2638f45a5915a146dcf95fb |
| RESPONSE_CORRECTION_TREE | 0460456a42110f2b010fa0462c18e52d67d5a458 |
| PUBLICATION_SHA | (set after commit) |
| PUBLICATION_TREE | (set after commit) |
| FCV_COMMIT_SHA | def02b7d485d2fa0e2638f45a5915a146dcf95fb |
| FCV_TREE_SHA | 0460456a42110f2b010fa0462c18e52d67d5a458 |
| FCV_BUILD_INPUT | COMMITTED_TREE |
| FCV_WORKTREE_DIRTY | FALSE |
| BUG | editorTimelineJson incorrectly aliased internalTimelineJson |
| CORRECTION | editorTimelineJson now produced through TimelinePayloadCodec.toEditorJson |
| CANONICAL_RESTORE_TRANSACTION_CHANGED | NO |
| LEGACY_WRITE_AUTHORITY_CHANGED | NO |
| LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT | 0 |
| CANONICAL_RESTORE_AUTHORITY_COUNT | 1 |
| RESTORE_RESPONSE_EDITOR_TIMELINE_SEMANTICS | PASS |
| RESTORE_RESPONSE_INTERNAL_TIMELINE_SEMANTICS | PASS |
| RESTORE_RESPONSE_FIELD_ALIASING_COUNT | 0 |
| I2 | NOT_STARTED |
| I3 | NOT_STARTED |
| ROADMAP_21 | NOT_STARTED |
| ROADMAP_22 | NOT_STARTED |
| ARCHITECTURE_ESCALATION | NONE |

## Bug

TimelineRevisionController.toRestoreResponse populated both
editorTimelineJson and internalTimelineJson with the same internal payload:

    editorTimelineJson   = internalTimelineJson (same string)
    internalTimelineJson = internalTimelineJson

The pre-I1 implementation correctly projected editorTimelineJson through
payloadCodec.toEditorJson(payload). The I1 rewrite regressed this read-side
semantics (the canonical restore WRITE itself was correct).

## Correction

TimelineRevisionController now injects TimelinePayloadCodec (the accepted
Timeline-owned projection port; RenderTimelinePayloadCodec implements it at the
Render boundary):

- internalTimelineJson = restored revision's internal timeline payload
  (read via retained query projection getRevisionSnapshotPayload)
- editorTimelineJson   = timelinePayloadCodec.toEditorJson(internalPayload)
- field aliasing        = 0 (editor and internal are distinct values)

Fail-closed policy: if canonical restore succeeded but the restored revision's
detail or internal payload cannot be read, an IllegalStateException is thrown
(RESTORE_RESPONSE_INCOMPLETE) instead of returning semantically incomplete
success. Canonical restore success guarantees both objects exist.

## Scope

Production changes:
- platform-app/.../TimelineRevisionController.java (inject TimelinePayloadCodec;
  toRestoreResponse editor projection; fail-closed missing-state)

Test changes:
- TimelineRevisionControllerRestoreResponseTest (new, 3 tests)
- TimelineMergeControllerTest / TimelineRevisionRenderJobStatusControllerTest
  (ctor sync for TimelinePayloadCodec injection)

Untouched: TimelineRevisionSaveService, TimelineRevisionService,
TimelineEditorSyncService, RenderController, build.gradle.kts, canonical models,
DB schema, GCR2 gate semantic direction.

## Tests

TimelineRevisionControllerRestoreResponseTest:
- restoreResponseCarriesDistinctInternalAndEditorProjection: internal payload =
  fixture; editorTimelineJson = EDITOR_PROJECTION (distinct); non-alias asserted
- editorProjectionIsProducedThroughTimelinePayloadCodec: verify(codec)
  .toEditorJson(INTERNAL_FIXTURE) — exact restored payload supplied
- restoreUsesCanonicalSaveServiceExactlyOnce: restoreRevision called exactly
  once with expectedCurrent from ProductCurrentRevisionService; legacy restore
  absent by construction

Cfrhi1LegacyWriteAuthorityGuardTest: remains GREEN (write authority 0,
canonical restore authority 1, service retained).

## FCV (from clean committed correction tree 0460456a)

- git status --porcelain --untracked-files=all = EMPTY
- full suite: 7586 tests, 0 failures, 0 errors, 43 skipped — PASS
  (./gradlew test, clean committed tree, 11m37s)
- verifyGcr2ArtifactAuthority: PASS (canonical pin authority unchanged)
- pfirr1RemediationCheck: PASS
- verifyC1Cnm1RedGates: PASS (C1-CNM1-RED-01..13)
- jooqFoundationCheck: PASS
- verifyTimelineEffectTransitionCanonicalization: PASS
- :render-module:verifyC20RenderPlanBoundaryGuard: PASS (55 files)
- :platform-app:bootJar: PASS

## I1 postconditions (unchanged)

LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT = 0
LEGACY_RECORD_REVISION_AUTHORITY_COUNT = 0
LEGACY_AI_ADOPT_WRITE_AUTHORITY_COUNT = 0
LEGACY_RESTORE_WRITE_AUTHORITY_COUNT = 0
LEGACY_BACKFILL_WRITE_AUTHORITY_COUNT = 0
CANONICAL_RESTORE_AUTHORITY_COUNT = 1
AI_EDITING = RETAINED
LOSSY_AI_CANONICAL_PERSISTENCE_COUNT = 0

READY_FOR_CHATGPT_CFRH_I1_RESPONSE_CORRECTION_REVIEW
