# Lead Attestation Decisions

## A. Memory Removal

**Decision:** EXACT_CLOSEOUT_STATUS_MEMORY_REMOVED

The CLOSEOUT.2B task updated an existing Memory entry with closeout status. This was a persistent-memory modification (even though not self-improvement). The exact closeout-status content was removed. Unrelated project Memory preserved. No replacement entry written.

**Final Memory language:**
```
persistent memory modified during CLOSEOUT.2B: YES (existing entry updated)
persistent memory modified during this task: YES — exact removal only
new persistent memory written during this task: NO
task-created closeout-status memory: REMOVED
```

## B. JUnit Statistics

**Decision:** BOTH_RUNS_EXACTLY_RECONCILED

**Run 1 (.2B):**
- total: 5,693
- passed: 5,652
- failures: 0
- errors: 0
- skipped: 41
- arithmetic: 5,652 + 0 + 0 + 41 = 5,693 ✅

**Run 2 (.2B):**
- total: 5,693
- passed: 5,652
- failures: 0
- errors: 0
- skipped: 41
- arithmetic: 5,652 + 0 + 0 + 41 = 5,693 ✅

Previous report error: "passed = 5,693" → corrected to "passed = 5,652"

## C. Kanban

**Actual task IDs:**
- BACKEND-INTEGRITY-RESTORE-GREEN-TEST-BASELINE.2: t_e0605003 (done)
- CLOSEOUT.2B: not a kanban task (sub-agent work)
- ATTESTATION-CORRECTION.2C: not a kanban task (evidence correction)
- ARCH-DOC-GOV-INVENTORY-AND-CLASSIFICATION.1: to be created after Agent E accepts
- DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0: blocked (no kanban task yet)

## D. Evidence Corrections

Files to correct:
- 12-final-decision.md: Memory claim, JUnit stats
- 08-provider-durability-proof.md: JUnit stats (if present)
- 10-forced-test-verification.md: JUnit stats
- 11-evidence-matrix.md: Memory claim, JUnit stats
- Agent E report: JUnit stats reference

## E. Allowed Evidence Files

```
.agent-tasks/BACKEND-INTEGRITY-GREEN-BASELINE-CLOSEOUT.2B/**
.agent-tasks/BACKEND-INTEGRITY-GREEN-BASELINE-ATTESTATION-CORRECTION.2C/**
```

No other paths may change.
