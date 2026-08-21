# ROADMAP20 FINAL INDEPENDENT ACCEPTANCE — EVIDENCE CORRECTION

## 1. Purpose and status

ROADMAP20_FINAL_IMPLEMENTATION_SHA            = 08696bd22457026a00a613c9e830db6e7bbf7c5d
ROADMAP20_FINAL_IMPLEMENTATION_TREE           = 2eaa75563dfa00429bad1d63d4f2ce5de58d4965
ROADMAP20_IMPLEMENTATION_INDEPENDENT_VERDICT  = PASS
MATERIAL_CODE_BLOCKERS                        = 0
ARCHITECTURE_ESCALATION                       = NONE
OPTION_B                                      = FROZEN

This document is a GOVERNANCE-ONLY evidence-accounting correction. It is the
superseding evidence ledger for exactly three accounting inaccuracies in the
immutable publication 35fd0c47 (roadmap-20-final-canonical-read-and-restore-
evidence-closure.md). It does NOT supersede: implementation SHA, architecture,
semantic contracts, test results themselves, FCV acceptance, Option B, or the
RenderPlan architecture. The accepted implementation 08696bd2 is NOT
challenged; no implementation/test/build file changed in this task.

35fd0c47 remains immutable historical publication evidence. Its implementation
conclusions were ACCEPTED. Its permanent evidence ledger contains three
accounting inaccuracies corrected here:

1. G1 — RST denominator / numbering
2. G2 — historical full-suite baseline comparability
3. G3 — test module attribution

## 2. G1 — RST accounting (13 numbered cases, RST_TX_HEAD separate)

Final test source (Roadmap20RevisionContextOwnershipAndRestoreTest, committed
tree 2eaa7556 at 08696bd2) contains exactly these numbered restore cases:

RST1, RST2, RST3, RST4, RST5, RST7, RST8, RST9, RST10, RST11, RST12, RST13, RST14

NUMBERING GAP RST6 IS HISTORICAL / INTENTIONAL; NO ACCEPTANCE EVIDENCE IS
CLAIMED FOR A NONEXISTENT TEST. (Verified: final source has zero `rst6`
method declarations; no test was renamed to make numbering contiguous.)

NUMBERED_RST_CASES = 13

Final accepted accounting:

RST NUMBERED CASES = 13/13 PASS  (RST1-RST5, RST7-RST14)

Separately (NOT folded into the numbered denominator):

RST_TX_HEAD = PASS

Corrected wording replaces any prior "RST 14/14" statement: the numbered RST
denominator is 13, and RST_TX_HEAD is a distinct transactional-injection test
counted on its own line.

## 3. G2 — Full-suite cross-publication baseline NOT comparable

Current exact FCV (fresh detached worktree at 08696bd2):

CURRENT_EXACT_FCV_SHA                        = 08696bd22457026a00a613c9e830db6e7bbf7c5d
CURRENT_REPORTED_FULL_SUITE                  = 7591 / 0 failures / 0 errors / 43 skipped

Previous immutable publication c525310c reported:

PREVIOUS_C525310C_PUBLICATION_REPORTED_FULL_SUITE = 7689 / 0 failures / 0 errors / 43 skipped

CROSS_PUBLICATION_TOTAL_TEST_COUNT_COMPARABILITY = NOT_ESTABLISHED

Reason: the historical publications do not establish that both full-suite
totals were aggregated with an identical counting method / discovery scope
(e.g. module discovery set, XML glob scope, inclusion of generated/adapter
modules). The prior statement "baseline c525310c = 7483" in 35fd0c47 is
withdrawn as unsupported; it contradicts the immutable c525310c publication
and no independently proven raw evidence backs the 7483 figure.

ROOT_CAUSE_OF_CROSS_PUBLICATION_COUNT_DIFFERENCE = UNRESOLVED
IMPACT_ON_CURRENT_ACCEPTANCE                   = NONE

(Current exact candidate independently passed its own clean-tree FCV and
affected-module regression evidence; UNKNOWN != fabricated explanation.)

No claim of "+108 from baseline", "-98 regression", or "7483 previous baseline"
is made anywhere in this correction.

## 4. G2 — High-confidence module evidence (preserved)

Current exact FCV module evidence (accepted):

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| render-module | 2976 | 0 | 0 | 19 |
| timeline-module | 790 | 0 | 0 | 0 |
| platform-app | 569 | 0 | 0 | 20 |

These per-module counts are attached to the exact committed tree 08696bd2 and
are NOT compared across publications as if directly comparable.

## 5. G3 — Test module attribution correction

Roadmap20RevisionContextOwnershipAndRestoreTest.java lives in:

