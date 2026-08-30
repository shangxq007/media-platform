# Render Audit Event Trail v0

## Overview

Internal render audit event trail for inspecting, debugging, and reasoning about the TimelineRevision render pipeline.

```text
TimelineRevisionRenderFacade → RenderAuditRecorder → RenderAuditEventSink
RenderExecutionStepExecutor  → RenderAuditRecorder → RenderAuditEventSink
```

## Domain Model

### RenderAuditEvent

Immutable record of a render lifecycle moment:

- `eventId` — unique UUID (not used in render fingerprint)
- `occurredAt` — Instant timestamp (not used in render fingerprint)
- `eventType` — explicit lifecycle event type
- `severity` — DEBUG, INFO, WARN, ERROR
- `projectId`, `timelineRevisionId`, `renderJobId` — stable identifiers
- `renderRequestFingerprint` — internal fingerprint (not in public DTOs)
- `executionMode` — LEGACY or PLAN_BASED
- `artifactGraphId`, `capabilityGraphId`, `providerBindingPlanId`, `renderExecutionPlanId` — compile pipeline IDs
- `providerName` — internal provider name (e.g., "ffmpeg")
- `inputProductIds`, `outputProductId` — product references
- `message` — human-readable description
- `sanitizedDetails` — sanitized additional context

### Event Types

| Type | When |
|------|------|
| RENDER_REQUEST_RECEIVED | Facade receives render request |
| RENDER_DEDUP_CHECKED | Dedup check completed |
| RENDER_READY_PRODUCT_REUSED | Existing READY product reused |
| RENDER_NEW_ATTEMPT_STARTED | New render attempt starting |
| RENDER_RETRY_AFTER_FAILURE | Retrying after previous failure |
| RENDER_DEDUP_FAILED_CLOSED | Dedup lookup failed |
| INPUT_MATERIALIZATION_COMPLETED | Input materialized |
| PROVIDER_EXECUTION_COMPLETED | FFmpeg execution completed |
| OUTPUT_VERIFICATION_COMPLETED | Output verified |
| OUTPUT_REGISTRATION_COMPLETED | Output registered |
| PRODUCT_DEPENDENCY_LINKED | ProductDependency created |
| RENDER_COMPLETED | Render succeeded |
| RENDER_FAILED | Render failed |
| RENDER_FAILED_CLOSED | Render fail-closed |

### RenderAuditRecorder

- Records events to a sink
- Fails the audited render operation when recording fails
- Propagates sink exceptions so required audit is never silently discarded
- Exposes sink for testing via `getSink()`

### RenderAuditEventSink (interface)

Pluggable sink:

- `InMemoryRenderAuditEventSink` — thread-safe, for testing
- `FailClosedRenderAuditEventSink` — typed 503 until durable storage exists

### RenderAuditTrail

Query interface for recorded events:

- `getEventsForRenderJob(renderJobId)`
- `getEventsForProject(projectId)`
- `hasEventOfType(type)`
- `getEventsOfType(type)`
- `getEventCount()`

## Payload Safety

Events must NOT include:

- Raw FFmpeg commands
- Process environment
- Local materialized paths
- Bucket/objectKey/rootPath/relativePath
- Signed URLs
- Secrets or credentials
- Full exception stack traces

Error messages are sanitized (path patterns replaced with `[path]`).

## Emission Points

### Facade Level
- RENDER_REQUEST_RECEIVED
- RENDER_DEDUP_CHECKED
- RENDER_READY_PRODUCT_REUSED
- RENDER_NEW_ATTEMPT_STARTED / RENDER_RETRY_AFTER_FAILURE
- RENDER_DEDUP_FAILED_CLOSED
- RENDER_COMPLETED / RENDER_FAILED

### Step Executor Level
- INPUT_MATERIALIZATION_COMPLETED
- PROVIDER_EXECUTION_COMPLETED
- OUTPUT_VERIFICATION_COMPLETED
- OUTPUT_REGISTRATION_COMPLETED
- PRODUCT_DEPENDENCY_LINKED

## Configuration

Default: `FailClosedRenderAuditEventSink` (audited operations fail until durable authority exists).

Override in tests: inject `InMemoryRenderAuditEventSink` via Spring context.

## Public API Safety

- Audit events are internal only
- Not exposed in request/response DTOs
- Not exposed in render status/result APIs
- Event IDs not in public surfaces
- Fingerprint not in public surfaces

## Architecture Compliance

- No DB migration
- No public API changes
- No external dependencies
- ProductDependency remains canonical lineage
- Audit trail is diagnostic supplement only
