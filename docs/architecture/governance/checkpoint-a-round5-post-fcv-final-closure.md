# CHECKPOINT_A — ROUND5_POST_FCV_FINAL_CLOSURE RECORD (PUBLICATION)

## Authority status
- Round 3 independent review (ChatGPT): FAIL_CORRECTABLE (unchanged record)
- Round 4 independent review (ChatGPT): FAIL_CORRECTABLE (unchanged record)
- Round 5 independent review (ChatGPT): FAIL_CORRECTABLE — this closure addresses
  its two remaining material blocker groups (F1 transaction boundary, F2
  canonical strictness). NO new architecture work; narrow append-forward only.

## Round-5 overclaims corrected here (spec section 24 — honest record, no rewrite)
1. Round-5 persistent merge failure test did NOT exercise the actual production
   merge failure path (it manually reproduced dsl.transactionResult + pin
   registration instead of calling TimelineMergeEngine.merge(request)).
2. Round-5 production merge used a self-invocation-sensitive transaction shape
   (merge(request) → this.merge(request, Map.of()) with @Transactional only on
   the overload; Spring proxy self-invocation does not apply transactional
   advice).
3. Round-5 canonical SourceBinding decoder ignored the authored
   contentDigest.algorithm (hardcoded SHA_256, silently normalizing unknown /
   missing algorithms).

## FINAL_CLOSURE_F1 — persistent merge transaction boundary (mechanically real)
TimelineMergeEngine:
- @Transactional REMOVED from both merge overloads (no Spring self-invocation
  dependency; PERSISTENT_MERGE_SELF_INVOCATION_TRANSACTION_BYPASS = 0).
- Persistent write phase = ONE explicit jOOQ transaction:
  dsl.transactionResult(tx -> { snapshot → revision → pins → head })
- Every write is transaction-aware through tx.dsl():
  - snapshotService.saveTx(tx.dsl(), ...)
  - revisionRepository.insertTx(tx.dsl(), mergeRow) (NEW tx-aware API;
    nextRevisionNumberTx NEW tx-aware API)
  - artifactPinService.registerRevisionPinsTx(tx.dsl(), ...)
  - currentRevisionService.updateCurrentRevisionTx(tx.dsl(), ...) — head CAS
    with authoritative expected-vs-actual check INSIDE the transaction (no
    check-then-act race)
- Semantic computation / pin extraction / pin validation remain OUTSIDE the
  write transaction (fail-early, no long-held DB tx for pure computation).
- Merge engine constructor: exactly ONE (10-arg, dsl added), requireNonNull
  all, no @Autowired.

TRUE production-path real-PG failure test (CheckpointARound5PersistentMergePinIT):
- Calls the real TimelineMergeEngine.merge(request) — the production public
  persistent merge entrypoint (NOT a manual transactionResult reproduction).
- VALIDATOR_RESULT = VALID for every artifact id (ArtifactQueryService mock
  seam) — the DB decides.
- Merged payload carries TWO pins: pin 1 → art-1 (REAL artifact row, INSERT
  succeeds in-tx) and pin 2 → ghost-art (no row → artifact_pin FK failure).
- After the production merge throws: merge revision rows = 0, snapshot = 0
  leaked, artifact_pin rows = 0 (partial-write erasure proven), head unchanged.
- Classification: REAL_DB_FAILURE_PRODUCTION_PATH + REAL_DB_PARTIAL_WRITE_ROLLBACK.

## FINAL_CLOSURE_F2 — canonical strictness / lossless codec
TimelineSourceBindingCanonicalSemantics:
- contentDigest.algorithm REQUIRED: missing / non-object / blank / unknown →
  FAIL CLOSED (decoder reads AND validates the authored algorithm; no SHA_256
  hardcode). Canonical domain value = "SHA_256" (DigestAlgorithm.name()).
- Legacy wire alias "SHA256" accepted ONLY at the clearly-identified adapter
  boundary (InternalTimelineCandidateAdapter.sourceBindingOf maps SHA256 →
  SHA_256 before delegating; canonical decoder itself rejects "SHA256").
