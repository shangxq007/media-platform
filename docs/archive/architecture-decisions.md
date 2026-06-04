# Architecture Decisions

> **Generated**: 2026-05-08T08:30Z
> **Scope**: Key architectural decisions made during Phase T1 (Architecture Convergence & Modulith Boundary Review)
> **Related**: [`module-boundaries.md`](./module-boundaries.md) | [`architecture-notes.md`](./architecture-notes.md)

---

## ADR-001: Eliminate Unnecessary Type.OPEN Modules

**Status**: Accepted (Phase T1)

**Context**: In a previous phase, `ai-module`, `audit-compliance-module`, and `storage-module` were changed to `Type.OPEN` to allow `RenderOrchestratorService` to directly inject their service beans (`AiGatewayService`, `AuditService`, `StorageCatalogService`). This was a temporary fix to make `ModularityTest` pass.

**Decision**: Revert all three modules back to CLOSED (default type). Expose their API surfaces through proper port interfaces and named interfaces instead.

**Consequences**:
- `ai-module`: Exposes `AiGatewayPort` interface via `@NamedInterface("API")` on `ai.api`. `AiGatewayService` implements the port.
- `storage-module`: Exposes `StorageCatalogPort` interface via `@NamedInterface("API")` on `storage.api` and domain types via `@NamedInterface("domain")` on `storage.domain`.
- `audit-compliance-module`: Fully decoupled from render via event-driven architecture. No external module needs direct access.
- Only `shared-kernel` retains `Type.OPEN` (justified: provides shared domain events, error codes, value objects, and cross-module SPIs).

**Type.OPEN Retention Justification**:
| Module | Type | Justification |
|--------|------|---------------|
| `shared-kernel` | OPEN | Provides shared types (events, error codes, `Ids`, `Jsons`, `NotificationEventPublisher`) to ALL modules. Making it CLOSED would require every module to declare `allowedDependencies` on `shared`, which is impractical and defeats the purpose of a shared kernel. |

---

## ADR-002: Port Interfaces for Cross-Module Service Access

**Status**: Accepted (Phase T1)

**Context**: `RenderOrchestratorService` needed to call AI chat and storage catalog operations, but direct injection of `AiGatewayService` and `StorageCatalogService` violated Modulith boundaries when those modules were CLOSED.

**Decision**: Introduce port interfaces in the `api` packages of `ai-module` and `storage-module`:
- `com.example.platform.ai.api.AiGatewayPort` — port for AI chat operations
- `com.example.platform.storage.api.StorageCatalogPort` — port for artifact registration and query

Implementation classes (`AiGatewayService`, `StorageCatalogService`) implement these ports. Other modules depend on the port interface, not the implementation.

**Consequences**:
- Render module imports `AiGatewayPort` and `StorageCatalogPort` (port interfaces), not the service implementations
- Port interfaces are placed in the `api` package alongside controllers, under `@NamedInterface("API")`
- Domain types (`ChatResult`, `BlobStorage`, etc.) are exposed via `@NamedInterface("domain")`
- Dependency inversion: modules depend on abstractions, not implementations

---

## ADR-003: Event-Driven Audit Decoupling

**Status**: Accepted (Phase T1)

**Context**: `RenderOrchestratorService` directly injected `AuditService` from `audit-compliance-module` to record audit entries for render job completion/failure. This created a hard cross-module dependency on an internal service implementation.

**Decision**: Decouple render from audit using Spring domain events:
- `RenderOrchestratorService` publishes `RenderJobCompletedEvent` and `RenderJobFailedEvent` via `ApplicationEventPublisher`
- `AuditEventHandler` in `audit-compliance-module` consumes these events via `@EventListener`
- Both event types live in `shared-kernel` (`com.example.platform.shared.events`)

**Consequences**:
- `render-module` no longer has `audit` in its `allowedDependencies`
- `audit-compliance-module` is fully decoupled — it reacts to events, never called directly
- Audit trail is eventually consistent (event-driven) rather than transactionally coupled
- New events added to shared kernel: `RenderJobCompletedEvent`, `RenderJobFailedEvent`

**Event Flow**:
```
RenderOrchestratorService
  ├── ApplicationEventPublisher.publishEvent(RenderJobCompletedEvent)
  │     └── AuditEventHandler.onRenderJobCompleted() → AuditService.record()
  └── ApplicationEventPublisher.publishEvent(RenderJobFailedEvent)
        └── AuditEventHandler.onRenderJobFailed() → AuditService.record()
```

---

## ADR-004: Named Interface Conventions

**Status**: Accepted (Phase T1)

**Context**: CLOSED modules with `@NamedInterface` annotations require explicit `allowedDependencies` declarations that reference both module names and named interface names.

**Decision**: Establish the following conventions:
- `@NamedInterface("API")` on `api` packages — exposes port interfaces and REST controllers
- `@NamedInterface("domain")` on `domain` packages — exposes domain value objects and interfaces
- `allowedDependencies` format: `"moduleName :: interfaceName"` for named interfaces, `"moduleName"` for the default (unnamed) interface

**Consequences**:
- `render-module` declares: `allowedDependencies = {"ai", "ai :: API", "ai :: domain", "shared", "storage", "storage :: API", "storage :: domain"}`
- Module names are derived from the last segment of the base package: `com.example.platform.ai` → `ai`
- Named interface names match the `@NamedInterface` annotation value exactly

---

## ADR-005: Shared Kernel Content Boundaries

**Status**: Accepted (Phase T1, reaffirmed)

**Context**: The `shared-kernel` module is `Type.OPEN` and accessible to all modules. It's critical to prevent it from accumulating business logic.

**Decision**: `shared-kernel` is strictly limited to:
- Domain events (cross-module event types)
- Error codes and base exceptions
- Infrastructure utilities (`Ids`, `Jsons`)
- Cross-module SPI port interfaces (e.g., `NotificationEventPublisher`)
- Logging/tracing constants (`TraceKeys`)

**Explicitly forbidden** in `shared-kernel`:
- Business services, repositories, workflows
- Provider adapters
- Module-specific DTOs
- Business policy logic
- Scheduled jobs

---

## ADR-006: render-module Dependency Model

**Status**: Accepted (Phase T1)

**Context**: The render module is the primary orchestrator that coordinates AI, storage, and notification capabilities. It needs cross-module access but must respect Modulith boundaries.

**Decision**: `render-module` has the following explicit cross-module dependencies:

| Dependency | Type | Access Mechanism |
|-----------|------|-----------------|
| `shared-kernel` | `api` | Direct use of `NotificationEventPublisher`, domain events, `Ids` |
| `ai-module` | `api` | Port interface `AiGatewayPort` via `ai.api` named interface |
| `storage-module` | `api` | Port interface `StorageCatalogPort` via `storage.api` named interface; domain types via `storage.domain` named interface |
| `audit-compliance-module` | *(none)* | Event-driven via `RenderJobCompletedEvent`/`RenderJobFailedEvent` |
| `notification-module` | *(none)* | Event-driven via `RenderJobCreatedEvent`/`RenderJobStatusChangedEvent`/`ArtifactCreatedEvent` |

**Consequences**:
- `render-module` does NOT import from `ai.app`, `audit.app`, or `storage.app`
- All cross-module communication goes through port interfaces or domain events
- `build.gradle.kts` declares `api(project(":ai-module"))` and `api(project(":storage-module"))` for compile-time access to API packages
