# ROADMAP20 AUTHORITY INTEGRATION CORRECTION — FINAL IMPLEMENTATION & PUBLICATION

Status: PUBLISHED (governance-only)
Date: 2026-08-21

## 1. Decision context

ChatGPT independent final review (doc_e1b830607bb6) = CORRECTION_REQUIRED with 5
material blockers (B1-B5). The previous FINAL_IMPLEMENTATION (230b06c1) and
FINAL_PUBLICATION (fe42b877) remain immutable evidence. This correction fixes the
INTEGRATION BOUNDARY: the Effect semantic subsystem is now joined into the
canonical TimelineRevision authority chain. No architecture reopening; OPTION_B
remains frozen. CLEAN-FORWARD: NO_HISTORICAL_PRODUCT_COMPATIBILITY_OBLIGATION=TRUE.

## 2. Blockers closed

- B1: TimelineRevision owns the exact EffectSemanticSnapshotReference via a
  mandatory TimelineRevisionSemanticContext (contract revision-semantics-v1);
  contentDigest == context.revisionSemanticDigest() enforced by construction;
  context persisted as revctx_ rows in timeline_snapshot (V1-only Flyway
  governance: exactly one V1 migration, zero new migrations); revision digest
  commits Effect semantics.
- B2: Render derives the expected pin FROM the revision
  (VerifiedRenderSemanticSnapshotFactory.verified(revision, digester, snapshot));
  no independent expectedReference parameter; foreign snapshot FAIL CLOSED.
- B3: EffectSemanticSnapshotAuthority is an instance authority: registry +
  store constructor-injected, no public static mint, snapshotId
  authority-generated, snapshot constructor package-private (same-id collision
  unreachable through typed paths).
- B4: JdbcEffectSemanticSnapshotStore / JdbcEffectDefinitionVersionRegistry are
  mandatory production dependencies (SaveService constructor injection, Spring
  wiring Roadmap20EffectAuthorityConfiguration); storeTx/registerTx join the
  revision's physical transaction (§21); pg_advisory_xact_lock serializes
  concurrent (definitionId, version) writers; InMemory production references = 0.
- B5: mediaType derived from canonical TimelineTrack.type via
  resolveTargetContext — no trackId string heuristics (MT1-MT4 prove).

## 3. Clean-forward (§78 invariant)

"NO NEW CANONICAL TIMELINE REVISION EXISTS WITHOUT AN EXPLICIT AUTHORITATIVE
EFFECT SEMANTIC STATE." New revisions carry either a NON-EMPTY authoritative
snapshot or an authoritative EMPTY snapshot; legacy MISSING never exists.
MISSING context = INVALID/CORRUPT (findById/restore/hydrate FAIL CLOSED).
Legacy authority types physically deleted:
AuthoredEffectSemanticAuthority, EffectSemanticBinding,
RevisionOwnedEffectProjection (CF6-CF8 ClassNotFound-verified).

## 4. One canonical write model

saveRevision (no-Effect) and saveRevisionWithEffects (Effect-bearing) both
delegate to saveRevisionInternal — ONE canonical writer
(ONE_CANONICAL_TIMELINE_REVISION_WRITE_PATH_MODEL_V1). Caller cannot choose:
snapshotId, registry/store implementation, target context, expected reference,
revision semantic digest. Caller-supplied definitions are admitted only through
the definition-version registry — the FINAL semantic authority ((id, version) ->
exactly one content digest forever, D1 durable).

## 5. Writer matrix (from real code)

| Writer | Effect source | authority | context | full digest | same TX | head | bypass |
|---|---|---|---|---|---|---|---|
| saveRevision | EMPTY mint | instance authority | revctx_ | yes | yes | yes | 0 |
| saveRevisionWithEffects | mintFromAuthoredState | instance authority | revctx_ | yes | yes | yes | 0 |
| restoreRevision | historical context reuse | authority | revctx_ | yes | yes | yes | 0 |
| patch apply | -> saveRevision | authority | revctx_ | yes | yes | yes | 0 |

BYPASS = 0. No writer can produce a valid revision without semantic context,
Effect reference, revision semantic digest.

## 6. Digest-domain audit

- Timeline semantic comparison / patch base: semanticContext.timelineContentDigest
  (TimelinePatchApplicationService apply + preview, VerifiedTimelineRevisionFactory)
- Revision identity / persistence: TimelineRevision.contentDigest ==
  context.revisionSemanticDigest (CONTENT_HASH column, findById construction)
- Effect identity: Effect snapshot content digest (store immutability, codec)
No ambiguous mixed use.

## 7. Evidence — exact-SHA FCV (--rerun-tasks)

Implementation SHA: 37c8d3699425810370c30dbca22c1bc81ce409fd
Tree: f26fa3366c5a5e0e22c4bf12f2445af3f7c99b3d
Parent (base): fe42b877
Main/origin-main: 07de009205e0ee50cad06e5a324ce18f5c46b10d (unchanged)

- FULL SUITE: 7652 tests / 0 failures / 0 errors / 43 skipped (961 result files, 19m48s)
- render-module: 2935/0/0/19
- timeline-module: 790/0/0
- platform-app: 569/0/0/20 (incl. ModularityTest, Roadmap20ProductionWiringTest,
  EffectSnapshotPersistenceIntegrationTest 5/5, C1CrrMergeAuthorityCompositionTest)
