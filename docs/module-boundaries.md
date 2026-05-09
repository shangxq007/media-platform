# Module Boundaries — Architecture Guardrails

> **Generated**: 2026-05-08T03:19Z
> **Updated**: 2026-05-08T08:28Z (Phase T1: Architecture Convergence)
> **Auditor**: Roo (Architect mode)
> **Scope**: Full module boundary audit of `/media-platform/` — 25 modules, build files, package-info annotations, dependency graph.
> **Related**: [`roo-gap-report.md`](./roo-gap-report.md) | [`architecture-notes.md`](./architecture-notes.md) | [`architecture-decisions.md`](./architecture-decisions.md) | [`.roo/rules/30-module-boundaries.md`](.roo/rules/30-module-boundaries.md)

---

## 1. Module Declaration Style

All 24 non-app modules declare `@org.springframework.modulith.ApplicationModule` in their root `package-info.java`. The root application class uses `@Modulith` (from `spring-modulith-api`, available to all modules via `compileOnly` in the root `build.gradle.kts`).

| Module | Annotation | Type | Display Name |
|--------|-----------|------|--------------|
| `shared-kernel` | `@ApplicationModule(type = OPEN)` | `OPEN` | Shared Kernel |
| `render-module` | `@ApplicationModule(allowedDependencies = {"ai", "ai :: API", "ai :: domain", "shared", "storage", "storage :: API", "storage :: domain"})` | CLOSED | Render |
| `notification-module` | `@ApplicationModule` | CLOSED | Notification |
| `ai-module` | `@ApplicationModule` | CLOSED | AI |
| `config-module` | `@ApplicationModule` | CLOSED | Config |
| `workflow-module` | `@ApplicationModule` | CLOSED | Workflow |
| `storage-module` | `@ApplicationModule` | CLOSED | Storage |
| `prompt-module` | `@ApplicationModule` | CLOSED | Prompt |
| `cloud-resource-module` | `@ApplicationModule` | CLOSED | Cloud Resource |
| `secrets-config-module` | `@ApplicationModule` | CLOSED | Secrets & Config Access |
| `extension-module` | `@ApplicationModule` | CLOSED | Extension |
| `datasource-module` | `@ApplicationModule` | CLOSED | Datasource |
| `observability-module` | `@ApplicationModule` | CLOSED | Observability |
| `outbox-event-module` | `@ApplicationModule` | CLOSED | Outbox & Events |
| `audit-compliance-module` | `@ApplicationModule` | CLOSED | Audit & Compliance |
| `scheduler-module` | `@ApplicationModule` | CLOSED | Scheduler |
| `identity-access-module` | `@ApplicationModule` | CLOSED | Identity & Access |
| `quota-billing-module` | `@ApplicationModule` | CLOSED | Quota & Metering |
| `commerce-module` | `@ApplicationModule` | CLOSED | Commerce |
| `payment-module` | `@ApplicationModule` | CLOSED | Payment |
| `billing-module` | `@ApplicationModule` | CLOSED | Billing |
| `entitlement-module` | `@ApplicationModule` | CLOSED | Entitlement |
| `policy-governance-module` | `@ApplicationModule` | CLOSED | Policy Governance |
| `artifact-catalog-module` | `@ApplicationModule` | CLOSED | Artifact Catalog |
| `sandbox-runtime-module` | `@ApplicationModule` | CLOSED | Sandbox Runtime |
| `federation-query-module` | `@ApplicationModule` | CLOSED | Federation Query |

**Key point**: `shared-kernel` is the **only** `OPEN` module (justified: it provides shared domain events, error codes, value objects, and cross-module SPIs). All others are CLOSED, meaning Spring Modulith's `ApplicationModules.verify()` enforces that inter-module dependencies must go through exposed named interfaces.

---

## 2. Module Dependency Graph

### 2.1 Dependency Direction Map

The following table shows **inter-module** `project(:...)` dependencies declared in each module's `build.gradle.kts`. The `platform-app` aggregator depends on all 24 modules (flat, no inter-module ordering).

