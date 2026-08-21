# ROADMAP20 FINAL AUTHORITY IMMUTABILITY + CLEAN-FORWARD CLOSURE — PUBLICATION

Status: PUBLISHED (governance-only; FINAL acceptance candidate)
Date: 2026-08-21

## 1. Review verdict closed

Independent review: ROADMAP20_FINAL_SOURCE_COMPLETENESS_REVIEW = CORRECTION_REQUIRED
with F1-F4. ARCHITECTURE_PREMISE_FAILURE = NO; ESCALATION = NO; OPTION_B = FROZEN.
Prior publications (37c8d369, 7929ae8f, 2abc341c) remain immutable historical
evidence. 2abc341c was NOT accepted because: F1 revctx ownership incomplete;
F2 public mutable authority setters; F3 TX5 evidence did not match actual
mutation order; F4 legacy restore payload fallback remained. This publication
closes all four and supersedes 2abc341c as the acceptance candidate.

## 2. F1 — REVCTX_OWNERSHIP_NOT_CLOSED: CLOSED

- REVISION_SEMANTIC_CONTEXT_HAS_EXPLICIT_OWNERSHIP_V1:
  storeTx(tx, projectId, tenantId, revisionId, context) — tenant_id persisted
  (never null), bound to (project_id, tenant_id, revision_id)
- REVISION_SEMANTIC_CONTEXT_LOOKUP_IS_PROJECT_AND_TENANT_SCOPED_V1:
  findByRevisionId(readDsl, projectId, tenantId, revisionId); revisionId-only
  authoritative lookup REMOVED from the production contract
- CROSS_PROJECT / CROSS_TENANT authority forbidden (both Effect snapshot AND
  revision context)
- Existing revctx rows fully deserialized + ownership + digest + Effect
  reference + contract verified (no partial JSON extraction)
- RCOWN1-6 PASS: cross-project lookup NOT FOUND; cross-tenant NOT FOUND;
  conflicting-ownership store FAIL CLOSED; exact-idempotent PASS; digest
  corruption FAIL CLOSED; Effect reference tamper FAIL CLOSED
- Read path: restore first lookup tenant-scoped; findById revctx lookup
  ownership-scoped; §38 ownership read audit recorded

## 3. F2 — PUBLIC_RUNTIME_FAILURE_INJECTION_BYPASS: CLOSED

- TimelineRevisionPersistencePort + HeadUpdatePort are private final,
  constructor-injected (CANONICAL_WRITE_AUTHORITIES_ARE_REQUIRED_BY_CONSTRUCTION_V1,
  immutable after construction, NO_PUBLIC_RUNTIME_AUTHORITY_MUTATION_SURFACE_V1)
- Public setters DELETED (structural guard: setter count = 0, port fields final)
- ONE public production constructor requires every authority dependency
- Production Spring beans: DefaultTimelineRevisionPersistence + a production
  HeadUpdatePort = ProductCurrentRevisionHeadUpdateAdapter delegating to the
  real ProductCurrentRevisionService.updateCurrentRevisionTx (real DB CAS
  predicate preserved — no check-then-act)
- TX3/TX5 migrated to construction-time failing ports (no setters, no
  testMode, no reflection)

## 4. F3 — TX5_LATEST_MUTATION_EVIDENCE_MISMATCH: CLOSED

Canonical save mutation order (HEAD_ADVANCE_PUBLISHES_ONLY_FULLY_PERSISTED_
REVISION_STATE_V1):
1. canonical validation
2. Timeline governed snapshot persistence
3. Effect snapshot persistence (mintAndPersistTx)
4. full semantic digest computation
5. revision row insert (port)
6. revision semantic context persistence (revctx, ownership-scoped)
7. artifact pin persistence
8. HEAD CAS LAST

Restore order: copy governed payload -> historical revctx (ownership-scoped)
-> revision insert -> new revctx -> pin copy -> HEAD CAS LAST.

- TX5: injected head failure after ALL preceding writes -> real PostgreSQL
  rollback of snapshot/esnap/revision/revctx/pins; head unchanged
- TX1-TX5 all real failure injection (definition conflict, snapshot store,
  revision insert, revctx store, head update) — 5/5
- head-order spy test (bounded port fixture): revctx + pins recorded BEFORE
  head; head is the final recorded mutation (HEAD_LAST proven)
- RST_TX_HEAD: restore head failure rolls back the whole new restored
  transition; original historical revision untouched

## 5. F4 — LEGACY_RESTORE_PAYLOAD_FALLBACK_REMAINS: CLOSED

- RESTORE_ONLY_ACCEPTS_COMPLETE_FINAL_CANONICAL_REVISION_V1;
  RESTORE_MISSING_GOVERNED_PAYLOAD_FAILS_CLOSED_V1;
  NO_LEGACY_RESTORE_PAYLOAD_FALLBACK_V1
- fallbackRevisionId DELETED; copyHistoricalSnapshotPayload(tx, projectId,
  tenantId, historicalRevisionId, newRevisionId) FAILS CLOSED on: revision not
  found; null/blank SNAPSHOT_ID; missing snapshot row; missing revctx; wrong
  revctx ownership; cross-project/tenant (restore first lookup is
  tenant-scoped)
- timelineSnapshotService == null compatibility branches DELETED
  (persistSnapshotPayload / findPayloadDocument / copyHistoricalSnapshotPayload)
