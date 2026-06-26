---
status: architecture-release
created: 2026-06-25
scope: platform-wide
truth_level: frozen
owner: platform
---

# Architecture Release A1 — Runtime & Registry Governance

> **Release Date:** 2026-06-25
> **Scope:** All platform runtimes and registries
> **Status:** Frozen — no new runtimes or registries without ADR

---

## 1. Runtime Inventory

### Outbox Runtime

| Property | Value |
|----------|-------|
| **Purpose** | Reliable at-least-once event delivery |
| **Owner** | `outbox-event-module` |
| **Key Components** | `OutboxEventService`, `OutboxEventDispatcher`, `outbox_events` table |
| **Allow Depends On** | PostgreSQL, Domain Events, Spring ApplicationEventPublisher |
| **Forbidden Depends On** | Coordination Runtime, Extension Runtime, Business Services |
| **Extension Point** | `OutboxEventRouter` (registration-based routing) |

### Coordination Runtime

| Property | Value |
|----------|-------|
| **Purpose** | Fan-out/fan-in job coordination, task lease, retry, barrier |
| **Owner** | `outbox-event-module` |
| **Key Components** | `PlatformJobRepository`, `PlatformTaskRepository`, `PlatformCoordinationService`, `PlatformTaskDispatcher`, `platform_job`, `platform_task` tables |
| **Allow Depends On** | PostgreSQL, TaskHandlerRegistry, OutboxEventService (for completion events) |
| **Forbidden Depends On** | Business logic (Asset, Timeline, Review, Marketplace), Provider Extension SPI |
| **Extension Point** | `TaskHandler` SPI |

### Execution Runtime

| Property | Value |
|----------|-------|
| **Purpose** | Where task execution happens (local process, future: BMF, OpenCue, K8s) |
| **Owner** | `outbox-event-module` |
| **Key Components** | `ExecutionBackend` SPI, `ExecutionBackendRegistry`, `LocalProcessExecutionBackend`, `ExecutionRequest`, `ExecutionResult` |
| **Allow Depends On** | ProcessBuilder, CLI tools (ffprobe, whisper, tesseract) |
| **Forbidden Depends On** | Business logic, Semantic Metadata, Search/Marketplace |
| **Extension Point** | `ExecutionBackend` SPI (new backends: BMF, OpenCue, K8s) |

### Extension Runtime

| Property | Value |
|----------|-------|
| **Purpose** | Plugin lifecycle: register, load, execute, unload, rollback |
| **Owner** | `extension-module` |
| **Key Components** | `ExtensionRegistryService`, `ProviderExtensionSPI`, `ExtensionContext`, `ExtensionResult`, `ExtensionTrustLevel`, `ExtensionResourceLimits`, PF4J `PluginManager` |
| **Allow Depends On** | Provider implementations (Whisper, Tesseract) |
| **Forbidden Depends On** | TaskHandler logic, Semantic Metadata, Search/Marketplace |
| **Extension Point** | `ProviderExtensionSPI`, `PromptExtensionSPI`, `WorkflowStepExtensionSPI` |

### Provider Runtime

| Property | Value |
|----------|-------|
| **Purpose** | AI/Media logic only — no orchestration, no persistence, no event publishing |
| **Owner** | `render-module` (provider implementations) |
| **Key Components** | `WhisperAsrProvider`, `TesseractOcrProvider` |
| **Allow Depends On** | ExecutionBackend, CLI tools |
| **Forbidden Depends On** | AssetSemanticMetadata, OutboxEventService, Search/Marketplace, TaskHandler, Business logic |
| **Extension Point** | None — providers are leaf nodes |

---

## 2. Registry Inventory

### ExtensionRegistryService

| Property | Value |
|----------|-------|
| **Purpose** | **Authoritative registry for all platform extensions** |
| **Registration Source** | `ProviderExtensionSPI` implementations (self-register via `@PostConstruct`) |
| **Lookup Strategy** | By provider key (`"whisper"`, `"tesseract"`) |
| **Lifecycle** | Active → Unloaded → Rollback |
| **Authority** | **Single source of truth for extensions** |

### TaskHandlerRegistry

| Property | Value |
|----------|-------|
| **Purpose** | Capability-based handler lookup (PROBE → ProbeTaskHandler, ASR → RealAsrTaskHandler) |
| **Registration Source** | Spring auto-discovery of `TaskHandler` beans via `@PostConstruct` |
| **Lookup Strategy** | By `TaskCapability` enum |
| **Authority** | Handler lookup only — not authoritative for anything else |

### ExecutionBackendRegistry

| Property | Value |
|----------|-------|
| **Purpose** | Capability-based execution backend lookup |
| **Registration Source** | Spring auto-discovery of `ExecutionBackend` beans |
| **Lookup Strategy** | By `TaskCapability` enum |
| **Authority** | Backend lookup only |

### OutboxEventRouter

| Property | Value |
|----------|-------|
| **Purpose** | Event type → Java class routing for deserialization |
| **Registration Source** | `OutboxEventRegistration` (`@PostConstruct` — 21 event types) |
| **Lookup Strategy** | By event type string (`"asset.published"`) |
| **Authority** | Event routing only |

