# Lead Root Cause and Repair Plan

## 1. External Environment Restoration

### Unauthorized Skill: multi-agent-orchestration

```text
Status: NEEDS_AUDIT
Action: Check if exists, remove if unauthorized
```

### Capability Profile Patch

```text
Status: NEEDS_AUDIT
Action: Check if patched, revert if unauthorized
```

### Memory Update

```text
Status: NEEDS_AUDIT
Action: Check what was added, remove if unauthorized
```

## 2. Architecture Document Repair

### Stale References Fixed

| File | Line | Before | After |
|------|------|--------|-------|
| target-state.md | 7 | UNIQUE(render_job_id, output_type) | UNIQUE(render_output_commit.render_job_id) |
| target-state.md | 89 | UNIQUE(render_job_id, output_type) | UNIQUE(render_output_commit.render_job_id) |
| ADR-026 | 240 | UNIQUE(render_job_id, output_type) | UNIQUE(render_output_commit.render_job_id) |

### Remaining Historical References (Acceptable)

| File | Context |
|------|---------|
| closeout.md | "BEFORE: UNIQUE(render_job_id, output_type)" — historical record |
| errata.md | "BEFORE: ..." — historical record |

## 3. Test Failure Root Cause Clusters

### Cluster 1: EnabledAdminSecurityTest (15 failures)

```text
Root cause: Likely Spring context or security configuration issue
Module: platform-app
Classification: SPRING_CONTEXT_OR_BEAN_WIRING
Repair: Investigate security test configuration
```

### Cluster 2: StartClaimAndFailureDurabilityTest (3 failures)

```text
Root cause: NullPointerException at createTenant:273
Module: platform-app
Classification: TEST_ISOLATION_OR_SHARED_STATE
Repair: Fix createTenant helper to handle missing response field
```

### Cluster 3: OutboxEventDispatcherTest (4 failures)

```text
Root cause: Mockito verification or assertion failure
Module: outbox-event-module
Classification: STALE_TEST_EXPECTATION
Repair: Update test expectations
```

### Cluster 4: RenderJobStateMachineErrorModelTest (1 failure)

```text
Root cause: Stale FALLBACKING transition test
Module: render-module
Classification: ARCHITECTURE_CONTRACT_DRIFT
Repair: Update test to match frozen architecture
```

### Cluster 5: Various render-module failures (16 files)

```text
Root cause: Multiple issues (FFmpeg, timeline, storage)
Module: render-module
Classification: MIXED
Repair: Investigate each sub-cluster
```

## 4. Retry Disposition

```text
Current: RenderJobService.retry() resets FAILED row to QUEUED
Frozen architecture: Retry creates new RenderJob
Action: DEFER_TO_PROTOCOL_IMPLEMENTATION_WITH_GUARD
Reason: Removing retry now may break existing tests; guard it instead
```

## 5. V5 Readiness

After repairs:
- Architecture docs consistent ✅ (fixed)
- Test baseline green (pending)
- No unauthorized changes (pending audit)
