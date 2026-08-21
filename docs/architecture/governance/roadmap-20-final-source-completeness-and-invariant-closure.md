# ROADMAP20 FINAL SOURCE COMPLETENESS + INVARIANT CLOSURE — PUBLICATION

Status: PUBLISHED (governance-only, supersedes 7929ae8f as the FINAL acceptance candidate)
Date: 2026-08-21

## 1. Review verdict closed

Independent review: ROADMAP20_AUTHORITY_INTEGRATION_FINAL_INDEPENDENT_REVIEW =
CORRECTION_REQUIRED. ARCHITECTURE_PREMISE_FAILURE = NO; ESCALATION = NO;
OPTION_B = FROZEN. MATERIAL_BLOCKERS = 4 — all closed below.
Prior publications (37c8d369, 7929ae8f) remain immutable historical evidence;
this publication supersedes 7929ae8f as the acceptance candidate. The review
discovered (and this closure fixes): committed-tree incompleteness, TX3-5
evidence overclaim, legacy Effect hydration residue, snapshot
ownership/corruption gaps. History stays honest — no rewrite.

## 2. B1 — REMOTE_COMMITTED_TREE_NOT_SELF_CONTAINED: CLOSED

Missing-source matrix (files that existed only in the dirty worktree and were
omitted from 37c8d369/7929ae8f):

| PATH | USED BY | TRACKED? | IN 37C8D369? | IN 7929AE8F? | ACTION |
|---|---|---|---|---|---|
| timeline/version/TimelineRevisionSemanticContext.java | TimelineRevision | NO | NO | NO | COMMITTED b9b0d227 |
| timeline/version/TimelineRevisionSemanticContextJsonCodec.java | revctx store | NO | NO | NO | COMMITTED b9b0d227 |
| timeline/version/TimelineRevisionSemanticContextStore.java | SaveService port | NO | NO | NO | COMMITTED b9b0d227 |
| timeline/adapter/JdbcTimelineRevisionSemanticContextStore.java | SaveService | NO | NO | NO | COMMITTED b9b0d227 |
| timeline/semantics/effect/EffectSemanticSnapshotAuthorityInternal.java | authority | NO | NO | NO | COMMITTED b9b0d227 |
| timeline/app/TimelineRevisionPersistencePort.java | TX3 port | NO | NO | NO | COMMITTED b9b0d227 |
| timeline/app/DefaultTimelineRevisionPersistence.java | TX3 port | NO | NO | NO | COMMITTED b9b0d227 |
| timeline/app/HeadUpdatePort.java | TX5 port | NO | NO | NO | COMMITTED b9b0d227 |

Root cause: `git add -u` stages only modifications of already-tracked files;
new files created mid-workflow were omitted. Mitigation (permanent): the
ROADMAP20_COMMITTED_SOURCE_COMPLETENESS_GATE_V1 script
(scripts/roadmap20-source-completeness-gate.sh) fails on any critical file
that is missing or untracked, and on ANY untracked Java/Kotlin/resource/build
file (exact-SHA FCV input must equal the committed tree). Gate PASS: 6769
tracked files.

## 3. B2 — TRANSACTION_ATOMICITY_EVIDENCE: CLOSED (real failure injection)

Narrow persistence ports (no testMode booleans; production defaults are the
single jOOQ writer / CAS head update):
- TimelineRevisionPersistencePort (+ DefaultTimelineRevisionPersistence)
- TimelineRevisionSemanticContextStore (interface; Jdbc implements)
- HeadUpdatePort

| TX | Injected failure point | Verified rollback (real PostgreSQL) |
|---|---|---|
| TX1 | definition identity conflict | 0 revision / 0 snapshot / head unchanged |
| TX2 | Effect snapshot storeTx throw | 0 revision / 0 snapshot / head unchanged |
| TX3 | revision ROW insert throw (after snap+esnap written) | 0 revision / 0 snapshot rows (snap/esnap/revctx) / head unchanged |
| TX4 | semantic context storeTx throw (after revision insert attempted) | 0 revision / 0 snapshot rows / head unchanged |
| TX5 | head update throw (latest mutation point) | 0 revision / 0 snapshot rows / head unchanged |

All 5 PASS with actual failure injection at the real boundary.

## 4. B3 — LEGACY_EFFECT_HYDRATION_PUBLIC_API: CLOSED

- LegacyWireEffect record: DELETED (ClassNotFound-verified in CF9)
- mintFromDocument(TimelineDocument, List<LegacyWireEffect>, List<Definition>): DELETED
- resolveTargetContext: fully typed (trackId, clipId, effectId, effectKey) — no
  wire-id encoding, no trackId heuristics
- AuthorityInternal.legacyEntry renamed to buildEntry (typed entry construction
  core — NOT a legacy compatibility layer; wire hydration model removed)
- Authority public surface: mintEmpty / mintFromAuthoredState / mintAndPersistTx only
- Production legacy hydration residue: ZERO

CF9 final meaning: NO LEGACY EFFECT HYDRATION PRODUCTION PATH. TimelineRevision
.hydrate(document) is a SEPARATE invariant
(TIMELINE_REVISION_HYDRATION_IS_CONTENT_VERIFIED_NOT_MUTATION_V1): computed
Timeline digest must equal semanticContext.timelineContentDigest; foreign
document FAIL CLOSED. Tested separately (hydrate_isContentVerifiedNotMutation).

## 5. B4 — SNAPSHOT_OWNERSHIP_AND_CORRUPTION_BOUNDARY: CLOSED

- EFFECT_SEMANTIC_SNAPSHOT_HAS_EXPLICIT_OWNERSHIP_V1: storeTx writes tenant_id
  (from the trusted tenant context — never null); row bound to (project_id,
  tenant_id)
