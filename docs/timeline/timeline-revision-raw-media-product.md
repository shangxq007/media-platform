# Timeline Revision RAW_MEDIA Product Materialization

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-REVISION-RAW-MEDIA-PRODUCT.0

---

## Summary

Product-backed RAW_MEDIA resolution is implemented but asset ID mapping needs alignment.

### Current Resolution Order

1. **PRODUCT_BACKED** — Try READY RAW_MEDIA Product by asset ID
2. **URI_BACKED_PREVIEW** — Fallback to storageUri from revision snapshot
3. **Safe failure** — No media source found

### Implementation

| Component | Status |
|-----------|--------|
| Upload creates RAW_MEDIA Product | ✅ |
| Product-backed resolver | ✅ EXISTS |
| URI-backed fallback | ✅ PRESERVED |
| Asset ID mapping | ⚠️ NEEDS ALIGNMENT |

### Issue

- Revision stores asset ID as `c1` (from internal timeline)
- RAW_MEDIA Product created with `media_xxx` as asset ID
- `TimelineInputProductResolver` looks up by asset ID
- Mismatch causes Product lookup to fail, falls back to URI

### Render Result

```
productStatus: READY
mimeType: video/mp4
resolution: 1920x1080
fps: 30
duration: 3.0s
hasSubtitles: true
renderer: ffmpeg-libass
renderMode: timeline-revision-render
mediaResolutionMode: URI_BACKED_PREVIEW (fallback)
```

---

## Status

- TIMELINE-REVISION-RAW-MEDIA-PRODUCT.0: PARTIAL
- Product-backed resolution: IMPLEMENTED
- URI-backed fallback: PRESERVED
- Asset ID alignment: NEEDS WORK
