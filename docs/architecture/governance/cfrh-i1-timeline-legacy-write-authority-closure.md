# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I1 TIMELINE LEGACY WRITE AUTHORITY CLOSURE

## Publication record

| Field | Value |
|---|---|
| BASE_SHA | 1b573cc67bfdfb457c2dfb4081af03b46851dd6d |
| BASE_TREE | 6e3390c5f8f03861f0954c06fd4d465cb87d71cf |
| IMPLEMENTATION_SHA | 6dc38ca4e45591f8638d344411987bc27b7b7b6f |
| IMPLEMENTATION_TREE | 116b0ef8129c51802cb52259a093e3d331f0b8b5 |
| PUBLICATION_SHA | 216798968cbf44fd8768f7ad7a4a74f1e34eddcb |
| PUBLICATION_TREE | 18a3be0843b45cc8e5d65e41147c1bcc4af0663a |
| FCV_COMMIT_SHA | 6dc38ca4e45591f8638d344411987bc27b7b7b6f |
| FCV_TREE_SHA | 116b0ef8129c51802cb52259a093e3d331f0b8b5 |
| FCV_BUILD_INPUT | COMMITTED_TREE |
| FCV_WORKTREE_DIRTY | FALSE |
| LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT | 0 |
| LEGACY_RECORD_REVISION_AUTHORITY_COUNT | 0 |
| LEGACY_AI_ADOPT_WRITE_AUTHORITY_COUNT | 0 |
| LEGACY_RESTORE_WRITE_AUTHORITY_COUNT | 0 |
| LEGACY_BACKFILL_WRITE_AUTHORITY_COUNT | 0 |
| recordRevision | DELETED |
| recordAiAdoptRevision | DELETED |
| legacy restore | REWIRED_TO_CANONICAL |
| backfill | DELETED |
| TimelineRevisionService | RETAINED_FOR_I2_QUERY_CLOSURE |
| AI_EDITING | RETAINED |
| AI_LEGACY_PERSISTENCE | DELETED |
| I2 | NOT_STARTED |
| I3 | NOT_STARTED |
| ROADMAP_21 | NOT_STARTED |
| ROADMAP_22 | NOT_STARTED |
| ARCHITECTURE_ESCALATION | NONE |

## Implementation summary

CFRH-I1 closed all four legacy timeline semantic-write authorities (frozen
dispositions from the reviewed execution contract, ChatGPT-authorized):

1. **restore** → TimelineRevisionController now calls
   TimelineRevisionSaveService.restoreRevision with expectedCurrentRevisionId
   from ProductCurrentRevisionService (canonical CAS authority). The canonical
   R4-D1 single-transaction restore reissues the verified historical payload,
   semantic context, Effect snapshot reference, and artifact pins
   (copyRevisionPinsTx) — no legacy fallback, no dual authority.

2. **backfillHeadFromLatestSnapshot** → deleted from TimelineRevisionService;
   TimelineEditorSyncService.pullByProject no longer creates a revision merely
   because HEAD is absent (existing fallthrough to latest snapshot retained).

3. **recordRevision** (editor-sync) → TimelineEditorSyncService.push is now a
   non-authoring conversion preview; sync method family (3 overloads +
   resolveSyncSource) and saveSnapshotEnsuringInternal deleted; /sync endpoint
   and SyncRequest/SyncResponse removed; TimelineSnapshotController persists
   directly via canonical TimelineSnapshotService.

4. **recordAiAdoptRevision** → RenderController adopt endpoint retains its
   separable proposal-resolve semantics; legacy revision persistence removed
   (LOSSLESS_MIGRATION_PROOF = FAIL — AI path can author transitions/
   automations the canonical TimelineDocument cannot carry losslessly).
   shouldPersistAiRevision and AiProposalResolveRequest.persistRevision removed.
   AI editing/preview capability fully retained.

5. **Dead write helpers** → TimelineRevisionService lost recordRevision ×2,
   recordAiAdoptRevision, restore, backfillHeadFromLatestSnapshot,
   RestoreResult, parseInternalRevision; artifactPinValidator/artifactPinService
   fields and ctor params removed. All I2 query/projection methods retained;
   class retained for CFRH-I2 query closure.

## Guard

Cfrhi1LegacyWriteAuthorityGuardTest (timeline-module, 3 tests):
- legacySemanticWriteAuthorityIsZero: production-wide scan (comment-aware) of
  the four forbidden symbols = 0
- canonicalRestoreAuthorityIsActive: TimelineRevisionSaveService.restoreRevision
  defined once; TimelineRevisionController calls it exactly once
- legacyServiceRetainedForI2Queries: class present; zero semantic write methods

verifyGcr2ArtifactAuthority (build.gradle.kts):
- OLD_EXPECTATION = OBSOLETE_LEGACY_AUTHORITY (TimelineRevisionService must
  validate pins through legacy recordRevision)
- NEW_EXPECTATION = CANONICAL_ARTIFACT_PIN_AUTHORITY (extract → validate →
  register on the canonical save path; restore reissues historically verified
  pins; legacy service must NOT regain pin write responsibility; forbidden
  legacy write symbols zero)
- RESULT = PASS (comment-aware, fail-closed; RED-1..4 proven: canonical
  validate removal, register removal, legacy writer reintroduction, restore
  pin-copy removal all FAIL-DETECTED; GREEN after exact restore)

## FCV (from clean committed implementation tree 116b0ef8)

- git status --porcelain --untracked-files=all = EMPTY (FCV_WORKTREE_DIRTY=FALSE)
- full suite: 7583 tests, 0 failures, 0 errors, 43 skipped — PASS
  (./gradlew test, clean committed tree, 18m53s)
- verifyGcr2ArtifactAuthority: PASS (canonical pin authority; RED-1..4 proven)
- pfirr1RemediationCheck: PASS
- verifyC1Cnm1RedGates: PASS (C1-CNM1-RED-01..13)
- jooqFoundationCheck: PASS
- verifyTimelineEffectTransitionCanonicalization: PASS
- :render-module:verifyC20RenderPlanBoundaryGuard: PASS (55 files)
- :platform-app:bootJar: PASS

## Tests updated for accepted deletions

- TimelineRevisionServiceE1bGateIntegrationTest (recordRevision E1b gate /
  backfill / AI-adopt / restore write behaviors): DELETED
- Gcr2PinRegistrationFailureRollbackTest (legacy recordRevision pin rollback):
  DELETED
- TimelineRevisionServiceTest: query tests (preview/replay/history/facets/
  annotation/steps) now seed revision rows via TimelineRevisionRepository;
  write-behavior tests (restore chain, pin register/fail) removed
- TimelineEditorSyncServiceTest: push non-authoring assertion; pullByProject
  no-backfill assertion; fallthrough test added
- 9 stub TimelineRevisionService ctors + 5 controller ctors synchronized to
  new signatures

## Contract checks

- dual write introduced: NO
- compatibility wrapper introduced: NO
- canonical schema changed: NO
- TimelineDocument widened: NO
- lossy AI canonical persistence introduced: NO
- I2 ownership/query migration: NOT STARTED
- I3 broad internal-1.0 cleanup: NOT STARTED
- #21/#22: NOT_STARTED

READY_FOR_CHATGPT_CFRH_I1_IMPLEMENTATION_REVIEW
