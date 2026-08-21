# ROADMAP20 FINAL CANONICAL READ + RESTORE EVIDENCE CLOSURE

## 1. Status

ROADMAP20_FINAL_CANONICAL_READ_IMPLEMENTATION = 08696bd22457026a00a613c9e830db6e7bbf7c5d
ROADMAP20_FINAL_CANONICAL_READ_TREE         = 2eaa75563dfa00429bad1d63d4f2ce5de58d4965
PARENT_SHA                                  = c525310cf21a92e4b6cbf043ced4794ad8dd6824
ROADMAP20_FINAL_CANONICAL_READ_PUBLICATION  = committed as this file's commit (see git log; parent = 08696bd2)

Branch: agent/roadmap20-renderplan (append-forward, no amend/reset/rebase)
main / origin-main: 07de009205e0ee50cad06e5a324ce18f5c46b10d (UNCHANGED)

History linearity: 564935c6 -> ffce39ad -> d46fe3e2 -> c525310c -> 08696bd2 -> <PUB>

## 2. c525310c Review Failure — explicit acknowledgement

c525310c was NOT accepted as final because:

- **C1** — restore verified an owned Timeline payload but then re-read it via
  unscoped `findPayload(historicalSnapshotId)` inside
  `copyHistoricalSnapshotPayload` before reissue. The bytes reissued were not
  provably the bytes verified (RESTORE_REISSUES_EXACTLY_THE_VERIFIED_TIMELINE_PAYLOAD_V1
  violated).
- **C2** — patch canonical hydration (`findPayloadDocument`) still used
  revisionId-only reads + the global `findPayload(snapshotId)`, and READOWN2
  called `restoreRevision` (a duplicate of RST7) instead of exercising the
  actual hydration helper.
- **C3** — RST10/RST11 modified/deleted `timeline_revision.snapshot_id`
  targets (the Timeline governed snapshot), NOT the real `esnap_` Effect
  snapshot identity. Their claimed Effect-snapshot evidence was invalid.
- **R3** — `findById` used one tenant-scoped discovery query followed by five
  revisionId-only field queries (parent/schema/contentHash/createdAt/author),
  contradicting the one-ownership-validated-unit publication language.

## 3. C1 — verified payload is exactly reissued

Contract: RESTORE_REISSUES_EXACTLY_THE_VERIFIED_TIMELINE_PAYLOAD_V1 = PASS
          VERIFIED_RESTORE_STATE_IS_ATOMIC_AS_A_SEMANTIC_VALUE_V1 = PASS

VerifiedHistoricalRevision now carries the VERIFIED state itself:

```java
record VerifiedHistoricalRevision(
    TimelineDocument document,
    String canonicalPayloadJson,      // exact verified bytes
    String timelineSchemaVersion,
    String timelineDigest,
    EffectSemanticSnapshotReference effectReference,
    String fullRevisionSemanticDigest,
    String digestContractVersion)
```

restore flow (one read, one verify, one reissue):

```
ownership-scoped snapshot read (findOwnedById)
  -> decode
  -> verify digest
  -> VERIFIED payload/document
  -> persist new restored snapshot directly from verified value
     (timelineSnapshotService.saveTx(tx, projectId, tenantId,
        verified.canonicalPayloadJson(), verified.timelineSchemaVersion()))
```

- `copyHistoricalSnapshotPayload(...)` DELETED (no historical re-query exists).
- `RESTORE_POST_VERIFICATION_HISTORICAL_REREAD_COUNT = 0` (production source
  has no `timelineSnapshotService.findPayload(historicalSnapshotId)` after
  verifier success).
- historicalContentHash null-bypass removed: `Objects.requireNonNull` FAIL
  CLOSED (§42) — missing persisted full commitment is INVALID/CORRUPT.
- All restored semantic state derives from the SINGLE verified result
  (digest, Effect reference, full commitment, contract version) — no mixed
  source.

Evidence: RST13_VERIFIED_TIMELINE_PAYLOAD_IS_EXACTLY_REISSUED PASS —
historical owned snapshot payload == restored owned snapshot payload
(byte-for-byte), restored Timeline digest == verified historical digest,
full semantic digest preserved.

## 4. C2 — findPayloadDocument canonical hydration is ownership scoped

Contract: CANONICAL_TIMELINE_PAYLOAD_READ_IS_PROJECT_AND_TENANT_SCOPED_V1 = PASS

findPayloadDocument(revisionId):

