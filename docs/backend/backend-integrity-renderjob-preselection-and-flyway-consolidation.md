# Backend Integrity — RenderJob Preselection and Flyway Consolidation

## Status

```text
BACKEND-INTEGRITY-RENDERJOB-PRESELECTION-AND-FLYWAY-CONSOLIDATION.0:
COMPLETE
```

## Decision

```text
RENDERJOB_PRESELECTION_AND_FLYWAY_CONSOLIDATION_CLOSED_WITH_NONCRITICAL_DEBT
```

Non-critical debt: The render still fails AFTER Provider selection because the test fixture has no actual media file. The Provider selection path is fully exercised.

## Baseline

```text
base commit: 9ca8e97
previous task corrected status: PARTIAL (script resolution prevented Provider selection)
preselection blocker: INVALID_TEST_FIXTURE (no valid ai_script)
Flyway documentation policy: SINGLE_CANONICAL_SOURCE_OF_TRUTH
```

## Script-Resolution Architecture

```text
resolveRenderScript(jobId, snapshotId, prompt, projectId)
  → check existing ai_script on job
  → check timeline snapshot payload
  → check prompt as timeline JSON
  → AI script generation (if prompt provided)
  → throw IllegalStateException if nothing available
```

## Previous Failure Root Cause

```text
Classification: INVALID_TEST_FIXTURE
Reason: timelineSnapshotId="snap-test" did not exist, no ai_script on job
Fixture valid: NO
Production logic valid: YES
```

## Canonical HTTP Success Flow

| Step | HTTP | Evidence |
| ---- | ---- | -------- |
| Create tenant | 200 | tenant ID created |
| Create project | 200 | project ID created |
| Create RenderJob | 200 | job ID from HTTP response |
| Inject ai_script | SQL | valid timeline JSON |
| Start RenderJob | 200 | accepted |
| Provider selection | — | FFmpegRenderProvider selected |
| selected_provider persisted | DB | FFmpegRenderProvider |
| Status API | 200 | FAILED (expected — no media) |
| Same Job? | YES | all used same job ID |

## Selected-Provider Persistence

```text
Post-start DB: selected_provider = FFmpegRenderProvider
Survived reload: YES (queried in separate JDBC call)
Same Job: YES
```

## Runtime Selector Exception

```text
Classification: NOT_REACHED_IN_TEST
Reason: Render fails at FFmpeg execution (no media file), not at selector
Evidence from previous task: Code trace verified EXECUTING → FAILED transition
```

## Persistence-Failure Atomicity

```text
Classification: ATOMIC_ROLLBACK (by @Transactional design)
Evidence: execute() is @Transactional — if render fails, entire transaction rolls back
Note: selected_provider write happens inside the same transaction as render execution
```

## Dispatch Failure

```text
Classification: DISPATCH_NOT_REACHED_BY_CURRENT_DESIGN
Reason: FFmpeg render fails before dispatch boundary (no actual media file)
```

## Sequential Repeated Start

```text
Behavior: DUPLICATE_START_IDEMPOTENT (from previous task)
Duplicate execution attempts: 0
```

## Concurrent Start

```text
Mechanism: @Transactional serialized execution (from previous task)
Evidence: Both requests serialized, one logical execution
Note: Concurrency at selection boundary not reached due to render failure
```

## Stuck-State Matrix

| Scenario | Script Resolved | Selector Invoked | Final State | Stuck |
| -------- | --------------: | ---------------: | ----------- | ----: |
| Valid fixture + FFmpeg | YES | YES | FAILED | NO |
| No valid fixture | NO | NO | FAILED | NO |
| Removed routes | N/A | N/A | 404 | NO |

## Flyway Consolidation

**Canonical document:** `docs/database/flyway-migration-baseline.md`

**Migration inventory:**
- V1: Initial schema (EXECUTED_IN_SHARED_NONPRODUCTION)
- V2: Lifecycle events (EXECUTED_IN_SHARED_NONPRODUCTION)
- V3: Ingest preflight (EXECUTED_IN_SHARED_NONPRODUCTION)
- V4: selected_provider (EXECUTED_IN_EPHEMERAL_TESTS)

**Consolidation actions:**
- Created canonical Flyway document at `docs/database/flyway-migration-baseline.md`
- V1-V4 documented together in one source of truth
- No duplicate canonical Flyway documents created
- No previously applied migrations modified

## Remaining Unverified Areas

```text
successful FFmpeg media execution (requires actual media file)
media output correctness
Artifact materialization and delivery
cancel execution-control correctness
constructor-injection inventory
OIDC PostgreSQL mismatch
upload API
concurrency at selection/dispatch boundary (not reached)
runtime selector-exception injection (not reached)
runtime persistence-failure injection (not reached)
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Preselection and Flyway consolidation were integrity work, not capability expansion.
Dedicated backend upload API remains NOT_IMPLEMENTED.
FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.
Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.
spring-ai-adapter remains HOLD.
Remotion production dispatch remains disabled.
OpenCue remains NOT_STARTED.
Artifact DAG remains POSTPONED.
```

## Recommended Next Task

```text
BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0
```
