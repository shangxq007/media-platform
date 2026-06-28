# Local Execution Plan Runner v0

## Overview

The LocalExecutionPlanRunner is the first plan-based execution runner for the FFmpeg baseline path. It bridges the compile pipeline (ProviderBindingPlan → RenderExecutionPlan) to actual FFmpeg execution.

```text
RenderExecutionPlan
    ↓ RenderPlanPolicyGuard (validation)
    ↓ LocalExecutionPlanRunner (orchestration)
    ↓ RenderExecutionStepExecutor (step execution)
    → FFmpeg baseline render
    → StorageRuntime output registration
    → ProductRuntime READY Product
    → ProductDependency lineage
```

## Domain Model

### LocalExecutionPlanRunner

Internal application service that orchestrates plan execution:

- Accepts `RenderExecutionPlan` + `LocalExecutionPlanContext`
- Runs policy guard validation
- Checks execution readiness (FFmpeg LOCAL PRODUCTION only)
- Executes steps in dependency order
- Blocks downstream steps on upstream failures
- Returns `LocalExecutionPlanRunResult`

### LocalExecutionPlanContext

Carries all execution context (internal only):

- `renderJobId`, `tenantId`, `projectId`, `timelineRevisionId`, `snapshotId`
- `timelineJson` (for FFmpeg)
- `inputProductIds`, `inputProductId` (primary)
- `storageRoot`, `outputDir`, `outputFileName`
- `width`, `height`, `fps`, `duration`, `hasSubtitles`, `outputFormat`

### RenderExecutionStepExecutor

Executes individual plan steps by delegating to existing services:

| Step Type | Delegation |
|-----------|-----------|
| MATERIALIZE_INPUT | `RenderInputMaterializationService.materialize()` |
| PREPARE_PROVIDER_DOCUMENT | No-op in v0 (planning marker) |
| EXECUTE_PROVIDER | FFmpeg via `ProcessToolRunner` (FFmpeg only) |
| VERIFY_OUTPUT | File existence + size check |
| REGISTER_OUTPUT | `RenderOutputRegistrationService.registerOutput()` |
| LINK_PRODUCT_DEPENDENCY | Handled by registerOutput (verification marker) |
| FINALIZE_RENDER | Completion marker |

### Execution Readiness

Only steps meeting ALL criteria are executable:

1. Provider is FFmpeg
2. Provider status is PRODUCTION
3. Provider is production dispatch eligible
4. Provider autoDispatch is allowed
5. FFmpeg tool is available (via `RenderToolCapabilityInventory`)
6. ExecutionEnvironmentTarget is LOCAL
7. Policy guard passes

Non-FFmpeg providers (Remotion, MLT, Blender, Natron, GStreamer, GPAC, OpenFX) are NOT executable.

## Integration with TimelineRevision Render API

### PlanBasedTimelineRevisionRenderService

Internal adapter that bridges the existing render API to the plan-based execution path:

1. Load TimelineRevision and verify ownership
2. Load snapshot and parse to TimelineSpec
3. Map to render job request
4. Resolve input Products
5. Compile pipeline: TimelineSpec → RenderExecutionPlan
6. Build execution context
7. Execute through LocalExecutionPlanRunner
8. Extract output product ID from registration step result
9. Return RevisionRenderResult (same contract as existing service)

### Backward Compatibility

The existing `TimelineRevisionRenderService` is not modified. The plan-based path is a separate service that can be used as a drop-in replacement or tested independently.

## v0 Constraints

- Only FFmpeg baseline is executable
- Non-FFmpeg providers are skipped or rejected
- All EXECUTE_PROVIDER steps for non-FFmpeg are SKIPPED
- PREPARE_PROVIDER_DOCUMENT is a no-op (no real document generation)
- No OpenCue submit
- No real provider execution beyond FFmpeg
- No command generation for non-FFmpeg providers
- No DB migration
- No public API changes

## Safety

- LocalExecutionPlanRunner is internal only (not in public API)
- LocalExecutionPlanContext is internal only
- RenderExecutionStepExecutor is internal only
- No storage internals in public surfaces
- No raw commands in public surfaces
- No materialized paths in public surfaces
- No process environment in public surfaces

## Failure Handling

| Failure | Status |
|---------|--------|
| Null plan | FAILED_CLOSED |
| Null context | FAILED_CLOSED |
| Policy guard rejects | FAILED_CLOSED |
| Non-FFmpeg provider | NOT_EXECUTABLE |
| Non-LOCAL target | NOT_EXECUTABLE |
| Non-production FFmpeg | NOT_EXECUTABLE |
| FFmpeg tool unavailable | FAILED |
| Input materialization failed | FAILED |
| FFmpeg execution failed | FAILED |
| Output verification failed | FAILED |
| Output registration failed | FAILED |
| Upstream step failed | BLOCKED |

## Known Limitations

- v0: Only FFmpeg baseline is executable
- v0: PREPARE_PROVIDER_DOCUMENT is a no-op
- v0: No cross-node dependency optimization
- v0: Single primary input only
- v0: No parallel step execution
