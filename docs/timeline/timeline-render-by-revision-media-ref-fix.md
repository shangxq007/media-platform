# Timeline Render by Revision - Media Reference Fix

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-RENDER-BY-REVISION-MEDIA-REF-FIX.0

---

## Summary

Render-by-revision is now working end-to-end.

### Fixes Applied (6 iterations)

| Fix | Issue | Solution |
|-----|-------|----------|
| TenantContext | JWT not parsed | Extract tenantId from JWT |
| JwtAuthFilter | Missing prefix | Add `/api/v1/render/projects` |
| InternalTimelineAdapter | Wrong parser | Use adapter for internal format |
| jOOQ DSLContext | Null injection | @Autowired on constructors |
| Media resolution | No RAW_MEDIA Product | URI-based fallback |
| Timestamp type | OffsetDateTime cast | jOOQ DSL API + type handling |

### Render Result

```
renderJobId: rj_9ef912bf9d7d49a4b2a8a7f4999fb416
productStatus: READY
mimeType: video/mp4
width: 1920, height: 1080, fps: 30
duration: 3.0s
hasSubtitles: true
baselineRenderer: ffmpeg-libass
renderMode: timeline-revision-render
message: Timeline revision rendered successfully
```

### Architecture Notes

- Revision render uses Product output (not RenderJob→Artifact)
- Media resolution: Product-backed preferred, URI-based fallback for preview
- Product remains canonical communication object
- URI-based rendering is preview/bootstrap only

### Files Changed

- TenantContextFilter.java
- TenantContextFilterConfig.java
- JwtAuthFilter.java
- TimelineRevisionRenderService.java
- ProductRepository.java
- ProductDependencyRepository.java
- StorageReferenceRepository.java

---

## Status

- TIMELINE-RENDER-BY-REVISION-MEDIA-REF-FIX.0: COMPLETE
- Render-by-revision: WORKING
- Selected revision semantics: VERIFIED
- Inline render regression: PASS
