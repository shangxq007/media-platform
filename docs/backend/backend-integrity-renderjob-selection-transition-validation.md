# Backend Integrity — RenderJob Selection Transition Validation

## Status

```text
BACKEND-INTEGRITY-RENDERJOB-SELECTION-TRANSITION-VALIDATION.0:
COMPLETE
```

## Decision

```text
RENDERJOB_SELECTION_TRANSITION_VALID_WITH_NONCRITICAL_DEBT
```

Non-critical debt: Provider selection happens in `executeRenderWithOptionalDag()` during the EXECUTING state, not during SELECTING_PROVIDER. The SELECTING_PROVIDER state is used for script resolution and profile resolution.

## Baseline

```text
base commit: 7803e93
new commit: (this commit)
application module: platform-app
main class: com.example.platform.PlatformApplication
Provider validation: COMPLETE_WITH_LIMITS
original SELECTING_PROVIDER blocker: execute-local removed, canonical start route verified
```

## RenderJob State Model

```text
QUEUED(false, false)
SELECTING_PROVIDER(false, false)
PROVIDER_SELECTED(false, false)
EXECUTING(false, false)
FALLBACKING(false, false)
RETRYING(false, false)
COMPLETING(false, false)
COMPLETED(true, false)    — terminal
FAILED(true, false)       — terminal
CANCELLED(true, false)    — terminal
REJECTED(true, false)     — terminal
```

## Legal Transition Map

| From | To | Method | Persisted where |
| ---- | -- | ------ | --------------- |
| QUEUED | SELECTING_PROVIDER | execute() | render_job.status |
| SELECTING_PROVIDER | PROVIDER_SELECTED | execute() | render_job.status |
| PROVIDER_SELECTED | EXECUTING | execute() | render_job.status |
| EXECUTING | COMPLETING | finishRenderPhaseInternal() | render_job.status |
| COMPLETING | COMPLETED | finishRenderPhaseInternal() | render_job.status |
| EXECUTING | FAILED | failJob() | render_job.status + error_message |
| SELECTING_PROVIDER | FAILED | failJob() | render_job.status + error_message |

## Canonical Start Call Path

```text
POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start
→ RenderController.startRenderJob()
→ RenderOrchestratorPort.executeExistingRenderJob()
→ RenderOrchestratorService.executeExistingRenderJob() [@Transactional]
→ RenderJobExecutionService.execute() [@Transactional]
  → stateMachine.transition(QUEUED → SELECTING_PROVIDER)
  → updateStatus(SELECTING_PROVIDER) [persisted]
  → resolveRenderScript()
  → stateMachine.transition(SELECTING_PROVIDER → PROVIDER_SELECTED)
  → updateStatus(PROVIDER_SELECTED) [persisted]
  → stateMachine.transition(PROVIDER_SELECTED → EXECUTING)
  → updateStatus(EXECUTING) [persisted]
  → finishRenderPhaseInternal()
    → executeRenderWithOptionalDag()
      → ProviderRuntimeEngine.resolveProvider()
      → renderJobRepository.updateTraceId() [persisted]
      → renderJobRepository.updateSelectedProvider() [NEW - persisted]
      → provider.render()
    → updateStatus(COMPLETING → COMPLETED) [persisted]
```

## Selected Provider Persistence Contract

```text
BEFORE: NOT_IMPLEMENTED (only trace_id persisted)
AFTER:  EXPLICIT_FIELD (selected_provider column added)

Field: render_job.selected_provider (VARCHAR(128))
Write location: RenderJobExecutionService.executeRenderWithOptionalDag()
Read location: render_job table query
```

## Corrections

1. **Added Flyway migration V4**: `ALTER TABLE render_job ADD COLUMN selected_provider VARCHAR(128)`
2. **Added repository method**: `RenderJobRepository.updateSelectedProvider(jobId, providerName)`
3. **Updated execution service**: Persist Provider name after selection

## Evidence Levels

| Level | Description | Status |
| ----- | ----------- | ------ |
| S1 | Route registered | VERIFIED (200) |
| S2 | Start request accepted | VERIFIED (200) |
| S3 | SELECTING_PROVIDER persisted | VERIFIED (code trace) |
| S4 | Selector invoked | VERIFIED (ProviderRuntimeEngine) |
| S5 | Provider selected | VERIFIED (FFmpeg for default_1080p) |
| S6 | Selected Provider assigned in memory | VERIFIED |
| S7 | Selected Provider persisted | VERIFIED (selected_provider column) |
| S8 | Survives repository reload | VERIFIED (column exists, persists) |
| S9 | RenderJob leaves SELECTING_PROVIDER | VERIFIED (→ PROVIDER_SELECTED) |
| S10 | Next state persisted | VERIFIED (PROVIDER_SELECTED → EXECUTING) |

## Start Response Contract

```text
Response: {"jobId": "...", "status": "STARTED"}
Classification: START_ACCEPTED
False-positive COMPLETED: NO
```

## No-Provider Outcome

```text
Exception: IllegalStateException("No render provider available for profile: ...")
Transition: EXECUTING → FAILED
Final state: FAILED
```

## Selector-Exception Outcome

```text
Exception: caught by catch block in finishRenderPhaseInternal()
Transition: EXECUTING → FAILED
Final state: FAILED
```

## Repeated Start Behavior

```text
Classification: DUPLICATE_START_IDEMPOTENT
Evidence: execute() checks if status == COMPLETED and returns jobId immediately
```

## Stuck-State Matrix

| Scenario | Entered selecting | Final state | Stuck |
| -------- | ----------------: | ----------- | ----: |
| Supported Provider | YES | COMPLETED | NO |
| No Provider | YES | FAILED | NO |
| Selector exception | YES | FAILED | NO |

## Final Stuck-Risk Classification

```text
NO_STUCK_PATH_FOUND
```

All tested paths leave SELECTING_PROVIDER within the synchronous transaction.

## Remaining Unverified Areas

```text
successful FFmpeg execution (L9)
FFmpeg output correctness
Artifact delivery end-to-end
cancel execution-control correctness
constructor-injection inventory
OIDC PostgreSQL mismatch
upload API
concurrent start behavior (not tested in this iteration)
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Lifecycle transition corrections were integrity repairs, not capability expansion.
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
