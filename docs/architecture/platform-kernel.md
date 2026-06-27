---
status: frozen
version: 1.0
created: 2026-06-26
scope: platform-wide
owner: platform
---

# Platform Kernel Baseline 1.0

## 0. Canonical Domain Model

### Asset
User-owned logical media. Examples: uploaded video, audio, image, subtitle, font.
Asset represents ownership. Asset is NOT execution. Asset is NOT build output.

### Product
The canonical object produced, consumed, transformed and tracked by the platform.
Every build result is a Product. Examples: transcript, OCR result, embedding, thumbnail,
proxy, preview, final render, package, timeline edit plan, search index, feature vector.

### Artifact
**Descriptive terminology only.** NOT a root domain concept.
"Artifact" describes a file-backed Product (e.g., "media artifact", "render artifact").
No independent Artifact runtime exists or should be created.

### Kernel Invariants

| # | Invariant |
|---|-----------|
| 1 | Asset is user-owned |
| 2 | Product is platform-owned (the canonical build object) |
| 3 | Artifact is descriptive terminology only |
| 4 | Product Graph is always a DAG (cycles rejected) |
| 5 | Execution Task Graph is always a DAG |
| 6 | Planner is pure (computation only, no side effects) |
| 7 | Backend is stateless (no local state between executions) |
| 8 | Environment owns execution only (no planning, no product lifecycle) |
| 9 | Storage is transparent (ExecutionBackend never resolves paths directly) |
| 10 | Every Product has complete lineage (producer, upstream Products, originating Asset/TimelineRevision) | ✅ Validated — `hasProvenance()` enforced on registration |

### Provenance Enforcement (S2)

`ProductRuntimeService.register()` validates `Product.hasProvenance()` before persistence. At least one of `ownerAssetId`, `producerId`, or `sourceTimelineRevisionId` must be non-null. Products without provenance are rejected with `"Product must have provenance"`.

Root products: `ownerAssetId` (e.g., uploaded media).
Derived products: `producerId` (e.g., AI-generated transcript).
Timeline products: `sourceTimelineRevisionId` (e.g., render output, mutation).

## 1. Runtime Inventory

### Product Runtime
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | ProducerResult |
| **Outputs** | Product (registered), ProductGraph edges |
| **Extension Point** | None — consumed via ProductRuntimeService |
| **Forbidden** | Execute producers, resolve storage, plan execution |

### Storage Runtime
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | StorageReferenceId |
| **Outputs** | Local file path (materialize), checksum verification |
| **Extension Point** | StorageProviderType (LOCAL only in V1) |
| **Forbidden** | Execute producers, resolve business logic, plan execution |

### Producer Runtime
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | ProducerContext |
| **Outputs** | ProducerResult |
| **Extension Point** | Producer SPI (auto-discovered via Spring) |
| **Forbidden** | Own storage, plan execution, resolve backends |

### Backend Compiler Runtime
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | ExecutionPlan |
| **Outputs** | BackendExecutionSpec |
| **Extension Point** | BackendCompiler SPI (auto-discovered via Spring) |
| **Forbidden** | Execute, access storage, call producers |

## 2. Planning Inventory

### Execution Planner
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | targetProductId, targetProductType |
| **Outputs** | ExecutionPlan (stages, steps, backend selections) |
| **Extension Point** | CapabilityResolutionService (resolution strategy) |
| **Forbidden** | Execute anything, resolve storage, call compilers directly |

### Capability Resolution
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | productType |
| **Outputs** | ResolutionResult (capability, producerId, backendId, reason) |
| **Extension Point** | CapabilityCatalogService (producer metadata) |
| **Forbidden** | Execute producers, plan execution, own backends |

### Capability Catalog
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | Producer beans (Spring auto-discovery) |
| **Outputs** | CapabilityDescriptor list, capability index |
| **Extension Point** | CapabilityDescriptor (producer SPI default) |
| **Forbidden** | Execute, own backends, persist metadata |

## 3. Execution Inventory

### Execution Pipeline
| Property | Value |
|----------|-------|
| **Owner** | render-module |
| **Inputs** | ExecutionPlan |
| **Outputs** | ExecutionPipelineResult |
| **Extension Point** | None — pure orchestration |
| **Forbidden** | Own backends, resolve storage, plan execution |

### Execution Backend
| Property | Value |
|----------|-------|
| **Owner** | outbox-event-module |
| **Inputs** | ExecutionRequest |
| **Outputs** | ExecutionResult |
| **Extension Point** | ExecutionBackend SPI (LocalProcess, BMF) |
| **Forbidden** | Plan execution, own products, resolve producers |

## 4. Compilation Inventory

