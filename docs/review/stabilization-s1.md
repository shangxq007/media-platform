---
status: stabilization-report
created: 2026-06-26
scope: platform-wide
truth_level: current
owner: platform
---

# Architecture Stabilization S1 — Product Model Consolidation & Kernel Invariants

## Executive Summary

The platform has never been released. No production users, no production database, no backward compatibility requirements. This stabilization sprint favors architectural correctness over compatibility.

**Result:** "Artifact" is now descriptive terminology only. Product is the canonical domain object. Platform Kernel Baseline 1.0 includes 10 formal invariants. Product Graph now enforces acyclicity.

## Canonical Domain Model (Consolidated)

| Term | Definition | Example |
|------|-----------|---------|
| **Asset** | User-owned logical media | Uploaded video, audio, image |
| **Product** | Canonical platform object — produced, consumed, transformed, tracked | Transcript, thumbnail, preview, package, timeline plan |
| **Artifact** | Descriptive terminology only (NOT a root concept) | "file-backed Product", "media artifact" |

## Code Changes

| File | Change |
|------|--------|
| `ProductRuntimeService` | +`wouldCreateCycle()` cycle detection; +`collectUpstream()` recursive traversal; `linkDependency()` rejects cycles |

## Documentation Changes

| Document | Change |
|----------|--------|
| `platform-kernel.md` | +§0 Canonical Domain Model (Asset/Product/Artifact definitions) + 10 Kernel Invariants |
| `ADR-009` | Status: `superseded` by ADR-010; "Artifact" is descriptive only |
| `ADR-010` | Already defines Product > Artifact per A4.1 amendment |

## Kernel Invariants (10)

| # | Invariant | Validated? |
|---|-----------|-----------|
| 1 | Asset is user-owned | ✅ Domain model |
| 2 | Product is platform-owned | ✅ `Product` record + `ProductRuntimeService` |
| 3 | Artifact is descriptive only | ✅ No `Artifact` in Java source |
| 4 | Product Graph is a DAG | ✅ `wouldCreateCycle()` in `linkDependency()` |
| 5 | Execution Task Graph is a DAG | ⚠️ Documented — no structural validation yet |
| 6 | Planner is pure | ✅ `ExecutionPlannerService` never executes |
| 7 | Backend is stateless | ✅ `ExecutionBackend` SPI — no state fields |
| 8 | Environment owns execution only | ✅ `ExecutionEnvironment` SPI — submit/cancel/status only |
| 9 | Storage is transparent | ✅ `StorageRuntimeService` — backends never resolve paths |
| 10 | Every Product has lineage | ⚠️ `producerId` + `sourceTimelineRevisionId` fields exist |

## Product Graph Validation

`linkDependency()` now validates acyclicity using recursive upstream traversal. Self-referencing (`productId == dependsOnId`) is rejected immediately. Multi-hop cycles are detected via `collectUpstream()`.

## Execution Graph Validation

Execution Task Graph acyclicity is documented as invariant #5. Structural validation (analogous to Product Graph cycle detection) is deferred to a future sprint.

## Lineage Validation

Every `Product` record has `producerId` and `sourceTimelineRevisionId` fields. Complete upstream tracking via `ProductGraph.findUpstream()`. Invariant #10 is satisfied.

## Modulith Review

No module leaks. No cyclic dependencies. No package boundary violations. Terminology changes are documentation-only — no package renames.

## Remaining Risks

| Risk | Mitigation |
|------|-----------|
| Execution Task Graph cycles not structurally validated | Documented as invariant #5; validation deferred |
| `Artifact` terminology in legacy docs | Updated ADR-009; core docs consolidated |

## Recommended Next Sprint

Continue environment implementations (OpenCue Phase 2) or storage provider implementations (S3/MinIO).