| Module | Depends On | Dependency Style |
|--------|-----------|-----------------|
| **`platform-app`** | All 24 modules (flat) | `implementation` |
| **`shared-kernel`** | *(none — root of graph)* | — |
| **`render-module`** | `shared-kernel`, `ai-module`, `storage-module` | `api` |
| **`notification-module`** | `shared-kernel` | `api` |
| **`ai-module`** | `shared-kernel` | `api` |
| **`config-module`** | *(none)* | — |
| **`workflow-module`** | `policy-governance-module` | `api` |
| **`storage-module`** | `shared-kernel` | `implementation` |
| **`prompt-module`** | `shared-kernel` | `implementation` |
| **`cloud-resource-module`** | `shared-kernel` | `implementation` |
| **`secrets-config-module`** | `shared-kernel` | `implementation` |
| **`extension-module`** | `shared-kernel` | `implementation` |
| **`datasource-module`** | `shared-kernel` | `implementation` |
| **`observability-module`** | `shared-kernel` | `api` |
| **`outbox-event-module`** | `shared-kernel` | `api` |
| **`audit-compliance-module`** | `shared-kernel` | `api` |
| **`scheduler-module`** | *(none)* | — |
| **`identity-access-module`** | `shared-kernel` | `api` |
| **`quota-billing-module`** | *(none)* | — |
| **`commerce-module`** | `shared-kernel` | `api` |
| **`payment-module`** | `shared-kernel` | `api` |
| **`billing-module`** | `shared-kernel` | `api` |
| **`entitlement-module`** | `shared-kernel` | `api` |
| **`policy-governance-module`** | *(none)* | — |
| **`artifact-catalog-module`** | *(none)* | — |
| **`sandbox-runtime-module`** | *(none)* | — |
| **`federation-query-module`** | *(none)* | — |

### 2.2 Visual Dependency Graph

```
                     ┌──────────────────────────────────────────────┐
                     │              platform-app (aggregator)        │
                     │   Depends on ALL 24 modules (flat, impl)     │
                     └──────────────────────────────────────────────┘
                                           │
         ┌─────────────────────────────────┼─────────────────────────────────┐
         │                 │               │               │                 │
         ▼                 ▼               ▼               ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ shared-kernel│  │  ai-module   │  │ config-module│  │scheduler-mod │  │quota-billing │
│   (OPEN)     │  │  (CLOSED)    │  │  (CLOSED)    │  │  (CLOSED)    │  │  (CLOSED)    │
└──────┬───────┘  └──────┬───────┘  └──────────────┘  └──────────────┘  └──────────────┘
       │                 │
       │ (api)           │ (api) ← render depends on ai.api (AiGatewayPort)
       │                 │        and ai.domain (ChatResult)
       ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│render-module │  │storage-module│  │prompt-module │  │cloud-resource│
│  (CLOSED)    │  │  (CLOSED)    │  │secrets-config│  │datasource-mod│
│              │  └──────────────┘  └──────────────┘  └──────────────┘
│ allowedDeps: │
│ ai, shared,  │
│ storage      │
└──────────────┘

render ──api──► storage (StorageCatalogPort, BlobStorage, PutObjectCommand, StorageObjectRef)
render ──api──► ai (AiGatewayPort, ChatResult)
render ──api──► shared (NotificationEventPublisher, domain events, Ids)

┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│notification  │  │audit-compli  │  │observability │  │outbox-event  │
│identity-acc  │  │commerce-mod  │  │payment-module│  │billing-module│
│entitlement   │  └──────────────┘  └──────────────┘  └──────────────┘
└──────────────┘

┌──────────────┐      ┌────────────────────┐
│policy-gov    │◄─────│  workflow-module   │
│  (CLOSED)    │ (api)│                    │
└──────────────┘      └────────────────────┘
```

### 2.3 Cross-Module Dependency Analysis

