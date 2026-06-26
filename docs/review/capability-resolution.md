---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F15 — Capability Resolution Foundation

## Capability Resolution: Replace Hardcoded Mapping

| Before (F14) | After (F15) |
|------------|-----------|
| `ExecutionPlannerService.selectBackend()` + `mapProductTypeToCapability()` inline | `CapabilityResolutionService.resolve(productType)` → ResolutionResult |
| Planner imports ExecutionBackendRegistry directly | Planner depends on CapabilityResolutionService |
| Hardcoded capability mapping | Centralized in CapabilityResolutionService |

## New Components

| Component | Purpose |
|-----------|---------|
| `CapabilityResolutionService` | Resolves productType → capability → producer → backend → ResolutionResult (capability, producerId, backendId, backendType, reason, resolved) |

## Modified

| Component | Change |
|-----------|--------|
| `ExecutionPlannerService` | Replaces `backendRegistry` + `selectBackend()` + `mapProductTypeToCapability()` with `capabilityResolver.resolve()` |
| `Producer` SPI | +`requiredCapabilities()`, `preferredBackend()`, `supportedRepresentations()` — all default methods, no breakage |

## Resolution Flow

```
ProductType → CapabilityResolutionService.resolve()
  → mapToCapability(productType) → TaskCapability
  → ProducerRuntimeService.listProducers() → select first
  → ExecutionBackendRegistry.resolve(capability) → select backend
  → ResolutionResult { capability, producerId, backendId, backendType, reason, resolved }
```

## Explain Output (Enhanced)

```
Plan eplan_1:
  Stage 0 (sequential):
    - MISSING → Producer whisper-asr → [TRANSCRIPT] [backend=local-process reason=local-process supports ASR]
```

## Planner Dependencies Simplified

| Before | After |
|--------|-------|
| ProducerRuntime + ProductRuntime + ExecutionBackendRegistry | ProducerRuntime + ProductRuntime + CapabilityResolutionService |

Backend resolution now centralized in CapabilityResolutionService.

## Tests
Compilation passes. All existing tests unaffected. Producer SPI backward-compatible (default methods).

## Deferred Items
- Dynamic producer capability declaration (runtime discovery vs compile-time map)
- Tenant/project policy overrides