---

## 3. Dependency Matrix

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ALLOWED DEPENDENCY DIRECTION                       │
│                                                                      │
│  TaskHandler                                                         │
│      ↓ (routes through Extension Runtime)                            │
│  ExtensionRegistryService.executeProvider()                          │
│      ↓ (resolves by provider key)                                    │
│  ProviderExtensionSPI.execute()                                      │
│      ↓ (delegates to provider implementation)                        │
│  WhisperAsrProvider / TesseractOcrProvider                           │
│      ↓ (delegates to Execution Runtime)                              │
│  ExecutionBackend.execute()                                          │
│      ↓ (executes subprocess)                                         │
│  PostgreSQL / ProcessBuilder / CLI                                   │
│                                                                      │
│                    FORBIDDEN (reverse)                                │
│                                                                      │
│  ExecutionBackend → Provider ✗                                       │
│  Provider → TaskHandler ✗                                            │
│  Provider → Semantic Metadata ✗ (not directly)                      │
│  ExecutionBackend → Outbox ✗                                         │
│  TaskHandler → Provider (directly) ✗                                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. Runtime Flow (Canonical)

```
1. Domain Service publishes DomainEvent
2. OutboxEventDispatcher delivers event
3. CoordinationConsumer creates PlatformJob + PlatformTask[]
4. PlatformTaskDispatcher picks up task (lease, capability resolution)
5. TaskHandlerRegistry.resolve(capability) → TaskHandler
6. TaskHandler.execute():
    a. extensionRegistry.executeProvider(key, inputJson, tenantId, traceId)
    b. ProviderExtensionSPI.execute(context, inputJson) → ExtensionResult
    c. Extract result, update AssetSemanticMetadata
    d. Publish AssetEnrichedEvent (via OutboxEventService)
7. OutboxEventDispatcher → SearchConsumer → reindex
8. Workbench / Marketplace read from projections
```

---

## 5. Governance Rules (7)

| # | Rule | Enforced By |
|---|------|-------------|
| 1 | No Provider Registry outside `ExtensionRegistryService` | Architecture review |
| 2 | No Provider called directly by TaskHandler — must go through `extensionRegistry.executeProvider()` | Code review |
| 3 | Execution must go through `ProviderExtensionSPI.execute()` | Architecture test |
| 4 | `AssetSemanticMetadata` is the single source of truth for AI results | Code review |
| 5 | Search Projection, Marketplace Listing, Dashboard are projections only — rebuildable | Architecture design |
| 6 | No provider creates its own retry or scheduling — reuse PlatformTask | Architecture review |
| 7 | No provider stores its own runtime state — reuse coordination runtime | Architecture review |

---

## 6. Architecture Decision Records

### ADR-001: Runtime Responsibilities

**Decision:** Six runtimes with single responsibilities. No runtime may own another runtime's concern.

### ADR-002: Registry Responsibilities

**Decision:** Four registries. `ExtensionRegistryService` is authoritative for extensions. Other registries are capability indices.

### ADR-003: Provider Governance

**Decision:** All providers implement `ProviderExtensionSPI`, self-register, route through extension runtime. No provider-specific registries or runtimes.

### ADR-004: Execution Flow

**Decision:** `TaskHandler → ExtensionRuntime → ProviderExtensionSPI → ExecutionBackend → CLI`. No shortcuts. Every layer is abstracted.

---

## 7. Validation Results

| Flow | Satisfies Rules? |
|------|-----------------|
| Render (RenderJob → Artifact → Outbox) | ✅ |
| Timeline (Patch → Revision → Outbox → Notification) | ✅ |
| Review (Review → Comment → Approval → Merge Guard) | ✅ |
| Asset (Registry → Semantic → Enrichment → Search) | ✅ |
| Marketplace (Publish → Listing → Search → Discovery) | ✅ |
| Whisper (PlatformTask → ExtensionRuntime → Whisper → Semantic) | ✅ |
| OCR (PlatformTask → ExtensionRuntime → Tesseract → Semantic) | ✅ |

**No violations found.**

---

## 8. Future Providers

All future providers follow the same canonical flow. No exceptions.

| Provider | Type | Dependencies |
|----------|------|-------------|
| Vision (YOLO) | `ProviderExtensionSPI` | `ExecutionBackend` |
| Embedding (CLIP) | `ProviderExtensionSPI` | `ExecutionBackend` |
| BMF Media | `ProviderExtensionSPI` | `ExecutionBackend` |
| OpenCue Render Farm | `ProviderExtensionSPI` | `ExecutionBackend` |

---

## 9. Related Documents

| Document | Relationship |
|----------|-------------|
| [Domain Event & Outbox Blueprint](../architecture/blueprint/domain-event-outbox-blueprint.md) | Outbox runtime design |
| [Platform Coordination Blueprint](../architecture/blueprint/platform-coordination-blueprint.md) | Coordination runtime design |
| [Provider Extension Runtime](provider-extension-runtime.md) | Extension runtime design |
| [Provider Governance](provider-governance.md) | Provider governance rules |
| [Execution Backend Abstraction](execution-backend-abstraction.md) | Execution runtime design |
