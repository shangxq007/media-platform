---
status: accepted
created: 2026-06-27
scope: platform-wide
owner: platform
---

# ADR-021: Capability Composition

## Context
Platform Kernel is frozen. Users need to compose capabilities into pipelines (ASR → Summary → Timeline → Render). Internal SPIs must not be exposed.

## Decision
1. Introduce Capability Composition — users compose Public Capabilities, not internal SPIs
2. Workflow Layer sits above Capability Runtime, below Applications
3. Nodes exchange Products (not implementation objects)
4. Platform resolves implementation automatically (Capability → Producer → Backend)
5. Every node passes through Access → Policy → Metering → Execution
6. Marketplace distributes Capabilities, Templates, and Nodes — never Kernel

## Consequences
- Users never see internal SPIs (Producer, BackendCompiler)
- Platform owns implementation selection
- Kernel remains unchanged across all 4 platform versions