### Backend Compiler
| Property | Value |
|----------|-------|
| **Owner** | render-module (SPI), outbox-event-module (implementations) |
| **Inputs** | ExecutionPlan |
| **Outputs** | BackendExecutionSpec (LocalProcess or BMF) |
| **Extension Point** | BackendCompiler SPI (two implementations) |
| **Forbidden** | Execute, access storage, call producers |

### BackendExecutionSpec Hierarchy
| Spec | Backend | Fields |
|------|---------|--------|
| `BackendExecutionSpec` (interface) | Common | executionSpecId, backendId, backendType, producerId, inputs, outputs, hints |
| `LocalProcessExecutionSpec` | local-process | +executable, arguments, environment, workingDirectory |
| `BmfExecutionSpec` | bmf | +graphDefinition, graphInputs, graphOutputs, graphOptions |

## 5. Canonical Execution Flow

```
Need Product
    ↓
Execution Planner (plan target + dependencies)
    ↓
Capability Resolution (resolve productType → capability → producer → backend)
    ↓
Capability Catalog (resolve preferred producer by priority)
    ↓
Producer Runtime (execute producer with context)
    ↓
Backend Compiler Runtime (select compiler by backendType)
    ↓
Backend Compiler (translate plan → BackendExecutionSpec)
    ↓
Execution Pipeline (orchestrate compile → execute → update products)
    ↓
Execution Backend (execute spec → ExecutionResult)
    ↓
Product Runtime (register outputs, create dependencies)
    ↓
Product Graph (track lineage)
```

Each stage owns one responsibility. No stage overlaps.

## 6. Stable SPIs (Platform Stable API v1.0)

| SPI | Stability | Implementers | Lifecycle Owner |
|-----|-----------|-------------|-----------------|
| `Producer` | Stable | AI providers, media providers, mutation providers | Producer Runtime |
| `BackendCompiler` | Stable | LocalProcess, BMF, future OpenCue | Compiler Runtime |
| `BackendExecutionSpec` | Stable | LocalProcessExecutionSpec, BmfExecutionSpec | Compiler Runtime |
| `ExecutionBackend` | Stable | LocalProcess, BMF, future OpenCue | Execution Runtime |
| `CapabilityDescriptor` | Stable | Producer.default descriptor() | Capability Catalog |
| `ProviderExtensionSPI` | Stable | Whisper, Tesseract, Vision, Embedding | Extension Runtime |

## 7. Runtime Governance Rules

| Rule | Rationale |
|------|-----------|
| Planner never executes | Planning is a computation; execution is a side effect |
| Compiler never stores | Compiler translates; ProductRuntime persists |
| Backend never plans | Backend executes; Planner plans |
| Product Runtime never executes | ProductRuntime tracks; ProducerRuntime invokes |
| Storage Runtime never resolves business logic | Storage owns physical data; Product owns logical metadata |
| Capability Resolution never executes providers | Resolution resolves; ProducerRuntime invokes |
| Producer never owns storage | Producer consumes Products; StorageRuntime materializes |
| Execution Pipeline only orchestrates | Pipeline wires runtimes; no business logic |

## 8. Dependency Rules

```
Allowed: Planner → CapabilityResolution → CapabilityCatalog → Producer → Compiler → Pipeline → Backend

Forbidden: ExecutionBackend → Planner
Forbidden: Producer → StorageRepository
Forbidden: Compiler → ProductRepository
Forbidden: Planner → ProcessBuilder
Forbidden: ProductRuntime → ExecutionBackend
```

## 9. Platform Layers

```
Planning Layer:     Execution Planner, Capability Resolution, Capability Catalog
Capability Layer:   Producer Runtime, Producer SPI, CapabilityDescriptor
Compilation Layer:  Backend Compiler Runtime, BackendCompiler SPI, BackendExecutionSpec
Execution Layer:    Execution Pipeline, Execution Backend, ExecutionBackend SPI
Runtime Layer:      Product Runtime, Product Graph, Storage Runtime
Storage Layer:       StorageReference, StorageRuntimeService
Governance Layer:    Metering, Access Control, Policy, Pricing, Cost, Billing (future — see ADR-018)
```

No component belongs to multiple layers.

## 10. Extension Points

| What | How | Required ADR? |
|------|-----|---------------|
| New AI Provider | Producer + CapabilityDescriptor + Compiler + Backend | No |
| New Render Backend | ExecutionBackend + BackendCompiler + ExecutionSpec | No |
| New Runtime | None of the above | Yes (ADR required) |
| New Registry | None of the above | Yes (ADR required) |

## 11. Component Descriptor Model (ADR-019)

Every extensible component is self-describing through a unified model (Identity + Capability + Resource + Metering + Security + Configuration + Observability + Health). Metadata belongs to the component, never the platform. Platform aggregates metadata, governance consumes it. See [Component Descriptor Architecture](component-descriptor.md).