```
tenantId = TenantContext.get()
readOwnedRevisionRow(revisionId, tenantId)     // ONE ownership-validated row
  -> projectId + snapshotId from the single row
timelineSnapshotService.findOwnedById(dsl, projectId, tenantId, snapshotId)
  -> deserialize
```

- No global `findPayload` on this path; foreign snapshot -> Optional.empty ->
  caller FAIL CLOSED (PAYLOAD_INVALID).
- Patch apply AND preview hydrate through the SAME owned path — preview is not
  a weaker authority path.

CANONICAL_UNSCOPED_TIMELINE_SNAPSHOT_LOOKUP_COUNT = 0
(see snapshot read audit in §8)

Evidence:
- READOWN2_FOREIGN_TIMELINE_SNAPSHOT_CANNOT_HYDRATE_CANONICAL_REVISION PASS
  (direct findPayloadDocument call, RA.snapshot_id corrupted to RB's snapshot
  -> Optional.empty)
- READOWN3_PATCH_APPLY_FOREIGN_SNAPSHOT_FAILS_CLOSED PASS
  (TIMELINE_PATCH_PAYLOAD_INVALID; no new revision; head unchanged)
- READOWN4_PATCH_PREVIEW_FOREIGN_SNAPSHOT_FAILS_CLOSED PASS
  (TIMELINE_PATCH_PAYLOAD_INVALID; no mutation)

## 5. C3 — RST10/RST11 target REAL Effect snapshot authority

Contract: RESTORE_EFFECT_SNAPSHOT_TESTS_MUST_TARGET_REAL_EFFECT_SNAPSHOT_AUTHORITY_V1 = PASS

RST10_MISSING_EFFECT_SNAPSHOT_FAILS_CLOSED:
- actual deleted object id = revctx.effectReference().snapshotId
  (loaded via JdbcTimelineRevisionSemanticContextStore — final production API)
- preconditions asserted: Timeline governed snapshot EXISTS, revctx EXISTS,
  historical revision EXISTS, ONLY the esnap_ row is deleted
- restore FAILS CLOSED at the Effect lookup; diagnostic names the Effect
  snapshot boundary

RST11_FOREIGN_EFFECT_SNAPSHOT_FAILS_CLOSED:
- actual foreign object id = RB revctx.effectReference().snapshotId
- foreign esnap_ EXISTS and is VALID under B (asserted)
- RA revctx is INTERNALLY SELF-CONSISTENT: rebuilt via the production codec
  with recomputed full digest H(RA.timelineDigest, RB.contract, RB.digest);
  RA content_hash aligned to the recomputed value
- only violated invariant = OWNERSHIP; failure occurs at the ownership-scoped
  Effect lookup (findById(projectA, tenantA, RB.snapshotId) -> NOT FOUND)

RST12_effectReferenceDigestMismatchFailsClosed retained unchanged (targets a
real esnap reference digest/version mismatch).

RST14_EFFECT_BEARING_EXACT_RESTORE_PASS (positive complement): restored
Effect reference == historical reference exactly (snapshotId, contentDigest,
semanticContractVersion) — no remint.

## 6. R3 — authoritative revision row read as one unit

Contract: AUTHORITATIVE_REVISION_ROW_IS_READ_AS_ONE_OWNERSHIP_VALIDATED_UNIT_V1 = PASS

findById:

```
SELECT id, project_id, tenant_id, parent_revision_id, schema_version,
       content_hash, snapshot_id, created_at, author_user_id
FROM timeline_revision
WHERE id = ? AND tenant_id = ?          -- ONE query, full-row ownership
```

- All fields derive from the single returned row (readOwnedRevisionRow).
- revisionId-only split field reads = 0 for findById.
- findPayloadDocument reuses the SAME bounded reader (no copy-paste queries).
- Ownership validation applies to the FULL row, not just projectId discovery.

## 7. Matrices

AI 20/20 — PASS (unchanged mapping: Roadmap20AIIntegrationAcceptanceTest +
ProductionWiring + E2E-A/B + DefinitionConcurrencyAndCorruption)
CF 10/10 — PASS (Roadmap20CleanForwardGuardTest; CF9 no legacy Effect
hydration; CF10 no legacy restore/new-write fallback; no compat code added)
TX 5/5 — PASS (Roadmap20TransactionAtomicityTest, real failure injection)
OWN 4/4 — PASS (Roadmap20SnapshotOwnershipAndCorruptionTest own1-4)
RCOWN 6/6 — PASS (rcown1-6)
SC 9/9 — PASS (sc1-9)
RST 14/14 — PASS (RST1-5, RST7-14 incl. corrected RST10/RST11 + new RST13/RST14)
RSTOWN 2/2 — PASS (rstown1-2)
RST_TX_HEAD — PASS (head-failure rollback, zero committed rows)
READOWN 4/4 — PASS (readown1-4)
MT 4/4 — PASS (mt1-4)
PV — truthful: PV1 PASS, PV2 FAIL CLOSED, PV3 NOT_REPRESENTABLE,
PV4 NOT_REPRESENTABLE (no invented schema semantics)
canonical 37/37 — PASS (EffectSemanticSnapshotFinalAcceptanceTest, R6 35/35 +
EMPTY2/EMPTY4 mapping unchanged)
E2E-A / E2E-B — PASS
definition concurrency — PASS (ai18: exactly one winner, real PostgreSQL)

