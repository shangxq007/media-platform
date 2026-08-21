# ROADMAP20 FINAL RESTORE OWNERSHIP + SEMANTIC COMMITMENT CLOSURE — PUBLICATION

Status: PUBLISHED (governance-only; FINAL acceptance candidate)
Date: 2026-08-21

## 1. Review verdict closed

Independent review: ROADMAP20_FINAL_AUTHORITY_IMMUTABILITY_REVIEW = CORRECTION_REQUIRED
with R1-R3. ARCHITECTURE_PREMISE_FAILURE = NO; ESCALATION = NO; OPTION_B = FROZEN.
F1-F4 confirmed PASS. ffce39ad was NOT accepted because: R1 historical Timeline
snapshot lookup was not ownership-scoped; R2 restore did not verify the complete
persisted historical semantic commitment; R3 findById initial revision row lookup
was not tenant-scoped. This publication closes all three and supersedes ffce39ad
as the acceptance candidate. All prior publications remain immutable historical
evidence.

## 2. R1 — HISTORICAL_TIMELINE_SNAPSHOT_OWNERSHIP_NOT_VERIFIED: CLOSED

- TimelineSnapshotService.findOwnedById(readDsl, projectId, tenantId, snapshotId)
  — ownership-scoped authoritative read
  (TIMELINE_SNAPSHOT_LOOKUP_IS_PROJECT_AND_TENANT_SCOPED_V1; identity
  uniqueness != ownership authority). Query: WHERE id AND project_id AND tenant_id.
- Restore copyHistoricalSnapshotPayload now loads the payload via the owned path
  (tx.dsl() — same physical transaction).
- Merge loadPayload + dedup filter and TimelineRevisionService snapshot reads
  migrated to findOwnedById (load-then-check removed).