render-module/src/test/java/com/example/platform/render/app/timeline/

Therefore corrected attribution (previous timeline-module attribution in
35fd0c47 §9.1 was wrong):

RST10     = render-module
RST11     = render-module
RST13     = render-module
RST14     = render-module
READOWN2  = render-module
READOWN3  = render-module
READOWN4  = render-module

Correct module wording:

- render-module = 2976 PASS, includes: corrected RST10, corrected RST11,
  RST13, RST14, READOWN2, READOWN3, READOWN4, and other Roadmap20
  integration evidence.
- timeline-module = 790 PASS, unchanged by this final test-fixture
  correction (the timeline-module EffectSemanticSnapshotFinalAcceptanceTest
  covers the canonical-37 / AI / CF / SC domain evidence in timeline-module).
- platform-app = 569 PASS.

## 6. Final accepted evidence table

| Evidence | Final accepted value |
|---|---|
| Implementation SHA | 08696bd22457026a00a613c9e830db6e7bbf7c5d |
| Implementation tree | 2eaa75563dfa00429bad1d63d4f2ce5de58d4965 |
| Current full suite | 7591 / 0 / 0 / 43 |
| Cross-publication full-suite comparability | NOT_ESTABLISHED |
| render-module | 2976 PASS |
| timeline-module | 790 PASS |
| platform-app | 569 PASS |
| AI | 20/20 PASS |
| CF | 10/10 PASS |
| TX | 5/5 PASS |
| OWN | 4/4 PASS |
| RCOWN | 6/6 PASS |
| SC | 9/9 PASS |
| Numbered RST | 13/13 PASS |
| RST_TX_HEAD | PASS |
| RSTOWN | 2/2 PASS |
| READOWN | 4/4 PASS |
| MT | 4/4 PASS |
| canonical | 37/37 PASS |
| E2E-A | PASS |
| E2E-B | PASS |
| definition concurrency | PASS |

## 7. Test-target table (retained, corrected attribution)

| Test | Actual object modified / exercised | Expected failure boundary |
|---|---|---|
| RST10 | DELETE esnap_ = revctx.effectReference().snapshotId (real esnap) | Effect snapshot owned lookup (render-module) |
| RST11 | revctx JSON → RB's REAL owned esnap_ (self-consistent codec rebuild) | Effect snapshot ownership boundary (render-module) |
| RST13 | none (restore executes) — verified Timeline payload directly reissued | byte equality historical vs restored (render-module) |
| RST14 | none (restore executes) — exact historical Effect reference preserved | reference equality, no remint (render-module) |
| READOWN2 | findPayloadDocument direct hydration (RA.snapshot_id → RB snapshot) | Optional.empty (render-module) |
| READOWN3 | patch apply with foreign snapshot | TIMELINE_PATCH_PAYLOAD_INVALID, zero writes (render-module) |
| READOWN4 | patch preview with foreign snapshot | TIMELINE_PATCH_PAYLOAD_INVALID, no mutation (render-module) |

## 8. Legacy / non-canonical read disclosure (preserved)

Some production paths still use unscoped TimelineSnapshotService.findPayload(String)
and TimelineSnapshotService.findById(String), classified as:

- render execution (RenderJobExecutionService, BaseJobTimelineLoader,
  PlanBasedTimelineRevisionRenderService, TimelineRevisionRenderService)
- legacy internal-1.0 (TimelineRevisionService read paths)
- admin/editor sync (TimelineAssetLifecycleService, TimelineEditorSyncService)

NOT Roadmap #20 canonical restore/patch/merge authority. Correct claim:

ROADMAP20_CANONICAL_UNSCOPED_TIMELINE_SNAPSHOT_LOOKUP_COUNT = 0

## 9. Unchanged implementation findings (PASS, preserved)

C1 = PASS  — RESTORE_REISSUES_EXACTLY_THE_VERIFIED_TIMELINE_PAYLOAD_V1
C2 = PASS  — CANONICAL_TIMELINE_PAYLOAD_READ_IS_PROJECT_AND_TENANT_SCOPED_V1
C3 = PASS  — RESTORE_EFFECT_SNAPSHOT_TESTS_MUST_TARGET_REAL_EFFECT_SNAPSHOT_AUTHORITY_V1
R3 = PASS  — AUTHORITATIVE_REVISION_ROW_IS_READ_AS_ONE_OWNERSHIP_VALIDATED_UNIT_V1

## 10. Scope of this correction

This document supersedes ONLY the three evidence statements above (RST
denominator/numbering; full-suite baseline comparability; test module
attribution). It does NOT supersede implementation SHA, architecture,
semantic contracts, test results, FCV acceptance, Option B, or RenderPlan
architecture.