**Cross-module dependencies** (besides `shared-kernel`):

| Source Module | Target Module | Named Interfaces | Style | Justification |
|--------------|---------------|------------------|-------|---------------|
| `render-module` | `ai-module` | `API` (`AiGatewayPort`), `domain` (`ChatResult`) | `api` | Render orchestrator uses AI chat via port interface |
| `render-module` | `storage-module` | `API` (`StorageCatalogPort`), `domain` (`BlobStorage`, `PutObjectCommand`, `StorageObjectRef`) | `api` | Render orchestrator registers artifacts and stores blobs via port interface |
| `render-module` | `shared-kernel` | `<<UNNAMED>>` (`NotificationEventPublisher`, events, `Ids`) | `api` | Render publishes domain events and notifications |
| `workflow-module` | `policy-governance-module` | `feature-flags` (`FeatureFlagEvaluator`) | `api` | RenderActivitiesImpl uses FeatureFlagEvaluator SPI |

**Event-based decoupled dependencies** (no direct Gradle dependency):

| Source Module | Target Module | Events | Mechanism |
|--------------|---------------|--------|-----------|
| `render-module` | `audit-compliance-module` | `RenderJobCompletedEvent`, `RenderJobFailedEvent` | Spring `@EventListener` in `AuditEventHandler` |
| `render-module` | `notification-module` | `RenderJobCreatedEvent`, `RenderJobStatusChangedEvent`, `ArtifactCreatedEvent` | Spring `@EventListener` in `NotificationEventHandler` |

---

## 3. Named Interfaces

CLOSED modules expose subpackages via `@NamedInterface`. Only types in named interfaces are accessible to other modules.

| Module | Named Interface | Package | Exposed Types |
|--------|----------------|---------|---------------|
| `ai-module` | `API` | `ai.api` | `AiGatewayPort`, `AiController`, DTOs |
| `ai-module` | `domain` | `ai.domain` | `ChatResult`, `ChatRequest`, `ChatProvider`, `ModelRouter` |
| `storage-module` | `API` | `storage.api` | `StorageCatalogPort`, `StorageCatalogPort.ArtifactRef`, `StorageController` |
| `storage-module` | `domain` | `storage.domain` | `BlobStorage`, `PutObjectCommand`, `StorageObjectRef` |
| `policy-governance-module` | `feature-flags` | `policy.api` | `FeatureFlagEvaluator`, `PolicyController` |

**Note**: `allowedDependencies` in `@ApplicationModule` must reference both the module name and the named interface name in the format `"moduleName :: interfaceName"` for named interfaces, and `"moduleName"` for the default (unnamed) interface.

---

## 4. Shared Kernel Rules

The `shared-kernel` module is declared as `ApplicationModule.Type.OPEN`, meaning all modules may use its types without Modulith boundary violations.

### 4.1 ALLOWED in Shared Kernel

| Category | Examples in Code | Purpose |
|----------|-----------------|---------|
| **Error codes** | `CommonErrorCode` enum (400/404/409/500/502), `ErrorCode` interface | Uniform error classification across all modules |
| **Shared value objects** | `Ids` (UUID generation with prefix), `Jsons` (Jackson wrapper) | Infrastructure-level utilities with no business policy |
| **Log context names** | `TraceKeys` (traceId, requestId, tenantId, projectId) | MDC key constants for structured logging |
| **Base exceptions** | `PlatformException` (RuntimeException with ErrorCode) | Base exception type for all module-specific exceptions |
| **Shared domain events** | `RenderJobCreatedEvent`, `RenderJobStatusChangedEvent`, `ArtifactCreatedEvent`, `RenderJobCompletedEvent`, `RenderJobFailedEvent` | Cross-module event types for render → notification/audit flows |
| **Cross-module SPI** | `NotificationEventPublisher` interface | Port through which other modules can publish notification events |

### 4.2 FORBIDDEN in Shared Kernel