## 8. Audits

### 8.1 ROADMAP20_FINAL_SNAPSHOT_READ_AUDIT

| FILE | METHOD | CALLER | CANONICAL? | PROJECT_SCOPED? | TENANT_SCOPED? | API_USED |
|---|---|---|---|---|---|---|
| TimelineRevisionSaveService | restoreRevision (reissue) | restore | YES | YES | YES | findOwnedById (via verifier) + saveTx from verified |
| HistoricalRevisionRestoreVerifier | verify | restore | YES | YES | YES | findOwnedById |
| TimelineRevisionSaveService | findPayloadDocument | patch apply/preview | YES | YES | YES | readOwnedRevisionRow + findOwnedById |
| TimelineMergeEngine | loadPayload | merge / mergeSemantic | YES | YES | YES | findOwnedById (contextTenant always non-null from TenantGuard) |
| TimelineSnapshotService | findPayload(String) | RenderJobExecutionService:590 | NO (render execution) | N/A | N/A | findPayload |
| TimelineSnapshotService | findPayload(String) | BaseJobTimelineLoader:56 | NO (render execution) | N/A | N/A | findPayload |
| TimelineSnapshotService | findPayload(String) | PlanBasedTimelineRevisionRenderService:187 | NO (render execution; project verified first) | YES | NO | findPayload |
| TimelineSnapshotService | findPayload(String) | TimelineRevisionRenderService:129 | NO (render execution; project verified first) | YES | NO | findPayload |
| TimelineSnapshotService | findPayload(String) | TimelineMergeEngine:768 | NO (unreachable fallback; contextTenant always non-null) | N/A | N/A | findPayload |
| TimelineSnapshotService | findPayload(String) | TimelineRevisionService:316/319/413/418/472 | NO (legacy internal-1.0 read paths) | PARTIAL | NO | findPayload |
| TimelineSnapshotService | findById(String) | TimelineAssetLifecycleService:115 | NO (asset admin) | load-then-check | NO | findById |
| TimelineSnapshotService | findById(String) | TimelineEditorSyncService:73 | NO (editor sync admin) | load-then-check | NO | findById |

CANONICAL_UNSCOPED_TIMELINE_SNAPSHOT_LOOKUP_COUNT = 0

### 8.2 ROADMAP20_FINAL_REVISION_READ_AUDIT

| READ | TENANT PREDICATE | OWNERSHIP SOURCE |
|---|---|---|
| TimelineRevisionSaveService.findById | YES (WHERE id AND tenant_id) | one ownership-validated row unit (R3) |
| TimelineRevisionSaveService.findPayloadDocument | YES (same reader) | inherited from validated row unit |
| TimelineRevisionSaveService.restoreRevision historical row reads | YES (project + tenant predicates on each read) | caller-validated project + TenantContext |
| TimelineRevisionRepository.findById (legacy internal-1.0) | NO (legacy path, non-canonical) | documented NON_CANONICAL_LEGACY |

### 8.3 TEST TARGET AUDIT

| TEST | CLAIMED TARGET | ACTUAL ROW/OBJECT MODIFIED | EXPECTED FAILURE BOUNDARY |
|---|---|---|---|
| RST7 | foreign Timeline snapshot | timeline_revision.snapshot_id -> RB snapshot | Timeline owned lookup (findOwnedById) |
| RST8 | content_hash mismatch | timeline_revision.content_hash | 3-way digest equality |
| RST9 | Timeline payload digest mismatch | timeline_snapshot.payload_json (owned) | Timeline digest recompute |
| RST10 | missing Effect snapshot | DELETE esnap_ = revctx.effectReference().snapshotId ONLY | Effect snapshot owned lookup |
| RST11 | foreign Effect snapshot | revctx JSON -> RB's REAL esnap_ (self-consistent codec rebuild) | Effect snapshot ownership boundary |
| RST12 | Effect reference digest mismatch | revctx JSON contentDigest tamper (real esnap ref) | exact reference match |
| RST13 | exact payload reissue | none (restore executes) | byte equality historical vs restored |
| READOWN1 | cross-tenant findById | none (tenant switch) | findById null |
| READOWN2 | foreign snapshot hydration | timeline_revision.snapshot_id -> RB snapshot | findPayloadDocument Optional.empty |
| READOWN3 | patch apply foreign snapshot | timeline_revision.snapshot_id -> RB snapshot | TIMELINE_PATCH_PAYLOAD_INVALID, zero writes |
| READOWN4 | patch preview foreign snapshot | timeline_revision.snapshot_id -> RB snapshot | TIMELINE_PATCH_PAYLOAD_INVALID, no mutation |

