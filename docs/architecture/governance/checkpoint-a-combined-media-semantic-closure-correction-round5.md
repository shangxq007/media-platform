# CHECKPOINT_A — COMBINED MEDIA SEMANTIC CLOSURE — ROUND 5 CORRECTION RECORD (PUBLICATION)

## Authority status
- Round 3 independent review (ChatGPT): FAIL_CORRECTABLE
- Round 4 independent review (ChatGPT): FAIL_CORRECTABLE
- Round 5 = append-forward correction on top of Round-4 publication (history NOT rewritten)
- ARCHITECTURE_ESCALATION_REQUIRED: NO (architecture premise valid; bounded closure)
- ROADMAP20_START_AUTHORIZED: NO (only independent ChatGPT review may set YES)

## Base (Round-4 publication, frozen)
- ROUND4_PUBLICATION_SHA: c82da1556365ecdb7f72017f0371a80a9b1cf3af
- ROUND4_PUBLICATION_TREE: a8410c6f415057fb40c2910ef097154879844859
- origin/main: 1d0d2d7e71d1f0fce72d04dda8057fc547b903f7 (unchanged)

## Round-5 implementation candidate (frozen)
- ROUND5_IMPLEMENTATION_SHA: __FILL_AFTER_FREEZE__
- ROUND5_IMPLEMENTATION_TREE: __FILL_AFTER_FREEZE__
- ROUND5_IMPLEMENTATION_PARENT: c82da1556365ecdb7f72017f0371a80a9b1cf3af
- branch: agent/checkpoint-a-semantic-closure (append-forward, no amend/rebase)
- scope drift: NONE

## Round-5 closure of the four remaining independent blockers

### R5-A — TRANSITION / AUTOMATION LOCAL SEMANTIC AUTHORITY FINAL CLOSURE
- TransitionCanonicalSemantics authority re-homed on the DOMAIN value
  CanonicalTransition (canonicalmodel); the diff snapshot
  CanonicalTimelineTransitionSnapshot is now merge TRANSPORT ONLY via
  toSnapshotValue/fromSnapshotValue — it never defines the canonical contract.
- AutomationCanonicalSemantics authority re-homed on the DOMAIN value
  CanonicalAutomationCurve / CanonicalAutomationKeyframe; the diff snapshot is
  merge transport only.
- STRICT decode (fail-closed): Transition requires transitionDefinitionId,
  transitionDefinitionVersion, outgoingClipId, incomingClipId, mediaType,
  durationTicks, durationTimeScale, alignment, temporalPolicy. Automation
  requires targetEntityId, parameterPath, valueType, extrapolation, keyframes
  structure, per-keyframe keyframeId/timeTicks/timeTimeScale/value/
  interpolation. Missing/malformed → IllegalArgumentException. Explicitly
  empty keyframes array remains a valid zero-keyframe curve; a MISSING
  keyframes field is not.
- REMOVED synthesized defaults: Transition "1.0"/"VIDEO"/"CENTER_ON_CUT"/
  "USE_SOURCE_HANDLES"/implicit timeScale=1; Automation
  "float"/"HOLD"/"LINEAR"/0.0/generated kf_N/implicit timeScale=1.
- Patch applier reconstruction now goes through fromCanonicalJson →
  toSnapshotValue (domain-value authority; fail-closed).
- Evidence: CheckpointARound5StrictDecodeTest 23/23 PASS (Transition 10 +
  Automation 13: every REQUIRED field individually removed → FAIL CLOSED;
  round-trip exactness; deterministic keyframe ordering; empty-keyframes
  validity; fingerprint sensitivity). R4 regression suites remain green.

### R5-B — TYPED TIMELINE SOURCE BINDING FINAL CLOSURE
- TimelineCandidate.Clip REMOVED the five independent flat source fields
  (sourceKind / mediaAssetId / mediaStreamId / artifactId / contentDigest);
  the typed TimelineSourceBinding is the SINGLE semantic authority
  (compile-time + reflection proof: exactly 10 record components, no flat
  source accessors).
- Flat wire input (sourceKind/mediaAssetId/mediaStreamId/artifactId/
  contentDigest on the clip node) is canonicalized IMMEDIATELY into one typed
  MediaStreamSourceBinding at the adapter boundary (fromFlatFields) — flat
  semantic state never survives into Candidate/merge.