| Category | Rationale |
|----------|-----------|
| **Business services** | No `Service`, `Orchestrator`, `Engine`, or `Manager` beans — these belong in individual modules |
| **Repositories / persistence** | No `Repository`, `Store`, or jOOQ query classes — each module owns its data access |
| **Workflows / orchestration** | No Temporal workflow definitions or activity implementations |
| **Provider adapters** | No Stripe, Kill Bill, Hyperswitch, Medusa, SendGrid, Twilio, or any external system adapter |
| **Module-specific DTOs** | No request/response types that belong to a single module's API |
| **Business policy** | No routing rules, quota logic, billing calculations, or entitlement decisions |
| **Scheduled jobs** | No `@Scheduled` tasks — these belong in `scheduler-module` or the owning module |

### 4.3 Cross-Module SPIs in Shared Kernel

The `NotificationEventPublisher` interface lives in `shared-kernel` as a cross-module SPI. This is **allowed** because:
- It is a port interface, not an implementation
- It enables the render-to-notification event flow without creating a dependency from `render-module` to `notification-module`

---

## 5. Module Purposes and Boundaries by Priority

### 5.1 P0 — Critical Infrastructure Modules

| Module | Purpose | Boundary Rule |
|--------|---------|---------------|
| **`observability-module`** | Logging, metrics, tracing, correlation IDs. Future OpenTelemetry integration. | Must NOT depend on business modules. Other modules may depend on it for trace context. |
| **`outbox-event-module`** | Domain events, outbox records, retry, idempotency. Default cross-module integration mechanism. | Must NOT depend on `notification-module`, `audit-compliance-module`, or any business module. |
| **`audit-compliance-module`** | Audit trail for config, prompt, policy, plugin, manual retry, permission changes. | Must NOT depend on business modules. Triggered via Spring `@EventListener` consuming domain events from `shared-kernel`. **Decoupled from render-module via events** — no direct `AuditService` calls from other modules. |

### 5.2 P1 — Platform Services Modules

| Module | Purpose | Boundary Rule |
|--------|---------|---------------|
| **`identity-access-module`** | Tenants, users, service accounts, API keys, permissions. | Must NOT depend on business modules. |
| **`scheduler-module`** | Periodic jobs, cleanup, reconciliation, compensation scans. | Must NOT depend on business modules directly. Should interact via outbox events or defined service interfaces. |
| **`quota-billing-module`** | Quota, usage, thresholds, future metering. | Must NOT depend on `entitlement-module` or `billing-module`. |

### 5.3 Commerce / Payment / Billing / Entitlement

| Module | Purpose | Boundary Rule |
|--------|---------|---------------|
| **`commerce-module`** | Canonical catalog, checkout intent, product mapping, order semantics. | Must NOT expose provider objects as internal truth. |
| **`payment-module`** | Payment provider adapter, hosted checkout, webhook ingestion, payment verification. | Must NOT expose provider objects as internal truth. |
| **`billing-module`** | Recurring contract state, invoice projection, cycle lifecycle, proration/reconciliation. | Must NOT be the source of truth for entitlement. |
| **`entitlement-module`** | Final access control, feature bundle, quota profile, overrides, grace period. | **Is the final source of truth for feature access.** |

**Event flow between these four**:
```
commerce → payment → billing → entitlement → notifications/audit
```

### 5.4 P2 — Extension and Governance Modules

| Module | Purpose | Boundary Rule |
|--------|---------|---------------|
| **`policy-governance-module`** | Policy versioning, explainability, conflict detection. OpenFeature integration. | Provides `FeatureFlagEvaluator` SPI via `feature-flags` named interface. |
| **`artifact-catalog-module`** | Artifact registration, relation, provenance. | Must NOT depend on business modules. |
| **`sandbox-runtime-module`** | Wasm/script sandbox SPI only, disabled by default. | Must NOT be enabled until script/plugin governance matures. |
| **`federation-query-module`** | Calcite/Trino placeholders only, no default heavy runtime. | Must NOT introduce heavy query engine dependencies. |

