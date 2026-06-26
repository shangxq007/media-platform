---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-013: Platform Kernel Baseline 1.0

## Context

Foundations F1-F15 and Capability C1 have established a complete platform execution kernel: Product Runtime, Product Graph, Storage Runtime, Producer Runtime, Execution Planner, Capability Resolution, Capability Catalog, Backend Compiler Runtime, Execution Pipeline, and specialized BackendExecutionSpec hierarchy.

This ADR freezes the kernel architecture as Platform Kernel Baseline 1.0.

## Decision

1. The platform kernel architecture is frozen as documented in [Platform Kernel Baseline](../platform-kernel.md)
2. Eight Platform Runtime Rules are binding on all future development
3. Eight SPIs are marked as Platform Stable API v1.0
4. Future capabilities (OpenCue, Remotion, MLT, Storage Providers, Marketplace) are Capability Extensions — NOT platform kernel work
5. Adding new Runtimes or Registries requires ADR approval

## Consequences

- All future providers follow the same pattern: Producer → Compiler → Backend
- No runtime proliferation — 5 runtimes maximum
- No registry proliferation — 4 registries maximum
- Dependency direction is fixed: Planner → Capability → Producer → Compiler → Pipeline → Backend

## Frozen Architecture

See [Platform Kernel Baseline](../platform-kernel.md) for complete inventory.

## Future Evolution Rules

1. New AI providers → Producer + CapabilityDescriptor + Compiler + Backend (no ADR)
2. New render backends → ExecutionBackend + BackendCompiler (no ADR)
3. New runtime → ADR required
4. New registry → ADR required
