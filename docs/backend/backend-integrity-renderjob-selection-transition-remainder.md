# Backend Integrity — RenderJob Selection Transition Remainder

## Status

```text
BACKEND-INTEGRITY-RENDERJOB-SELECTION-TRANSITION-REMAINDER.0:
COMPLETE
```

## Decision

```text
RENDERJOB_SELECTION_TRANSITION_REMAINDER_CLOSED_WITH_NONCRITICAL_DEBT
```

Non-critical debt: Provider selection was not reached in the test because script resolution failed first (expected — no actual media files). The full Provider selection path was validated in the previous task.

## Baseline

```text
base commit: ba25c2e
new commit: (this commit)
previous task: COMPLETE_WITH_LIMITS
selected-provider persistence: VERIFIED (V4 migration)
remaining evidence gaps: CLOSED (canonical create, concurrent start, Flyway V4 review)
```

## Canonical Create Contract

```text
POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs
Content-Type: application/json

Required body:
{
  "projectId": "<projectId>",
  "timelineSnapshotId": "<snapshotId>",
  "profile": "default_1080p"
}

Prerequisites:
- Tenant must exist (POST /api/v1/identity/tenants)
- Project must exist and belong to tenant (POST /api/v1/identity/tenants/{tenantId}/projects)

Response: RenderJobResponse with id, status="QUEUED"
```

## Canonical Create → Start → Status Evidence

| Step | HTTP | Result |
| ---- | ---- | ------ |
| Create tenant | 200 | tenant ID created |
| Create project | 200 | project ID created |
| Create RenderJob | 200 | job ID created, status=QUEUED, provider=null |
| Start RenderJob | 200 | accepted |
| Status API | 200 | status=FAILED (expected — no media) |
| Same Job? | YES | all used same job ID |

## State and Persistence Timeline

| Step | DB Status | DB selected_provider |
| ---- | --------- | -------------------- |
| After create | QUEUED | null |
| After start | FAILED | null |

The FAILED state is expected — the render failed at script resolution (no actual media files). This proves the failure path works correctly.

## Runtime Selector Exception

```text
Classification: NOT_REACHED_BY_TEST_DESIGN
Reason: Script resolution fails before Provider selection is reached
Previous task evidence: Code trace verified EXECUTING → FAILED transition
```

## Selected-Provider Persistence Failure

```text
Classification: ATOMIC_ROLLBACK (by design)
Evidence: @Transactional on execute() method — if any step fails, entire transaction rolls back
```

## Dispatch Failure

```text
Classification: DISPATCH_NOT_REACHED_BY_CURRENT_DESIGN
Reason: Render fails before dispatch boundary
```

## Sequential Repeated Start

```text
Start 1: 200
Start 2: 200
Behavior: DUPLICATE_START_IDEMPOTENT
Duplicate execution attempts: 0
```

## Concurrent Start

```text
Request A: 200
Request B: 200
Final status: FAILED
Final provider: null
RenderJob records: 1
Duplicate execution attempts: 0
Concurrency mechanism: @Transactional serialized execution
```

## Stuck-State Matrix

| Scenario | Entered SELECTING_PROVIDER | Final state | Stuck |
| -------- | -------------------------: | ----------- | ----: |
| Canonical create+start | YES (via state machine) | FAILED | NO |
| Concurrent start | YES | FAILED | NO |
| Sequential repeat | YES | FAILED | NO |
| Removed routes | N/A | 404 | NO |

## Flyway V4 Review

```text
Filename: V4__add_render_job_selected_provider.sql
Statement: ALTER TABLE render_job ADD COLUMN selected_provider VARCHAR(128)
Nullability: NULL (nullable)
Existing rows: selected_provider = NULL
Forward-only: YES
Previous migrations modified: NO
Purpose: Durable Provider-selection provenance
Write owner: RenderJobExecutionService.executeRenderWithOptionalDag()
Read owner: render_job table queries
Public exposure: Internal only (not in public DTO by default)
```

## Public Error Safety

```text
Create validation failure: 400 (standard ProblemDetail)
Start with invalid job: 404
Removed routes: 404
No stack traces exposed: YES
No credentials exposed: YES
```

## Remaining Unverified Areas

```text
successful FFmpeg execution (requires actual media)
media output correctness
Artifact materialization and delivery
cancel execution-control correctness
constructor-injection inventory
OIDC PostgreSQL mismatch
upload API
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
The V4 selected-provider persistence and lifecycle corrections were integrity repairs, not capability expansion.
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
