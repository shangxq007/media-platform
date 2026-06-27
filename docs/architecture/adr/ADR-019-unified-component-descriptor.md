---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-019: Unified Component Descriptor

## Context
Different extensible components (Producer, Backend, Environment, Storage, Compiler) expose different metadata models. The platform should converge on a unified self-description model.

## Decision
Every extensible component is self-describing through a unified ComponentDescriptor model:
1. Identity (componentId, name, version, vendor)
2. Capability (supported capabilities)
3. Resource (CPU, memory, GPU, disk)
4. Metering (declared units, never prices)
5. Security (trust level, permissions)
6. Configuration (options, defaults)
7. Observability (metrics, tracing)
8. Health (health endpoint)

Metadata belongs to the component. Platform aggregates metadata. Governance consumes metadata.

## Why No Centralized Registry
A centralized registry would own metadata that rightfully belongs to the component. Component identity is immutable. Changes to capabilities, resources, or configuration should come from the component itself, not from a platform-managed registry. Spring auto-discovery provides the mechanism.

## Consequences
- Descriptor-driven behavior over hardcoded branching
- Governance reads descriptors, never modifies them
- Capability Catalog is complemented, not replaced
- All component types specialize the same conceptual model
