---
status: frozen
version: 1.0
created: 2026-06-26
scope: platform-wide
owner: platform
---

# Platform Kernel Baseline 1.0

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
Storage Layer:      StorageReference, StorageRuntimeService
```

No component belongs to multiple layers.

## 10. Extension Points

| What | How | Required ADR? |
|------|-----|---------------|
| New AI Provider | Producer + CapabilityDescriptor + Compiler + Backend | No |
| New Render Backend | ExecutionBackend + BackendCompiler + ExecutionSpec | No |
| New Runtime | None of the above | Yes (ADR required) |
| New Registry | None of the above | Yes (ADR required) |