- CANONICAL_UNSCOPED_TIMELINE_SNAPSHOT_LOOKUP_COUNT = 0 (production).
- RST7 (RA corrupted to point at RB's snapshot): restore FAIL CLOSED — no new
  revision/snapshot/revctx/esnap/pins; head unchanged.

## 3. R2 — RESTORE_HISTORICAL_SEMANTIC_COMMITMENT_NOT_FULLY_VERIFIED: CLOSED

New bounded HistoricalRevisionRestoreVerifier (fail-closed verification boundary
only — NOT a new authority; runs BEFORE any new persisted state, no temporary
rows before discovering historical corruption):

1. owned Timeline snapshot load (project+tenant)
2. decode persisted canonical TimelineDocument (malformed -> FAIL CLOSED, no
   recovery hydration)
3. recomputed Timeline digest == revctx.timelineContentDigest
   (HISTORICAL_TIMELINE_PAYLOAD_MUST_MATCH_PINNED_TIMELINE_DIGEST_V1)
4. owned Effect snapshot exact load by historical reference
   (HISTORICAL_EFFECT_REFERENCE_MUST_RESOLVE_OWNED_EXACT_SNAPSHOT_V1) — id +
   digest + contract match + internal definition digests + snapshot digest
   recompute
5. recomputed FULL revision semantic digest
   == revctx.revisionSemanticDigest == timeline_revision.content_hash
   (3-way equality; contentHashFinal is now a mandatory integrity value)

Restore reissues the exact historical commitment under a new revision identity —
no remint, no EMPTY fallback, no wire hydration. New revision digest derives
from verified values only (no caller values, no stale strings).

RST8 content_hash tamper -> FAIL CLOSED; RST9 payload digest mismatch -> FAIL
CLOSED; RST10 missing esnap_ -> FAIL CLOSED (no replacement EMPTY); RST11
foreign esnap_ ownership -> FAIL CLOSED; RST12 Effect reference digest tamper
-> FAIL CLOSED.

## 4. R3 — TIMELINE_REVISION_FIND_BY_ID_INITIAL_ROW_LOOKUP_NOT_TENANT_SCOPED: CLOSED

- findById first row read now tenant-scoped
  (REVISION_ROW_AUTHORITATIVE_READ_IS_TENANT_SCOPED_V1): WHERE id AND tenant_id;
  projectId/parent/schema/hash/snapshot derived from that ownership-validated
  row; revctx lookup uses projectId + tenantId + revisionId.
- READOWN1 cross-tenant findById -> NOT FOUND (no existence/project leakage).
- READOWN2 foreign Timeline snapshot never hydrates canonically (restore
  verification fails closed — RST7 path).

## 5. Frozen restore contracts

RESTORE_REQUIRES_SINGLE_OWNERSHIP_CLOSURE_V1 (revision row + Timeline snapshot
+ revctx + Effect snapshot all in project P / tenant T);
RESTORE_REQUIRES_SINGLE_SEMANTIC_COMMITMENT_CLOSURE_V1 (payload digest ->
Timeline digest; Effect snapshot -> commitment; commitment -> full digest ==
revctx == content_hash). Restore is VERIFY COMPLETE HISTORICAL CANONICAL
AUTHORITY -> REISSUE THE SAME SEMANTICS UNDER A NEW REVISION ID.

## 6. Ownership read audit (R1/R3)

| OBJECT | READ PATH | PROJECT SCOPED | TENANT SCOPED | AUTHORITY |
|---|---|---|---|---|
| timeline_revision (restore) | historical lookup | YES | YES | canonical |
| timeline_revision (findById) | initial row read | via row | YES | canonical |
| timeline_snapshot snap_ (restore) | findOwnedById | YES | YES | canonical |
| timeline_snapshot snap_ (merge) | findOwnedById | YES | YES | diff input |
| timeline_snapshot snap_ (revision svc) | findOwnedById | YES | YES | app read |
| timeline_snapshot revctx_ | findByRevisionId(project, tenant, rev) | YES | YES | canonical |
| timeline_snapshot esnap_ | findById(project, tenant, id) | YES | YES | canonical |

CANONICAL_UNSCOPED = 0.

## 7. Evidence — clean-checkout FCV (exact committed tree)

FINAL_RESTORE_INTEGRITY_IMPLEMENTATION_SHA = d46fe3e2d7555d6b9eaf7b03ebd26a904cdfdb96
FINAL_RESTORE_INTEGRITY_IMPLEMENTATION_TREE = 87dcd7d61fd4df2617e8c8d44c4eb0ce0d2a19dd
PARENT_SHA = ffce39ad13a6e211ba0e7421288dcc14d8f8d3a7
FCV_SHA = d46fe3e2; FCV_TREE = 87dcd7d6
FCV_INITIAL_STATUS = CLEAN (0); FCV_UNTRACKED_SOURCE_COUNT = 0
FCV_SOURCE_MUTATIONS_BEFORE_TEST = 0
COMMITTED_SOURCE_COMPLETENESS = PASS (6774 tracked files)
BUILD_INPUT_IDENTITY_MATCHES_COMMITTED_TREE = PASS

- Fresh-checkout compileJava + compileTestJava: PASS (0 errors, 55s)
- FULL SUITE --rerun-tasks (fresh worktree): 7689 / 0 / 0 / 43 (963 files, 18m30s)
- render-module: 2972/0/0/19; timeline-module: 790/0/0; platform-app: 569/0/0/20
- AI 20/20; CF 10/10; TX 5/5 (real injection); OWN 4/4; RCOWN 6/6; SC 9/9;
  RST 12/12; RSTOWN 2/2; RST_TX_HEAD PASS; READOWN 2/2; MT 4/4; PV truthful
  (PV3/PV4 NOT_REPRESENTABLE); canonical 37/37
- E2E-A/B PASS; definition concurrency PASS
- C20 PASS (55 files); verifyC1Cnm1RedGates PASS; Modulith PASS; bootJar PASS;
  pfirr1RemediationCheck PASS; git diff --check PASS
- V1-only Flyway governance: PRESERVED

## 8. Writer matrix (BYPASS = 0, HEAD_LAST = YES)

| Writer | classification | Effect authority | revctx | project/tenant | full digest | pins | head CAS | head last | historical state verified before restore | same TX | bypass |
|---|---|---|---|---|---|---|---|---|---|---|---|
| saveRevision | CANONICAL_DOMAIN_WRITER | EMPTY mint | yes | scoped | yes | yes | yes | YES | N/A | yes | 0 |
| saveRevisionWithEffects | CANONICAL_DOMAIN_WRITER | typed mint | yes | scoped | yes | yes | yes | YES | N/A | yes | 0 |
| restoreRevision | CANONICAL_DOMAIN_WRITER | historical reuse (verified) | yes | scoped | yes | yes | yes | YES | YES | yes | 0 |
| patch apply | DELEGATING_SURFACE | via writer | via writer | scoped | via writer | via writer | via writer | YES | N/A | via writer | 0 |
| merge | diff-only (no writer) | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 0 |

## 9. Semantic commitment audit

HISTORICAL_TIMELINE_PAYLOAD_DIGEST_VERIFIED = YES
HISTORICAL_EFFECT_SNAPSHOT_EXACTLY_VERIFIED = YES
HISTORICAL_REVCTX_DIGEST_VERIFIED = YES
HISTORICAL_REVISION_CONTENT_HASH_VERIFIED = YES
THREE_WAY_FULL_DIGEST_EQUALITY = PASS

## 10. History (append-forward only)

fe42b877 -> 37c8d369 -> 7929ae8f -> b9b0d227 -> 2abc341c -> 564935c6 ->
ffce39ad -> d46fe3e2 (FINAL_RESTORE_INTEGRITY_IMPLEMENTATION) -> <PUBLICATION>.
No amend/reset/rebase/squash/force.

## 11. Governance

- main / origin-main: 07de009205e0ee50cad06e5a324ce18f5c46b10d (UNCHANGED)
- Merge: NO; #20 closed: NO; #21/#22 started: NO; worktree retained
- Evidence: this publication + /tmp/rm20-fcv3-authoritative.log (fresh worktree)

## 12. Non-goals (unchanged)

Operation Model expansion, Constraint Kernel, Formal Methods F0/F1, Lean/Rocq,
Operation Algebra, Semantic Analysis/Rewrite, Cost Optimizer, Physical Planner,
GraphQL expansion, Effect catalog redesign, automation subsystem, polyglot
execution, distributed/user compute.
