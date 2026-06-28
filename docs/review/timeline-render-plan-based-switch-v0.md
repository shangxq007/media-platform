# Timeline Render Plan-Based Switch v0

## Overview

Adds a feature flag to switch the TimelineRevision render API between the legacy direct FFmpeg path and the plan-based execution path.

```text
POST /api/v1/render/projects/{projectId}/timeline/revisions/{revisionId}/render
    ↓
TimelineRevisionRenderFacade
    ↓ (feature flag)
    ├── LEGACY: TimelineRevisionRenderService (existing)
    └── PLAN_BASED: PlanBasedTimelineRevisionRenderService
            ↓
        Compile pipeline → LocalExecutionPlanRunner → FFmpeg
```

## Configuration

### Property

```yaml
media:
  render:
    timeline:
      execution-mode: LEGACY  # or PLAN_BASED
```

### Default

**PLAN_BASED** — compile pipeline + LocalExecutionPlanRunner.

### Rollback

Set `execution-mode: LEGACY` and restart. No data migration required.

### Usage

| Mode | Service | Description |
|------|---------|-------------|
| PLAN_BASED | `PlanBasedTimelineRevisionRenderService` | Compile pipeline + LocalExecutionPlanRunner (default) |
| LEGACY | `TimelineRevisionRenderService` | Existing direct FFmpeg path (rollback) |

## Internal Components

### TimelineRenderExecutionMode (enum)

- `PLAN_BASED` — compile pipeline path (default)
- `LEGACY` — direct FFmpeg path (rollback)

### TimelineRenderExecutionProperties (record)

Configuration properties:
- `executionMode` — LEGACY or PLAN_BASED
- `isPlanBasedEnabled()` — convenience check
- `isLegacyEnabled()` — convenience check

### TimelineRevisionRenderFacade (service)

Routing layer that:
1. Reads `TimelineRenderExecutionProperties`
2. Delegates to legacy or plan-based service
3. Returns same `RevisionRenderResult` contract

## Public API Contract

The public API contract is **unchanged**:

- Request: `TimelineRevisionRenderRequest` (outputProfile only)
- Response: `TimelineRevisionRenderResponse` (same fields)
- Result: `RevisionRenderResult` (same fields)

The feature flag is internal configuration only — not exposed in:
- Request DTOs
- Response DTOs
- Public API parameters
- Query parameters
- Headers

## Both Paths Produce

- READY output Product
- ProductDependency lineage
- StorageRuntime output registration
- Same RevisionRenderResult contract

## Rollback

To rollback from PLAN_BASED to LEGACY:

1. Set `media.render.timeline.execution-mode: LEGACY` in application config
2. Restart application
3. No code change required
4. No data migration required

## Architecture Compliance

- FFmpeg remains only PRODUCTION baseline
- Non-FFmpeg providers remain non-executable
- OpenCue submit remains disabled
- RenderExecutionPlan is internal only
- LocalExecutionPlanRunner is internal only
- No storage internals in public APIs
- No raw commands in public APIs
- No provider internals in public APIs

## Known Limitations

- v0: Only FFmpeg baseline is executable in PLAN_BASED mode
- v0: Single primary input only
- v0: No parallel step execution
- v0: PREPARE_PROVIDER_DOCUMENT is a no-op