- RST1-6 PASS (RST1: SNAPSHOT_ID NOT NULL constraint = null unrepresentable;
  RST2 missing payload row; RST3 missing revctx; RST4 wrong ownership; RST5
  valid restore; RST6 covered by RST5 atomic new revctx/snapshot/pins)
- RSTOWN1/2 cross-project / cross-tenant restore FAIL CLOSED
- CF10 is now REAL executable evidence (RST1-6/RSTOWN + structural absence of
  fallbackRevisionId / null-service branch) — not assertTrue(true)

## 6. Also closed (review observations)

- SC8/SC9: Effect snapshot existing-row ownership verification — same id +
  different project/tenant store attempt FAIL CLOSED; exact-idempotent PASS;
  same-ownership different-content FAIL CLOSED
- Clean Javadocs in EffectSemanticSnapshotAuthority / EffectInstance (legacy
  wire language removed)
- §57 clean-forward production residue scan: LegacyWireEffect, mintFromDocument,
  AuthoredEffectSemanticAuthority, EffectSemanticBinding,
  RevisionOwnedEffectProjection, timeline-only-v1, fallbackRevisionId,
  legacy-3-arg-wiring, timelineSnapshotService == null,
  setRevisionPersistencePort, setHeadUpdatePort = ZERO in production source

## 7. Evidence — clean-checkout FCV (exact committed tree)

FINAL_AUTHORITY_IMMUTABILITY_IMPLEMENTATION_SHA = 564935c642195ff097e79c248400fd649a3e2f8b
FINAL_AUTHORITY_IMMUTABILITY_IMPLEMENTATION_TREE = 550a1fac3f25e5c6c12d2178f34c144b5d65d081
PARENT_SHA = 2abc341cb5965c3c04a913d9d469039e6a9b6e3d
FCV_WORKTREE = fresh `git worktree add --detach` at the implementation SHA
FCV_INITIAL_WORKTREE_STATUS = CLEAN (0)
FCV_UNTRACKED_SOURCE_COUNT = 0
FCV_SOURCE_MUTATIONS_BEFORE_TEST = 0
COMMITTED_SOURCE_COMPLETENESS = PASS (6772 tracked files)
BUILD_INPUT_IDENTITY_MATCHES_COMMITTED_TREE = PASS

- Fresh-checkout compileJava + compileTestJava: PASS (0 errors, 57s)
- FULL SUITE --rerun-tasks (fresh worktree): 7681 / 0 / 0 / 43 (963 result files, 18m56s)
- render-module: 2964/0/0/19; timeline-module: 790/0/0; platform-app: 569/0/0/20
- AI 20/20; CF 10/10 (CF10 real); TX 5/5 (real injection, head-last);
  OWN 4/4; RCOWN 6/6; RST 6/6; RSTOWN 2/2; SC 9/9; MT 4/4; PV truthful
  (PV3/PV4 NOT_REPRESENTABLE documented); canonical 37/37 (R6 35/35 +
  EffectSemanticSnapshotFinalAcceptanceTest 16/16)
- E2E-A/B PASS (ownership-scoped reload + render consumption, head-last)
- Definition concurrency PASS (advisory lock, same physical transaction)
- C20 PASS (55 files); verifyC1Cnm1RedGates PASS; Modulith PASS; bootJar PASS;
  pfirr1RemediationCheck PASS; git diff --check PASS
- V1-only Flyway governance: PRESERVED (revctx_/esnap_ rows in timeline_snapshot)

## 8. Writer matrix (BYPASS = 0, HEAD_LAST = YES for all canonical writers)

| Writer | classification | Effect authority | revctx | project/tenant | full digest | pins | head CAS | head last | same TX | bypass |
|---|---|---|---|---|---|---|---|---|---|---|
| saveRevision | CANONICAL_DOMAIN_WRITER | EMPTY mint | yes | scoped | yes | yes | yes | YES | yes | 0 |
| saveRevisionWithEffects | CANONICAL_DOMAIN_WRITER | typed mint | yes | scoped | yes | yes | yes | YES | yes | 0 |
| restoreRevision | CANONICAL_DOMAIN_WRITER | historical reuse | yes | scoped | yes | yes | yes | YES | yes | 0 |
| patch apply | DELEGATING_SURFACE (-> saveRevision) | via writer | via writer | scoped | via writer | via writer | via writer | YES | via writer | 0 |
| merge | diff-only (no canonical writer) | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 0 |

## 9. History (append-forward only)

fe42b877 -> 37c8d369 -> 7929ae8f -> b9b0d227 -> 2abc341c -> 564935c6
(FINAL_AUTHORITY_IMMUTABILITY_IMPLEMENTATION) -> <PUBLICATION>.
No amend/reset/rebase/squash/force.

## 10. Governance

- main / origin-main: 07de009205e0ee50cad06e5a324ce18f5c46b10d (UNCHANGED)
- Merge: NO; #20 closed: NO; #21/#22 started: NO; worktree retained
- Evidence: this publication + /tmp/rm20-fcv2-authoritative.log (fresh worktree)

## 11. Non-goals (unchanged)

Formal Methods F0/F1, Lean/Rocq, Operation Algebra, Semantic Analysis/Rewrite,
Cost Optimizer, Physical Planner, Effect catalog redesign, GraphQL expansion,
automation subsystem, polyglot execution, distributed/user compute.
