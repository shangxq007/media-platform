# Render Request Idempotency v0

## Overview

Prevents duplicate renders for the same logical render request by checking for existing READY Products before starting a new render.

```text
TimelineRevisionRenderFacade.render()
    ↓
RenderDeduplicationService.check()
    ├── REUSE_READY_PRODUCT → return existing result
    ├── RETRY_AFTER_FAILURE → proceed with new render
    ├── PROCEED_NEW_RENDER → proceed with new render
    └── FAILED_CLOSED → throw IllegalStateException
    ↓
LEGACY or PLAN_BASED render path
```

## Domain Model

### RenderRequestFingerprint

Deterministic SHA-256 hash of render request identity:

- `projectId`
- `timelineRevisionId`
- `normalized output profile` (lowercase, trimmed, underscores)
- `execution mode` (LEGACY or PLAN_BASED)

Excludes: random UUID, timestamp, temp paths, job IDs, worker host, process environment, bucket/key/rootPath.

Format: `rfp-<24 hex chars>`

### RenderDeduplicationDecision

Decision from dedup check:

- `type` — PROCEED_NEW_RENDER, REUSE_READY_PRODUCT, RETRY_AFTER_FAILURE, FAILED_CLOSED
- `reason` — NO_EXISTING_RENDER, READY_PRODUCT_MATCH, FAILED_PREVIOUS_ATTEMPT, LOOKUP_ERROR
- `fingerprint` — the request fingerprint
- `reusedResult` — existing result to reuse (null if proceeding)

### RenderDeduplicationService

Checks for existing Products:

1. Queries `ProductRepository.findBySourceTimelineRevisionId(revisionId)`
2. Filters: same `projectId`, `productType == FINAL_RENDER`, `status == READY`
3. Matches `outputProfile` from `metadataJson`
4. If exact match found → REUSE_READY_PRODUCT
5. If failed previous found → RETRY_AFTER_FAILURE
6. If nothing found → PROCEED_NEW_RENDER

## READY Product Reuse

When an exact READY Product exists:

- Returns existing `RevisionRenderResult` with the existing `outputProductId`
- Does NOT create duplicate Product
- Does NOT create duplicate ProductDependency
- `renderMode` is set to `dedup-reuse`
- `renderJobId` is synthetic: `dedup-reuse-<productId>`

## In-Progress Detection

v0 limitation: No render job status table exists. In-progress detection is reserved for future work.

## Failed Render Retry

When a FAILED Product exists for the same revision:

- Allows new render attempt
- Old FAILED Product is not reused
- New render creates a new Product

## Integration

The `TimelineRevisionRenderFacade` calls `RenderDeduplicationService.check()` before routing to legacy or plan-based service. Dedup applies to both modes consistently.

## Public API Safety

- Fingerprint is internal only (not in request/response DTOs)
- No public idempotency key in v0
- `TimelineRevisionRenderRequest` has only `outputProfile` — no execution mode or dedup key
- Reused result has same `RevisionRenderResult` contract

## Known Limitations

- v0: No in-progress render detection (no job status table)
- v0: No distributed locking
- v0: No public idempotency key
- v0: Reused result has limited metadata (dimensions/duration not reconstructable from Product alone)
- v0: Output profile match uses simple JSON string extraction (no JSON parser)

## Architecture Compliance

- No DB migration required (uses existing `source_timeline_revision_id` column)
- No new public API fields
- No storage internals exposed
- No fingerprint in public surfaces
