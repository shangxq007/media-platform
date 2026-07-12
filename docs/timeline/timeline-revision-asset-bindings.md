# Timeline Revision Asset Bindings

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-REVISION-ASSET-BINDINGS.0

---

## Implementation

### Model Changes

| Component | Change |
|-----------|--------|
| TimelineAssetRef | ✅ Added `productId` field |
| TimelineInputProductResolver | ✅ Added `resolveWithBindings` method |
| TimelineRevisionRenderService | ✅ Extracts bindings from spec |
| RenderController | ✅ Creates RAW_MEDIA Product on upload |

### Binding Model

```
assetId (timeline-local) → productId (RAW_MEDIA Product)
```

**Example:** `media_1783579669520 → prod_xxx`

### Resolution Order

1. **Explicit binding** — `clip.assetRef.productId` → Product lookup
2. **Direct asset ID** — `findByAsset(assetId)` → Product lookup
3. **URI-backed fallback** — `storageUri` from revision snapshot
4. **Safe failure** — No media source found

---

## Current Status

| Check | Result |
|-------|--------|
| Asset ID matches Product | ✅ `media_xxx` used as both |
| Product found | ✅ |
| Product status READY | ✅ (REGISTERED → markReady) |
| Product has storageReferenceId | ❌ NEEDS FIX |
| URI-backed fallback | ✅ PRESERVED |

## Blocker

Product created without `storageReferenceId`. Materialization fails:
"Input Product has no storageReferenceId"

**Fix needed:** Create StorageReference when uploading media, or use URI fallback when Product has no storageReferenceId.

---

## Status

- TIMELINE-REVISION-ASSET-BINDINGS.0: PARTIAL
- Binding model: IMPLEMENTED
- Product creation: PARTIAL (needs storageReferenceId)
- URI fallback: PRESERVED
