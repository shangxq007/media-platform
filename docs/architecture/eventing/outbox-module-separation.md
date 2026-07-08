# Outbox Module Separation

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** OUTBOX-MODULE-SEPARATION.0

---

## Background

OUTBOX-BOUNDARY-REVIEW.0 found that the `outbox_events` table and `OutboxEventDispatcher` are CLEAN. The issue is module-level mixing: `PlatformJob`/`PlatformTask` orchestration coexists with the transactional outbox in `outbox-event-module`.

---

## Current Inventory

### Transactional Outbox (stays in outbox)

| Class | Responsibility | Target |
|-------|---------------|--------|
| OutboxEventService | Persist/dispatch outbox events | ✅ Outbox |
| OutboxEventDispatcher | Poll and publish events | ✅ Outbox |
| OutboxEventRouter | Event type → class mapping | ✅ Outbox |
| OutboxEventRegistration | Register 22 event types | ✅ Outbox |
| OutboxBackedNotificationEventPublisher | Write notification events | ✅ Outbox |
| OutboxController | Admin API | ✅ Outbox |
| PostgresNotificationService | PG NOTIFY | ✅ Outbox |
| DispatchBooster | Wake dispatcher on new events | ✅ Outbox |

### Platform Job/Task Orchestration (moved to coordination)

| Class | Responsibility | Target |
|-------|---------------|--------|
| PlatformCoordinationService | Job/task lifecycle | 🔄 coordination |
| PlatformJobRepository | Job persistence | 🔄 coordination |
| PlatformTaskRepository | Task persistence | 🔄 coordination |
| PlatformTaskDispatcher | Task polling/execution | 🔄 coordination |
| PlatformJob | Job domain model | 🔄 coordination |
| PlatformTask | Task domain model | 🔄 coordination |
| JobStatus, JobType | Job enums | 🔄 coordination |
| TaskStatus, TaskCapability | Task enums | 🔄 coordination |
| ExecutionBackend | Backend SPI | 🔄 coordination |
| ExecutionBackendRegistry | Backend registry | 🔄 coordination |
| BmfExecutionBackend | BMF backend | 🔄 coordination |
| LocalProcessExecutionBackend | Local process backend | 🔄 coordination |
| TaskHandler | Handler SPI | 🔄 coordination |
| TaskHandlerRegistry | Handler registry | 🔄 coordination |
| MockAsrTaskHandler | Mock ASR | 🔄 coordination |
| MockProbeTaskHandler | Mock PROBE | 🔄 coordination |
| ExecutionRequest, ExecutionResult | DTOs | 🔄 coordination |
| TaskExecutionContext | Context DTO | 🔄 coordination |

---

## Target Boundaries

### Transactional Outbox (`com.example.platform.outbox`)

Owns:
- `outbox_events` table
- Event persistence/dispatch
- Event type registration
- Idempotency tracking

Does NOT own:
- Job/task orchestration
- Worker scheduling
- Execution backends

### Platform Coordination (`com.example.platform.outbox.coordination`)

Owns:
- PlatformJob/PlatformTask lifecycle
- Task dispatching
- Execution backends
- Task handlers

External consumers:
- `render-module/AssetSearchConsumer` → `PlatformCoordinationService`
- `render-module/MarketplaceConsumer` → `PlatformCoordinationService`
- `render-module/ProbeTaskHandler` → `ExecutionBackend`
- `render-module/WhisperAsrProvider` → `ExecutionBackend`

---

## Dependency Rules

1. Outbox must NOT own job/task orchestration
2. Outbox must NOT become an integration router
3. Coordination may depend on outbox for event emission (via interface)
4. No cyclic dependencies
5. Camel/EventMesh/APISIX remain external candidates

---

## Classification After Separation

- Outbox module: **CLEAN**
- Platform coordination: **SEPARATED**
- No schema changes
- No behavior changes
- No external dependencies added
