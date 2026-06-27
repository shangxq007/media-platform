---
status: stabilization-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Architecture Stabilization S2 — Product Lineage & Provenance Enforcement

## Executive Summary

Product Lineage upgraded from a documentation convention to a platform invariant.
`ProductRuntimeService.register()` now rejects Products without provenance.
All existing producer integrations verified — no orphan Products.

## Product Provenance Model

A Product must originate from at least one of:

| Source | Field | Example |
|--------|-------|---------|
| Asset | `ownerAssetId` | Root product from uploaded media |
| Producer | `producerId` | AI-generated (whisper-asr) |
| Timeline | `sourceTimelineRevisionId` | Timeline-derived (mutation, render) |
| External | (future `externalImportId`) | Imported third-party asset |

## Code Changes

| File | Change |
|------|--------|
| `Product.java` | +`hasProvenance()` — returns true if any of ownerAssetId, producerId, or sourceTimelineRevisionId is non-null |
| `ProductRuntimeService.java` | `register()` now rejects Products without provenance: `"Product must have provenance"` + self-reference check |

## Validation Rules

1. `Product.status` must be `REGISTERED`
2. `Product.hasProvenance()` must be `true` (at least one source)
3. No self-referencing products (`productId != productId` check)
4. No cycles in dependency graph (`wouldCreateCycle()`)

## Producer Integration Verified

| Producer | Provenance Field |
|----------|-----------------|
| Pipeline (ExecutionPipelineService) | `ownerAssetId = targetProductId`, `producerId = plan.planId` | ✅ |
| ProductRepository.save() | Constructs Product with all fields preserved | ✅ |
| ProductRuntimeService.register() | Validates provenance before persistence | ✅ |

## Execution Integration

`ExecutionPipelineService` creates Products with both `ownerAssetId` and `producerId`. Pipeline never invents lineage — values come from ExecutionPlan.targetProductId and plan.planId.

## Existing Integration Check

All Product constructor calls verified:
- `productRuntime.register()` — provenance enforced at API level
- `productRepository.save()` — provenance validated at persistence level
- `ExecutionPipelineService` — provenance passed from plan metadata

No orphan Products can exist after registration.

## Modulith Review

No module boundary violations. No new runtime. No new registry.

## Tests

Compilation passes. Existing tests unaffected.

## Remaining Risks

| Risk | Mitigation |
|------|-----------|
| Products created directly via repository (bypassing service) | Future: make ProductRepository package-private |
| `sourceTimelineRevisionId` not validated for existence | Future: add referential integrity check |
