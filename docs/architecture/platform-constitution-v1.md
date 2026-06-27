---
status: frozen
version: 1.0
created: 2026-06-27
scope: platform-wide
owner: chief-platform-architect
---

# Platform Constitution v1.0

> **Status:** FROZEN — This document is the authoritative architectural reference for the platform.
> **Effective:** 2026-06-27
> **Last Validated:** Four independent architecture validations (OpenCue, Storage Providers, Whisper, Remotion)

## 1. Frozen Architecture

The following architecture elements are FROZEN and MUST NOT be redesigned without ADR:

| Layer | Component | Status |
|-------|-----------|--------|
| Domain | Product Model (Asset → Product → Product Graph) | Frozen |
| Domain | Execution Model (Job → Task → Command → Lifecycle) | Frozen |
| Planning | Execution Planner + Capability Resolution + Capability Catalog | Frozen |
| Runtime | ProductRuntime, ProducerRuntime, StorageRuntime | Frozen |
| Execution | Execution Pipeline, Execution Control Service | Frozen |
| Environment | ExecutionEnvironment SPI + EnvironmentCompiler SPI | Frozen |
| Backend | BackendCompiler SPI + ExecutionBackend SPI | Frozen |
| Governance | AccessGovernanceService, MeteringService | Frozen |
| Descriptor | Component Descriptor Model (Identity/Capability/Resource/Metering/Security/Config/Observability/Health) | Frozen |

## 2. Stable Public SPIs (API v1.0)

| SPI | Purpose | Compatibility |
|-----|---------|---------------|
| `Producer` | Capability execution entry | Backward-compatible |
| `BackendCompiler` | Plan → ExecutionSpec translation | Backward-compatible |
| `ExecutionEnvironment` | Where execution happens | Backward-compatible |
| `ExecutionBackend` | How execution happens | Backward-compatible |
| `StorageProvider` | Object storage abstraction | Backward-compatible |
| `AccessGovernanceService` | Single authorization entry | Stable |
| `MeteringService` | Single metering entry | Stable |

Breaking changes to any SPI require an ADR.

## 3. Stable Runtime Services

| Service | Owns | Forbidden |
|---------|------|-----------|
| `ProductRuntimeService` | Product lifecycle, dependency graph | Execute, resolve storage, plan |
| `ProducerRuntimeService` | Producer discovery, execution | Own storage, plan, resolve backends |
| `StorageRuntimeService` | Materialization, checksum | Execute producers, resolve business logic |
| `ExecutionPlannerService` | Plan computation | Execute, resolve storage, call compilers directly |
| `CapabilityResolutionService` | ProductType → capability → producer → backend | Execute producers, plan |
| `ExecutionControlService` | Job submit/cancel/status | Execute directly, own storage |
| `AccessGovernanceService` | Authorization decisions | Price, bill, meter |
| `MeteringService` | Consumption facts | Price, bill, authorize |

## 4. Kernel Invariants (10)

1. Asset is user-owned
2. Product is platform-owned (the canonical build object)
3. Artifact is descriptive terminology only
4. Product Graph is always a DAG (cycles rejected)
5. Execution Task Graph is always a DAG
6. Planner is pure (computation only, no side effects)
7. Backend is stateless (no local state between executions)
8. Environment owns execution only (no planning, no product lifecycle)
9. Storage is transparent (ExecutionBackend never resolves paths directly)
10. Every Product has complete lineage (producerId, upstream Products, originating Asset/TimelineRevision)

## 5. Architecture Evolution Policy

| Category | Scope | Requires ADR? |
|----------|-------|---------------|
| A — Internal Improvement | Performance, bug fixes, logging | No |
| B — Capability Extension | New Producer, Backend, Environment, Storage Provider | No |
| C — Kernel Evolution | New Runtime, SPI change, domain model change, lifecycle change | Yes |

## 6. Governance Evolution Policy

**May evolve** without ADR:
- RBAC rules (add roles/permissions)
- ABAC rules (add context attributes)
- Pricing models
- Policy engine implementation
- OpenMeter adapter
- Quota configuration

**Must remain stable:**
- Metering records facts — never prices
- Access decides execution — never bills
- Providers never declare commercial policy
- Cost and Billing are independent

## 7. Multi-Agent Development Policy

**Agents may add** without ADR:
- Producers, Backends, Environments, Storage Providers, Capabilities

**Agents may NOT modify** without ADR:
- Platform Kernel, Execution Model, Domain Model, Stable SPIs

## 8. ADR Governance

ADR is mandatory for: Kernel redesign, SPI modification, Governance redesign, Domain model changes, Execution lifecycle changes, Storage model changes, Planner redesign.

## 9. Validation Summary

| Validation | Capability | Kernel Changes? | Result |
|-----------|-----------|----------------|--------|
| C8 | OpenCue Distributed Execution | None | ✅ Passed |
| C9 | Storage Providers (MinIO/S3) | None | ✅ Passed |
| C10 | Whisper End-to-End AI | None | ✅ Passed |
| C11 | Remotion JavaScript Rendering | None | ✅ Passed |

**All four independent validations confirm Platform Kernel stability.**

## 10. Related Documents

| Document | Purpose |
|----------|---------|
| [Platform Kernel Baseline](platform-kernel.md) | Architecture inventory |
| [Architecture Freeze Report](../review/architecture-freeze-report-v1.md) | Freeze decision record |
| [Platform Governance](platform-governance.md) | Governance blueprint |
| [Component Descriptor](component-descriptor.md) | Descriptor architecture |