- Partial binding intent, unknown sourceKind, malformed digest, missing/
  malformed source range → FAIL CLOSED (TimelineCanonicalRejectionException).
  No catch→null narrowing, no MediaAssetId-only fallback, no SourceBindingV2
  dual track.
- Intent rule: binding INTENT = presence of binding-specific fields
  (mediaStreamId/artifactId/contentDigest, plus mediaAssetId for the adapter
  wire path); sourceKind alone is a legacy projection, not intent (the
  TimelineClip document constructor FORCES sourceKind=MEDIA_STREAM when null).
- TimelineSnapshotConverter.toDocument REMOVED — zero production/test callers
  (dead code) and a documented semantic-loss path ("full binding restoration
  is a follow-up bounded delivery").
- Evidence: CheckpointARound5SourceBindingClosureTest 13/13 PASS (nested typed
  round-trip, flat→typed immediate canonicalization, no-intent null, partial
  fail-closed, unknown kind fail-closed, malformed digest fail-closed, missing
  artifactId/mediaStreamId/source-range fail-closed, sourceKind-alone not
  intent, exact digest/algorithm/range preservation, single typed authority
  reflection proof, no asset-only fallback).

### R5-C — ARTIFACT PIN INVARIANT BY CONSTRUCTION
- TimelineMergeEngine: exactly ONE public 9-arg production constructor;
  7-arg null-forwarding constructor REMOVED; ALL nine dependencies
  requireNonNull; NO @Autowired (constructor injection via sole constructor);
  nullable pin-skip (`artifactPinValidator != null && artifactPinService !=
  null`) REMOVED — the pin boundary runs unconditionally.
  PERSISTENT_MERGE_WITHOUT_PIN_BOUNDARY = IMPOSSIBLE_BY_CONSTRUCTION.
- TimelineRevisionSaveService: exactly ONE public 6-arg constructor; ALL six
  dependencies requireNonNull; NO @Autowired; nullable skips REMOVED in
  saveRevision (validator null check), tx pin registration
  (artifactPinService != null), and restoreRevision (copyRevisionPinsTx
  conditional). save/restore can never skip pin persistence.
- CheckpointAPinInvariantTest.case6 re-written: null pin boundary is now
  REJECTED BY CONSTRUCTION (NullPointerException assertions for null
  validator / null pinService / null dsl). case4 migrated to the Tx API mock
  (registerRevisionPinsTx).
- Real PostgreSQL evidence (zero mock pin service / repository):
  - R5-C6 REAL_DB_FAILURE_PATH: validator VALID (ArtifactQueryService mock
    answers artifact exists) → real ArtifactPinRepository INSERT fails
    artifact_pin FK (fk_pin_artifact → artifact.id; artifact row intentionally
    absent) → whole save transaction rolls back. 3/3 PASS
    (CheckpointARound4RealPinAtomicityIT: success path + FK failure + partial
    pin rollback).
  - R5-C7 REAL_PARTIAL_PIN_ROLLBACK: TWO pins — pin 1 references a REAL
    artifact row (its INSERT succeeds inside the transaction), pin 2
    references a ghost artifact (INSERT fails the FK). After rollback:
    artifact_pin count for the new revision = 0 (no partial pin set survives),
    no revision row, head unchanged.
  - R5-C10 PERSISTENT_MERGE_REAL_DB: real TimelineMergeEngine.merge(...) (NOT
    mergeSemantic) against real PostgreSQL. SUCCESS: base/source/target
    internal-1.0 revisions load, real semantic merge, NEW merge revision
    created, exact pin rows registered for the NEW merge revision id, head
    advances. FAILURE: real pin INSERT FK failure inside the merge transaction
    → merge revision rolled back, no pin rows, head unchanged. 2/2 PASS
    (CheckpointARound5PersistentMergePinIT).
  - Patch path (R5-C9): CheckpointARound4PatchPathPinIT 3/3 PASS (valid pin →
    new revision + pins + head; digest mismatch → rejected; pin persistence
    failure → whole patch write rollback).
  - Restore path (R5-C8): CheckpointARound4RestorePinCopyIT success 2/2 PASS
    (new restored revision id gains exact own pin rows copied from the
    historical revision; failure path is MOCK_FAILURE_INJECTION — classified
    honestly below; real-DB restore pin-copy failure is not independently
    triggerable because the copied rows are the historical revision's already-
    FK-valid pins).

### R5-D — EVIDENCE / GUARDS / MATRICES
- Guards H17-H22 added (verifyTimelineEffectTransitionCanonicalization):
  - H17: no explicit @Autowired on corrected surfaces; exactly ONE public
    constructor per service; requireNonNull pin boundary.
  - H18: no nullable pin-skip in TimelineMergeEngine /
    TimelineRevisionSaveService.
  - H19: Transition/Automation authority defined over DOMAIN values; no
    synthesized defaults (asText("1.0")/asText("VIDEO")/asText("HOLD")/
    asText("LINEAR")/asDouble(0.0)/kf_/asLong(1) forbidden).
  - H20: TimelineCandidate.Clip has no flat source semantic fields; carries
    typed TimelineSourceBinding.
  - H21: no silent catch→null narrowing in adapter / document mapper.
  - H22: R5 behavior + real-PG tests exist.
  - G1 updated: no-pin save surface impossible by construction (requireNonNull).
- Component semantic authority matrix: regenerated from source (see
  checkpoint-a-component-authority-matrix.md) — all eight components owned by
  their local domain authorities; Transition/Automation domain models are
  CanonicalTransition / CanonicalAutomationCurve; TimelineCandidate has NO
  independent flat source authority.
- Revision write surface matrix: regenerated from call graph (see
  checkpoint-a-revision-write-surface-matrix.md) — every revision-creating
  surface carries canonical gate + pin boundary + CAS head protection;
  RevisionCommandApplyService / OperationPlanApplyService confirmed
  domain-neutral GENERIC_REVISION_MECHANICS with ZERO production callers (not
  Spring beans, test-only instantiation); no Timeline semantic bypass.

## Real PostgreSQL evidence classification (honest)
- REAL_DB_SUCCESS_PATH: CheckpointARound4RealPinAtomicityIT
  (realPinRepositorySuccessCommitsRevisionPinsAndHead), CheckpointARound4-
  RestorePinCopyIT (restoreCopiesExactPinsToNewRevision), CheckpointARound4-
  PatchPathPinIT (valid), CheckpointARound5PersistentMergePinIT (success).
  All use real ArtifactPinRepository/ArtifactPinService; only the
  ArtifactQueryService validation seam is mocked.
- REAL_DB_FAILURE_PATH: CheckpointARound4RealPinAtomicityIT
  (realPinRepositoryFkViolationRollsBackWholeSave — validator VALID,
  failure layer DATABASE, failure operation ArtifactPinRepository INSERT,
  constraint fk_pin_artifact, rollback asserted: revision/pins/head),
  CheckpointARound4RealPinAtomicityIT (realPinRepositoryPartialPinWrite-
  RollsBackEntirely), CheckpointARound5PersistentMergePinIT (failure).
  VALIDATOR_RESULT = VALID for all; FAILURE_LAYER = DATABASE for all.
- MOCK_FAILURE_INJECTION: CheckpointARound4RestorePinCopyIT
  (restorePinCopyFailureRollsBackEverything — mocked ArtifactPinService
  throws; documented as unit-level supplement, NOT called "real DB failure"),
  CheckpointAPinInvariantTest.case4.
- UNIT: CheckpointAPinInvariantTest (cases 1-6), R5-A/R5-B behavior suites.
- SOURCE_GUARD: verifyTimelineEffectTransitionCanonicalization (H1-H22).

## Test / gate results (exact, from JUnit XML on the frozen candidate)
- __FILL_AFTER_FCV__

## Historical honesty
- Round-3 independent review: FAIL_CORRECTABLE (unchanged record)
- Round-4 independent review: FAIL_CORRECTABLE (unchanged record)
- Round-5: READY_FOR_CHATGPT_INDEPENDENT_FINAL_REVIEW (Hermes proposal only)

## Explicit final state
- CHECKPOINT_A_STATUS = READY_FOR_CHATGPT_INDEPENDENT_FINAL_REVIEW
  (only if Round-5 FCV PASS)
- PROPOSED_CHECKPOINT_A_VERDICT = PASS (Hermes proposal; ChatGPT decides)
- ARCHITECTURE_ESCALATION_REQUIRED = NO
- ROADMAP20_START_AUTHORIZED = NO
- main unchanged: 1d0d2d7e71d1f0fce72d04dda8057fc547b903f7
- branch unmerged; awaiting ChatGPT independent review of the exact pushed
  Round-5 implementation SHA/tree.
