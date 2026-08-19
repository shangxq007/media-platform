# CHECKPOINT_A — POST_FINAL_REVIEW BOUNDED CLOSURE RECORD (PUBLICATION)

## Frozen candidate (exact)
- POST_FINAL_REVIEW_IMPLEMENTATION: 2fdd95c69719066f1a4d9a7eafa9e15d75fd07b4
- POST_FINAL_REVIEW_IMPLEMENTATION_TREE: 677f3f9fb7a6b00e7ca2e4728c4a938440b6abc5
- POST_FINAL_REVIEW_IMPLEMENTATION_PARENT: 79172509daa4bb776aaa3ddd46e169b0c8178b4b
- POST_FINAL_REVIEW_FCV: PASS
- CHECKPOINT_A_STATUS: READY_FOR_CHATGPT_INDEPENDENT_FINAL_REVIEW (proposal)
- ROADMAP20_START_AUTHORIZED: NO

## Authority status (unchanged history — no rewriting)
- Round 3 independent review (ChatGPT): FAIL_CORRECTABLE
- Round 4 independent review (ChatGPT): FAIL_CORRECTABLE
- Round 5 independent review (ChatGPT): FAIL_CORRECTABLE
- Round5 POST-FCV FINAL_CLOSURE independent review: FAIL_CORRECTABLE
- Latest independent review (post-final-closure): FAIL_CORRECTABLE — this
  record closes its final TWO material blocker classes (P1, P2). No new
  architecture work; narrow append-forward only.

## Latest independent findings (recorded, then closed)
1. ProductCurrentRevisionService.updateCurrentRevisionTx was transaction-aware
   but NOT a DB-enforced CAS: it used SELECT → Java compare → unconditional
   UPDATE by product_id only (check-then-act). The previous publication's
   "head CAS / no check-then-act race" wording was an overclaim — corrected
   append-forward here.
2. A PRESENT-but-malformed nested sourceBinding (string / array / null /
   number / boolean / empty object) could fall through to flat-field detection
   and silently become null/absence.
3. The TimelineDocument path could synthesize a missing exact source range as
   0..0 via TimelineClip's ZERO defaults for trimStart/trimEnd.

## P1 — DB-ENFORCED HEAD CAS (closed)
ProductCurrentRevisionService.updateCurrentRevisionTx is now a REAL conditional
database update:
- expected != null → UPDATE ... WHERE product_id = ? AND current_revision_id = ?
- expected == null → UPDATE ... WHERE product_id = ? AND current_revision_id IS NULL
- affected rows == 1 is the ONLY correctness authority; 0 rows → diagnostic
  SELECT (same tx) ONLY to distinguish product-missing from stale-head, then
  TimelineConflictException. No read-then-act anywhere in the CAS method.
- Persistently used by TimelineMergeEngine.merge write transaction via
  updateCurrentRevisionTx(tx.dsl(), ...) — same physical transaction, real CAS.