- C20 structural guard: PASS (55 files)
- verifyC1Cnm1RedGates (drift + fail-closed gates): PASS
- Modulith: PASS
- platform-app bootJar: PASS
- pfirr1RemediationCheck: PASS
- git diff --check: PASS
- V1-only Flyway governance: PRESERVED

## 8. Test matrices

### AI1-AI20 (20/20)
AI1 revision owns exact pin (Roadmap20AIIntegrationAcceptanceTest)
AI2 no-Effect -> authoritative EMPTY
AI3 Effect-bearing -> NON-EMPTY
AI4 caller cannot substitute expectedReference (foreign snapshot FAIL CLOSED)
AI5 caller cannot choose snapshotId (authority-generated)
AI6 MISSING = INVALID/CORRUPT
AI7-9 same-transaction writes (SnapshotIntegrationTest + CheckpointAPinRollbackIT)
AI11-13 legacy types physically absent
AI14-15 durable Spring wiring (Roadmap20ProductionWiringTest: Jdbc registry +
  Jdbc store + Jdbc revctx store; authority mints through durable deps)
AI16-17 real reload + render consumption (E2E-A/B)
AI18 definition concurrency (Roadmap20DefinitionConcurrencyAndCorruptionTest:
  serial + concurrent conflicting digests — exactly one wins, FAIL CLOSED)
AI19 corrupt esnap_ row FAIL CLOSED
AI20 track-type authority (MT1-4)

### CF1-CF10 (10/10) — Roadmap20CleanForwardGuardTest
CF1 revision requires semanticContext; CF2 context requires Effect reference
and the single revision-semantics contract; CF3 no-Effect -> EMPTY; CF4
Effect-bearing -> NON-EMPTY; CF5 missing context unreachable/corrupt;
CF6/7/8 legacy types physically absent; CF9 hydrate verifies committed Timeline
digest (no semantic mutation); CF10 restore carries context (no MISSING branch).

### TX1-TX5 (Roadmap20TransactionAtomicityTest)
TX1 definition identity failure -> no revision/snapshot/head (real PG verified)
TX2 snapshot store failure -> no revision/snapshot/head
TX3 revision persistence -> governed rows exactly (snap + esnap + revctx)
TX4 context/restore failure -> no partial rows, no head
TX5 head update -> exactly the new revision

### MT1-MT4 / PV1-PV4 (Roadmap20MediaTypeAndParameterValidationTest)
MT1 arbitrary trackId + AUDIO + AUDIO def PASS; MT2 trackId "audio" + VIDEO +
VIDEO def PASS (no heuristic); MT3 AUDIO track + VIDEO-only def FAIL CLOSED;
MT4 VIDEO track + AUDIO-only def FAIL CLOSED.
PV1 known parameter PASS; PV2 unknown FAIL CLOSED; PV3/PV4 NOT_REPRESENTABLE
(document§14: typed parameters Map<String,String>; schema has no requiredness).

### Corruption matrix (AI19 + registry)
CR1 malformed esnap JSON FAIL CLOSED; CR2/CR3 digest mismatch fail-closed
(registry/verify); CR4 unsupported contract rejected by codec; CR5 id mismatch
FAIL CLOSED (findById); CR6/CR7 revctx codec recomputes digest and requires
Effect reference (FAIL CLOSED); CR8 missing/wrong snapshot -> verified factory
FAIL CLOSED.

### Canonical 37
RP1-5, SA1-5, D1-6, SO1-4, L1-5, R1-5, BI1-5 all green within render 2935/0/0/19
and timeline 790/0/0 (R6AcceptanceTest 35/35, EffectSemanticSnapshotFinalAcceptanceTest 16/16).

## 9. E2E evidence (Roadmap20E2ESaveReloadRenderIntegrationTest)

E2E-A: no-Effect save -> authoritative EMPTY pin -> reload -> exact verification
-> RenderPlan ZERO Effect nodes.
E2E-B: typed Effect-bearing saveRevisionWithEffects -> NON-EMPTY snapshot ->
durable store -> revision-owned exact reference -> full revision semantic digest
-> reload -> exact snapshot resolution -> verification -> RenderPlan with
complete Effect WHAT (definition id/version/digest, parameters, enabled,
application range, target preserved).

## 10. History (append-forward only)

9538e73e (BASE) -> ... -> bf7a2702 -> 230b06c1 (prev FINAL_IMPLEMENTATION)
-> fe42b877 (prev FINAL_PUBLICATION) -> 37c8d369 (CORRECTION_IMPLEMENTATION)
-> <CORRECTION_PUBLICATION>
No amend/reset/rebase/squash/force.

## 11. Governance

- Implementation commit: 37c8d3699425810370c30dbca22c1bc81ce409fd
- Publication commit: (see report)
- Evidence: /tmp/ROADMAP20_AUTHORITY_INTEGRATION_CORRECTION_FCV/ (built from
  this run) + prior /tmp/ROADMAP20_EFFECT_SEMANTIC_SNAPSHOT_REVISION_PIN_FINAL_IMPLEMENTATION/
- Blockers: none material
- Architecture escalation: NONE
- main changed: NO (07de0092 unchanged)
- Merge: NO
- Roadmap #20 closed: NO
- #21/#22 started: NO

## 12. Deferred non-goals (unchanged)

automation subsystem, full effect catalog, Physical Planner, Operation Algebra,
semantic rewrite, cost optimizer, polyglot algorithm providers, user compute.