### 5.5 Core Business Modules

| Module | Purpose | Boundary Rule |
|--------|---------|---------------|
| **`render-module`** | Render job creation, policy engine, backend selection. | Depends on `ai-module` (via `AiGatewayPort`), `storage-module` (via `StorageCatalogPort`), and `shared-kernel`. Publishes domain events for audit and notification. **Does NOT directly depend on `audit-compliance-module`** — audit is event-driven. |
| **`notification-module`** | Notification event handling, template rendering, multi-channel delivery. | Depends on `shared-kernel` only. Reacts to events via `@EventListener`. |
| **`ai-module`** | AI model routing, chat providers. Exposes `AiGatewayPort` via `API` named interface. | CLOSED module. `AiGatewayService` implements `AiGatewayPort`. Other modules depend on the port, not the implementation. |
| **`config-module`** | Versioned configuration storage/retrieval. | No module dependencies. |
| **`workflow-module`** | Temporal workflow/activity definitions. | Only cross-module dependency: `policy-governance-module` (for `FeatureFlagEvaluator`). |
| **`storage-module`** | Blob storage provider catalog. Exposes `StorageCatalogPort` via `API` named interface and domain types via `domain` named interface. | CLOSED module. `StorageCatalogService` implements `StorageCatalogPort`. Other modules depend on the port, not the implementation directly. |
| **`prompt-module`** | Prompt template rendering. | Depends on `shared-kernel` only. |
| **`extension-module`** | CLI tool execution, plugin management (PF4J). | Depends on `shared-kernel` only. |
| **`datasource-module`** | Named DataSource and DSLContext registry. | Depends on `shared-kernel` only. |
| **`cloud-resource-module`** | Cloud resource provider catalog. | Depends on `shared-kernel` only. |
| **`secrets-config-module`** | Secret resolution. | Depends on `shared-kernel` only. |

---

## 6. Forbidden Dependencies

The following dependencies are **explicitly forbidden** by the architecture rules.

### 6.1 Absolute Prohibitions

| # | Forbidden Dependency | Reason |
|---|---------------------|--------|
| 1 | Any module → `platform-app` | `platform-app` is the aggregator. No module should depend on it. |
| 2 | `shared-kernel` → any other module | Shared kernel is the root. It must never depend on business modules. |
| 3 | `observability-module` → any business module | Observability is infrastructure. It must remain independent. |
| 4 | `audit-compliance-module` → any business module | Audit is infrastructure. It must remain independent. |
| 5 | `outbox-event-module` → `notification-module` | Outbox must not directly call notification. Use events. |
| 6 | `outbox-event-module` → `audit-compliance-module` | Outbox must not directly call audit. Use events. |
| 7 | `entitlement-module` → `payment-module` | Entitlement consumes billing state, not payment state. |
| 8 | `entitlement-module` → `commerce-module` | Entitlement must not depend on commerce. |
| 9 | `billing-module` → `entitlement-module` | Billing feeds entitlement, not the reverse. |
| 10 | `payment-module` → `entitlement-module` | Payment success ≠ entitlement grant. |
| 11 | `quota-billing-module` → `entitlement-module` | Quota is an input to entitlement, not dependent on it. |
| 12 | `sandbox-runtime-module` → any business module | Sandbox is isolated by design. |
| 13 | `artifact-catalog-module` → any business module | Artifact catalog is infrastructure. |
| 14 | `scheduler-module` → any business module (direct bean calls) | Scheduler must interact via outbox events or defined service interfaces. |
| 15 | `render-module` → `audit-compliance-module` (direct bean injection) | Audit must be event-driven. Use `RenderJobCompletedEvent`/`RenderJobFailedEvent`. |

### 6.2 Dependency Style Violations

| # | Violation | Details |
|---|-----------|---------|
| 1 | Using `api()` for Spring Boot starters | Leaks transitive dependencies. Should use `implementation()` unless the type is exposed in the module's API. |
| 2 | Missing `plugins { id("java-library") }` | `storage-module` and others omit the `java-library` plugin, relying on the root project's `apply(plugin = "java")`. |