- Real-PG proof (CheckpointAPostFinalReviewHeadCasIT, 3/3):
  - casSingleWriterSucceeds            REAL_DB_CAS_SINGLE_WRITER
  - casStaleExpectationFailsClosed     REAL_DB_CAS_STALE_EXPECTATION
  - concurrentWritersSingleWinner      REAL_DB_CAS_CONCURRENT_WRITERS
    (two writers race the same persisted head R100; exactly SUCCESS_COUNT=1 /
     CONFLICT_COUNT=1; final head = winner's revision)

## P2-A — PRESENT-malformed sourceBinding fails closed (closed)
InternalTimelineCandidateAdapter.sourceBindingOf now distinguishes:
- clipNode.has("sourceBinding") == false → legacy flat detection (null allowed)
- PRESENT field → must be a non-empty object; null / string / array / number /
  boolean / empty object → TimelineCanonicalRejectionException (FAIL CLOSED).
TimelineSourceBindingCanonicalSemantics.fromCanonicalValue: only a null Java
reference means caller-level absence; NullNode / non-object / empty object
JsonNode → IllegalStateException (FAIL CLOSED).
NESTED_SOURCE_BINDING_SILENT_ABSENCE_COUNT = 0.

## P2-B — TimelineDocument exact source range required (closed)
- TimelineClip no longer defaults trimStart/trimEnd to MediaTime.ZERO —
  MISSING (null) is preserved and distinguishable from an authored zero.
- TimelineDocumentCandidateMapper.typedBindingOf: binding intent (mediaStreamId
  / artifactId / contentDigest present) requires mediaAssetId + mediaStreamId +
  artifactId + contentDigest + trimStart + trimEnd; missing range → FAIL CLOSED
  (never 0..0 synthesis). No intent → null allowed. Authored zero range
  survives as 0..0 (distinct from missing).
- CanonicalTimelineClipSnapshot.toTypedBinding: intent + null sourceStart/
  sourceDuration → FAIL CLOSED.
TIMELINE_DOCUMENT_MISSING_RANGE_SYNTHESIS = 0.

## Guards (verifyTimelineEffectTransitionCanonicalization)
G29  expected revision in UPDATE predicate (eq / IS NULL)
G30  no read-then-act: conditional UPDATE precedes any diagnostic read
G30b affected-row count == 1 enforced
G30c real-PG CAS concurrency test exists
G31  adapter distinguishes absent from present sourceBinding
G31b present-but-malformed fails closed
G32  canonical decoder rejects malformed JsonNode roots
G33  document mapper does not synthesize ZERO for missing range
G34  TimelineClip preserves null trimStart/trimEnd
G34b P2 boundary test matrix exists

## Matrix evidence correction (CAPABILITY ≠ REACHABILITY)
RevisionCommandApplyService and OperationPlanApplyService are UNUSED_INTERNAL_
MECHANICS: MECHANICALLY_CAN_CREATE_REVISION = YES (they contain revision
insertion / head-advance capability), PRODUCTION_REACHABLE = NO (zero
production callers), SPRING_BEAN = NO, BYPASS_POSSIBLE = NO. Capability is not
equated with reachability. OperationPlanApplyService's conditional head CAS
(WHERE head = expected; rows==1) served as the SQL reference for P1.

## Test / gate results (exact, from JUnit XML on the frozen candidate)
- FULL_SUITE: 7346 tests / 0 failures / 0 errors / 43 skipped (39 modules)
- timeline-module: 771 / 0 / 0 / 0 (incl. P2 strict-boundary 19/19, F2 strict
  codec 25/25, R5-A 23/23, R5-B 13/13)
- audio-module: 22 / 0 / 0 / 0
- render-module: 2757 / 0 / 0 / 19 (incl. P1 real-PG head CAS 3/3)
- platform-app: 562 / 0 / 0 / 20
- REAL_POSTGRES_GATES: 13/13 (pin ITs 10 + head-CAS IT 3)
- REAL_PG_CAS_CONCURRENCY_TESTS: 3 (single-writer / stale-expectation /
  concurrent-writers — the concurrent test proves SUCCESS_COUNT=1,
  CONFLICT_COUNT=1, final head = winner)
- SOURCE_BINDING_STRICT_BOUNDARY_TESTS: 19 (11 nested present-malformed +
  4 canonical root + 7 document range incl. missing-vs-zero distinction)
- bootJar: PASS
- pfirr1RemediationCheck: PASS
- verifyTimelineEffectTransitionCanonicalization (H1-H22 + G1-G8 + F23-F28 +
  G29-G34): PASS
- ALL 20 repository verify tasks: PASS
- Modulith: N/A — no standalone Modulith task exists; the repository's
  boundary/drift equivalent is verifyJooqNamedInterfacePreservation (PASS),
  same substitute as all prior records.
- git diff --check: PASS

## Historical honesty
- All prior independent verdicts preserved (R3/R4/R5/POST-FCV FAIL_CORRECTABLE).
- This record ADDS the P1/P2 overclaim corrections; it does NOT rewrite any
  previous record.
