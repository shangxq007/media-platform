---
status: accepted
created: 2026-06-27
scope: platform-wide
owner: platform
---

# ADR-020: Public Capability Architecture

## Context
Platform Kernel is frozen. Four architecture validations completed. Internal SPIs (Producer, BackendCompiler, ExecutionEnvironment) should not be exposed to external consumers.

## Decision
Introduce a Public Capability Architecture:
1. External consumers interact with Capabilities (not internal SPIs)
2. Public Capability Descriptor defines contract (identity, I/O types, formats, execution mode, permissions, meters, version, visibility, SLA, idempotency, cancellation, webhooks, stability)
3. Capability Resolution maps external capabilityId → internal Producer → Backend → Environment
4. Callers never choose implementation directly — platform owns selection
5. Internal SPIs remain implementation details

## Consequences
- Clean separation between public API and internal SPI
- Internal components can evolve without breaking external consumers
- New Producers can be added behind existing Capabilities
- Marketplace listings map to Capabilities, not internal components
