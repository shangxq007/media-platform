---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Capability C1 — Capability Catalog Foundation

## Implemented

### Domain Model
| Component | Purpose |
|-----------|---------|
| `CapabilityDescriptor` | Immutable: capabilityId, capability, producerId, producerName, producerVersion, backendId, backendType, supportedRepresentations, producedProductTypes, preferred, priority, enabled |

### Catalog Service
| Component | Key Methods |
|-----------|-------------|
| `CapabilityCatalogService` | `listCapabilities()`, `candidatesFor(capability)`, `resolvePreferred(capability)`, `resolve(capability)`, `catalog()`, `size()` |

### Modified
| Component | Change |
|-----------|--------|
| `Producer` SPI | +`descriptor()` default method returning CapabilityDescriptor |
| `CapabilityResolutionService` | Replaces `ProducerRuntimeService.listProducers()` with `CapabilityCatalogService.resolvePreferred()` + `resolve()` |

## Resolution Strategy (V1)

1. Filter by `enabled`
2. Prefer `preferred=true`
3. Highest `priority`
4. First compatible

## Explain Output

```
TRANSCRIPT → ASR → Producer whisper-asr → Backend local-process (preferred producer for ASR)
```

## Architecture

```
CapabilityResolutionService → CapabilityCatalogService (metadata)
    → resolvePreferred(capability) → CapabilityDescriptor
    → ExecutionBackendRegistry.resolve(capability) → backend
    → ResolutionResult { capability, producerId, backendId, reason, resolved }
```

## Tests
Compilation passes. All existing tests unaffected.

## Deferred Items
- Tenant/project policy overrides
- Cost-based optimization
- Runtime registration