- Flat sourceRange: ALL required fields (rate.num, rate.den, start.frame,
  duration.frame) must be present integral numbers — NO semantic defaults
  (no fps=30 / den=1 / frame=0 synthesis); non-positive rate / negative frames
  / overflow → FAIL CLOSED; exact rational conversion (no floating point).
- Roundtrip: canonicalValue(binding) → fromCanonicalValue → identical binding
  (algorithm, value, asset id, stream id, artifact id, exact range).

TransitionCanonicalSemantics (F2 numeric strictness):
- durationTicks / durationTimeScale require INTEGRAL JSON nodes
  (isIntegralNumber) — strings/booleans/objects/arrays FAIL CLOSED (no Jackson
  coercion).

AutomationCanonicalSemantics (F2 numeric strictness):
- Every keyframe timeTicks / timeTimeScale require integral JSON nodes; value
  must be a finite JSON number (NaN/Infinity rejected).

## Guards (verifyTimelineEffectTransitionCanonicalization)
F23: no @Transactional on TimelineMergeEngine (self-invocation bypass = 0)
F23b: explicit dsl.transactionResult write transaction present
F24: registerRevisionPinsTx used (non-Tx registerRevisionPins absent)
F24c/d/e: saveTx / insertTx / updateCurrentRevisionTx used
F24f: TimelineRevisionRepository exposes insertTx + nextRevisionNumberTx
F24g: real-PG failure test calls mergeEngine.merge(request)
F25: decoder consumes contentDigest.algorithm
F26: no hardcoded SHA_256 ignoring authored algorithm
F27: flat source-range parser has no semantic defaults
F28: Transition/Automation decoders require integral JSON nodes
F28c: F2 strict-codec test matrix exists

## Matrices (regenerated from final code)
- checkpoint-a-revision-write-surface-matrix.md: TimelineMergeEngine.merge row
  now shows TRANSACTION_MECHANISM=EXPLICIT_JOOQ, SELF_INVOCATION_TRANSACTION_
  BYPASS=NO, all writes TX_AWARE, SAME_PHYSICAL_TRANSACTION_PROVEN=YES,
  BYPASS_POSSIBLE=NO; REVISION_WRITE_SURFACE_BYPASS_COUNT=0.
- checkpoint-a-component-authority-matrix.md: TimelineSourceBinding row updated
  with strict digest-algorithm and no-default flat-range evidence.

## Test / gate results (exact, from JUnit XML on the frozen candidate)
- FULL_SUITE: 7324 tests / 0 failures / 0 errors / 43 skipped (39 modules)
- timeline-module: 752 / 0 / 0 / 0 (incl. FINAL_CLOSURE_F2 strict codec 25/25,
  R5-A strict decode 23/23, R5-B closure 13/13)
- audio-module: 22 / 0 / 0 / 0
- render-module: 2754 / 0 / 0 / 19
- platform-app: 562 / 0 / 0 / 20
- REAL_POSTGRES pin gates: 10/10 (CheckpointARound4RealPinAtomicityIT 3,
  CheckpointARound4RestorePinCopyIT 2, CheckpointARound4PatchPathPinIT 3,
  CheckpointARound5PersistentMergePinIT 2 — the latter BOTH call the real
  TimelineMergeEngine.merge(request) production entrypoint; failure test =
  REAL_DB_FAILURE_PRODUCTION_PATH with VALID validator + real FK on pin 2)
- bootJar: PASS
- pfirr1RemediationCheck: PASS
- verifyTimelineEffectTransitionCanonicalization (H1-H22 + G1-G8 + F23-F28): PASS
- ALL 20 repository verify tasks: PASS
- Modulith: N/A — no standalone Modulith task exists; the repository's
  boundary/drift equivalent is verifyJooqNamedInterfacePreservation (PASS),
  same substitute as Round-3/4/5 records.
- git diff --check: PASS

## Historical honesty
- Round-3 independent review: FAIL_CORRECTABLE (unchanged)
- Round-4 independent review: FAIL_CORRECTABLE (unchanged)
- Round-5 independent review: FAIL_CORRECTABLE (unchanged)
- This record ADDS the Round-5 overclaim correction above; it does NOT rewrite
  any previous record.