### 6.3 Canonical Model Violations

| # | Violation | Details |
|---|-----------|---------|
| 1 | Using provider objects as internal truth | Commerce/Payment/Billing/Entitlement must use internal canonical models. |
| 2 | Payment state as entitlement state | `PaymentStateProjectedEvent` must not directly trigger entitlement changes. |
| 3 | Raw provider webhooks consumed directly | Webhooks must be projected into internal events first. |

---

## 7. ModularityTest Status

**Location**: `platform-app/src/test/java/com/example/platform/ModularityTest.java`

```java
class ModularityTest {
    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(PlatformApplication.class).verify();
    }
}
```

**Status**: PASSES (confirmed in Phase T1 convergence). This test verifies that all 25 modules respect Spring Modulith boundary constraints including named interface access rules.

---

## 8. Phase T1 Architecture Convergence Summary

The following changes were made in Phase T1 to converge module boundaries:

### 8.1 Removed Unnecessary Type.OPEN

| Module | Before | After | Rationale |
|--------|--------|-------|-----------|
| `ai-module` | `Type.OPEN` | CLOSED | Exposes API via `@NamedInterface("API")` on `ai.api` |
| `audit-compliance-module` | `Type.OPEN` | CLOSED | Decoupled from render via events; no external module needs direct access |
| `storage-module` | `Type.OPEN` | CLOSED | Exposes API via `@NamedInterface("API")` on `storage.api` and `@NamedInterface("domain")` on `storage.domain` |

**Retained Type.OPEN**: `shared-kernel` — justified as the shared kernel providing cross-module types.

### 8.2 Introduced Port Interfaces

| Module | Port Interface | Named Interface | Package |
|--------|---------------|-----------------|---------|
| `ai-module` | `AiGatewayPort` | `API` | `ai.api` |
| `storage-module` | `StorageCatalogPort` | `API` | `storage.api` |

### 8.3 Decoupled Audit via Events

- **Before**: `RenderOrchestratorService` directly injected `AuditService` from `audit-compliance-module`
- **After**: `RenderOrchestratorService` publishes `RenderJobCompletedEvent`/`RenderJobFailedEvent` to `ApplicationEventPublisher`; `AuditEventHandler` in `audit-compliance-module` consumes these events via `@EventListener`
- **Removed**: `audit-compliance-module` from `render-module`'s `allowedDependencies`

### 8.4 New Domain Events in Shared Kernel

| Event | Published By | Consumed By |
|-------|-------------|-------------|
| `RenderJobCompletedEvent` | `RenderOrchestratorService` | `AuditEventHandler` |
| `RenderJobFailedEvent` | `RenderOrchestratorService` | `AuditEventHandler` |

### 8.5 Named Interface Conventions

- `@NamedInterface("API")` on `api` packages — exposes port interfaces and controllers
- `@NamedInterface("domain")` on `domain` packages — exposes domain value objects and interfaces
- `allowedDependencies` format: `"moduleName :: interfaceName"` for named interfaces, `"moduleName"` for default interface

---

## 9. Reference: Spring Modulith Architecture Rules

From [`architecture-notes.md`](./architecture-notes.md):

1. Temporal handles durable orchestration.
2. LiteFlow handles local policy/routing decisions.
3. Outbox is the default cross-module integration mechanism.
4. Public APIs use `/api/v1/**`; internal evolution should remain backward-compatible.
5. Every long-running job should emit audit records and carry trace correlation identifiers.
6. Multi-datasource is managed via named DataSources and named jOOQ `DSLContext`s.

From [`.roo/rules/10-coding-standards.md`](.roo/rules/10-coding-standards.md):

- Critical cross-module changes must go through Outbox.
- Do not directly call notification, payment, entitlement, render, workflow, or AI modules from unrelated modules unless through defined ports, APIs, or events.
