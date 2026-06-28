# Render Observability and Correlation IDs v0

## Overview

Internal correlation context for the TimelineRevision render pipeline. Consistently correlates render requests, dedup decisions, audit events, and output products without changing the public API.

## Domain Model

### RenderCorrelationContext

Immutable record carrying stable identifiers through the pipeline:

- `renderCorrelationId` — UUID generated per request (not used for fingerprint/dedup)
- `renderRequestFingerprint` — deterministic hash (attached after dedup check)
- `projectId`, `timelineRevisionId` — stable request identity
- `executionMode` — LEGACY or PLAN_BASED
- `renderJobId` — attached when render starts
- `artifactGraphId`, `capabilityGraphId` — attached after compile
- `providerBindingPlanId`, `renderExecutionPlanId` — attached after planning
- `localExecutionRunId` — attached at local runner start (reserved)
- `inputProductIds`, `outputProductId` — attached after registration
- `futureOpenCueJobId` — reserved, null in v0

## Fingerprint vs Correlation ID

| Aspect | RenderRequestFingerprint | RenderCorrelationId |
|--------|------------------------|-------------------|
| Purpose | Idempotency / dedup | Observability / tracing |
| Determinism | Deterministic (SHA-256) | Random UUID per request |
| Same request = same value? | Yes | No (new per attempt) |
| Used for Product reuse? | Yes | No |
| Affects render output? | Yes (dedup) | No |
| In public API? | No | No |

## Correlation Flow

```text
TimelineRevisionRenderFacade.render()
    → create RenderCorrelationContext(projectId, revisionId, mode)
    → attach fingerprint after dedup check
    → emit audit events with fromCorrelation(ctx)
    → pass to plan-based/legacy service
    → attach result IDs (renderJobId, outputProductId)
    → emit RENDER_COMPLETED with full correlation
```

## Builder Integration

`RenderAuditEvent.builder().fromCorrelation(ctx)` copies all correlation fields:
- renderCorrelationId
- projectId, timelineRevisionId, executionMode
- renderJobId, renderRequestFingerprint
- artifactGraphId, capabilityGraphId
- providerBindingPlanId, renderExecutionPlanId
- inputProductIds, outputProductId

## Public API Safety

- Correlation context is internal only
- Not in TimelineRevisionRenderRequest
- Not in TimelineRevisionRenderResponse
- Not in RevisionRenderResult
- Not in render status/result APIs

## Payload Safety

Correlation context does NOT include:
- Raw FFmpeg commands
- Process environment
- Materialized paths
- Storage internals (bucket/key/rootPath)
- Secrets or credentials

## Known Limitations

- v0: localExecutionRunId not propagated (reserved for future)
- v0: Step executor events don't include renderCorrelationId (use renderJobId to correlate)
- v0: No OpenTelemetry or distributed tracing integration
- v0: No persistent correlation store

## Future Path

1. OpenCue job correlation via futureOpenCueJobId
2. Distributed tracing integration (OpenTelemetry)
3. Persistent audit/correlation store
4. Step-level correlation via context propagation