### 8.4 FINAL WRITER MATRIX

| WRITER | BYPASS | HEAD_LAST | VERIFIED-PAYLOAD-REISSUED |
|---|---|---|---|
| saveRevision | 0 | YES | N/A |
| saveRevisionWithEffects | 0 | YES | N/A |
| restoreRevision | 0 | YES | YES (historical fully verified; verified payload directly reissued) |
| patch | 0 (delegates canonical writer) | YES | owned payload hydration YES |
| merge | N/A (diff-only semantics unchanged) | N/A | N/A |

## 9. FCV

FCV_SHA                          = 08696bd22457026a00a613c9e830db6e7bbf7c5d
FCV_TREE                         = 2eaa75563dfa00429bad1d63d4f2ce5de58d4965
FCV_INITIAL_WORKTREE_STATUS      = CLEAN
FCV_UNTRACKED_SOURCE_COUNT       = 0
FCV_SOURCE_MUTATIONS_BEFORE_TEST = 0
COMMITTED_SOURCE_COMPLETENESS    = PASS
BUILD_INPUT_IDENTITY_MATCHES_COMMITTED_TREE = PASS

<FCV_RESULTS: filled after full-suite run>

### 9.1 FCV results (fresh detached worktree /tmp/rm20-fcv-final-cr)

- compileJava / compileTestJava: PASS (94 tasks, 0 mutations, 56s)
- FULL SUITE `test --rerun-tasks`: **7591 / 0 failures / 0 errors / 43 skipped**
  (18m48s; 176 tasks; baseline c525310c = 7483/0/0/43, +108 = +4 new tests
  [RST13/RST14/READOWN3/READOWN4] + accumulated non-roadmap20 growth)
- timeline-module: 790/0/0/0 (incl. corrected RST10/RST11, new RST13/RST14)
- render-module: 2976/0/0/19 (incl. READOWN 4/4, all matrices)
- platform-app: 569/0/0/20 (incl. Roadmap20ProductionWiringTest C20 2/2)
- bootJar: PASS
- Modulith (ModularityTest): PASS
- jooqFoundationCheck (incl. verifyC20RenderPlanBoundaryGuard): PASS
- pfirr1RemediationCheck: PASS
- verifyC1Cnm1RedGates (RED-01..13): PASS
- verifyC1TimelineMergeConvergence: PASS
- git diff --check: PASS
- FCV_SOURCE_MUTATIONS_BEFORE_TEST = 0 (verified: worktree clean, HEAD exact,
  untracked source count 0, source mutations 0 after full suite)

### 9.2 Matrices (FCV, exact)

- AI 20/20 PASS; CF 10/10 PASS; TX 5/5 PASS; OWN 4/4 PASS; RCOWN 6/6 PASS;
  SC 9/9 PASS; RST 14/14 PASS (RST1-5, RST7-14); RSTOWN 2/2 PASS;
  RST_TX_HEAD PASS; READOWN 4/4 PASS; MT 4/4 PASS; PV truthful
  (PV1 PASS, PV2 FAIL CLOSED, PV3/PV4 NOT_REPRESENTABLE);
  canonical 37/37 PASS; E2E-A PASS; E2E-B PASS;
  definition concurrency (ai18 real PostgreSQL) PASS;
  writer BYPASS = 0; HEAD_LAST = YES;
  restore VERIFIED_PAYLOAD_REISSUED_DIRECTLY = YES;
  canonical Timeline snapshot unscoped read count = 0;
  canonical revision row split/unscoped read count = 0 for findById

## 10. Governance

- merge = NO
- #20 closed = NO
- #21/#22 started = NO
- main/origin-main = 07de0092... UNCHANGED (verified after push)
- Roadmap #20 worktree retained (not removed)
- Prior publications immutable (no edits; this is a NEW publication file)