- AUTHORITATIVE_SNAPSHOT_LOOKUP_IS_OWNERSHIP_SCOPED_V1: findById(projectId,
  tenantId, snapshotId); global by-id lookup removed from the production API
- CROSS_PROJECT / CROSS_TENANT authority forbidden: OWN1-OWN4 PASS
  (OWN1 cross-project NOT FOUND; OWN2 cross-tenant NOT FOUND; OWN3 foreign
  snapshot unresolvable; OWN4 no global-by-id API)
- Ambiguous single-arg 'esnap' project default REMOVED — store() throws;
  production writes use storeTx with explicit ownership
- CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1: existing rows fully
  deserialized and verified (id match, recomputed digest, supported contract,
  embedded definition digests). No partial field extraction; no null-digest
  idempotent success
- SC1-SC7 PASS (malformed payload, missing/invalid digest, id mismatch,
  unsupported contract, idempotent exact, same-id different-content FAIL CLOSED)

## 6. Clean-forward final matrix (CF1-CF10, 10/10)

CF1 revision requires semanticContext; CF2 context requires exact Effect
reference + single revision-semantics contract; CF3 no-Effect -> authoritative
EMPTY; CF4 Effect-bearing -> NON-EMPTY; CF5 missing revctx INVALID/CORRUPT;
CF6/7/8 legacy authority types physically absent; CF9 legacy Effect hydration
API/path absent; CF10 restore/new writes have no legacy MISSING branch.

## 7. AI1-AI20 (20/20) — mapping unchanged in meaning

AI1 pin ownership; AI2 EMPTY; AI3 NON-EMPTY; AI4 no expectedReference
substitution; AI5 no caller snapshotId; AI6 MISSING=INVALID; AI7-9 same-TX
writes; AI11-13 legacy types absent; AI14-15 durable Spring wiring
(Roadmap20ProductionWiringTest — Jdbc registry + store + revctx store;
InMemory production references = 0); AI16-17 E2E reload/render; AI18
definition concurrency (advisory lock, same physical transaction); AI19
corrupt fail-closed; AI20 track-type authority (MT1-4).

## 8. Evidence — clean-checkout FCV (exact committed tree)

FCV_SHA            = b9b0d22756a2386a6401977b23ba212e2626437e
FCV_TREE           = 10117a9a2bb9d5870a4cb063b5857b53b443b57a
PARENT             = 7929ae8f1f4ecd604ab4f219ca9c3d3fe8cd4cca
FCV_WORKTREE       = fresh `git worktree add --detach` at FCV_SHA
FCV_WORKTREE_INITIAL_STATUS = CLEAN (0 untracked/modified)
FCV_WORKTREE_SOURCE_MUTATIONS_BEFORE_TEST = 0 (no copied/generated sources)
REQUIRED_UNTRACKED_SOURCE_COUNT = 0
COMMITTED_SOURCE_COMPLETENESS = PASS

- Fresh-checkout compileJava + compileTestJava: PASS (0 errors, 56s)
- FULL SUITE --rerun-tasks (fresh worktree): 7664 / 0 / 0 / 43 (962 result files, 18m53s)
- render-module: 2947/0/0/19; timeline-module: 790/0/0; platform-app: 569/0/0/20
- OWN 4/4; SC 7/7; TX1-TX5 real injection PASS; AI 20/20; CF 10/10; MT 4/4;
  PV truthful (PV1 PASS, PV2 FAIL CLOSED, PV3/PV4 NOT_REPRESENTABLE documented)
- canonical 37: RP1-5/SA1-5/D1-6/SO1-4/L1-5/R1-5/BI1-5 green (R6 35/35 +
  EffectSemanticSnapshotFinalAcceptanceTest 16/16 within the suites)
- E2E-A / E2E-B: PASS (ownership-scoped reload + render consumption)
- Definition concurrency: PASS (pg_advisory_xact_lock, same transaction)
- C20 guard: PASS (55 files); verifyC1Cnm1RedGates: PASS; Modulith: PASS;
  bootJar: PASS; pfirr1RemediationCheck: PASS; git diff --check: PASS
- V1-only Flyway governance: PRESERVED (revctx_/esnap_ rows in timeline_snapshot)

## 9. Writer matrix (BYPASS = 0)

| Writer | Effect authority | context | pin | full digest | same TX | ownership | head | bypass |
|---|---|---|---|---|---|---|---|---|
| saveRevision | EMPTY mint | revctx_ | yes | yes | yes | yes | yes | 0 |
| saveRevisionWithEffects | typed mint | revctx_ | yes | yes | yes | yes | yes | 0 |
| restoreRevision | historical reuse | revctx_ | yes | yes | yes | yes | yes | 0 |
| patch apply | -> saveRevision | revctx_ | yes | yes | yes | yes | yes | 0 |
| merge | diff-only (no writer) | — | — | — | — | — | — | 0 |

## 10. History (append-forward only)

fe42b877 -> 37c8d369 -> 7929ae8f -> b9b0d227 (FINAL_CLOSURE_IMPLEMENTATION)
-> <FINAL_CLOSURE_PUBLICATION>. No amend/reset/rebase/squash/force.

## 11. Governance

- main / origin-main: 07de009205e0ee50cad06e5a324ce18f5c46b10d (UNCHANGED)
- Merge: NO; #20 closed: NO; #21/#22 started: NO; worktree retained
- Evidence: this publication + /tmp/rm20-fcv-authoritative.log (fresh worktree)

## 12. Non-goals (unchanged)

Operation Algebra, Formal Methods F0/F1, Lean/Rocq, Semantic Analysis/Rewrite,
Cost Optimizer, Physical Planner, new Effect catalog system, automation
subsystem, polyglot execution, distributed/user compute, GraphQL expansion.
