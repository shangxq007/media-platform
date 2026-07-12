# Timeline Revision Asset Product Mapping

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-REVISION-ASSET-PRODUCT-MAPPING.0

---

## Identity Model

| Identity | Example | Scope |
|----------|---------|-------|
| Timeline-local asset ID | `c1` | Within TimelineRevision |
| RAW_MEDIA Product ID | `media_xxx` | Product system |

**Key Rule:** assetId ≠ productId. They are different identities.

## Current Resolution Order

1. **Direct asset ID lookup** — `productRuntime.findByAsset(sourceAssetId)`
2. **URI-backed fallback** — Use storageUri from revision snapshot
3. **Safe failure** — No media source found

## Current Issue

- Upload creates RAW_MEDIA Product with `mediaId` (e.g., `media_1783575740157`) as `owner_asset_id`
- Revision stores `c1` as the timeline-local asset ID
- `TimelineInputProductResolver` looks up by `c1`, doesn't find Product
- Falls back to URI-backed preview

## Design Decision

**OpenAssetIO is NOT introduced.** Current problem is internal TimelineRevision asset binding to Product ID. OpenAssetIO is for external DCC/studio asset manager integration.

## Recommended Fix

Add explicit asset binding: `c1 → media_xxx`

Possible locations:
- Revision snapshot `assetBindings`
- TimelineSpec `assets` block
- Clip `mediaRef.productId`
- Product metadata reverse mapping

## Status

- TIMELINE-REVISION-ASSET-PRODUCT-MAPPING.0: PARTIAL
- Asset ID is timeline-local: YES
- Explicit binding supported: NO (needs implementation)
- URI-backed fallback: PRESERVED
