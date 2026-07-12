# Timeline Render by Revision

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-RENDER-BY-REVISION.0
**Implementation mode:** EXISTING_ENDPOINT_VALIDATED

---

## Background

TIMELINE-REVISION-API.0 confirmed the API exists. This task validates render-by-revision.

---

## Endpoint

```
POST /api/v1/render/projects/{projectId}/timeline/revisions/{revisionId}/render
```

**Controller:** TimelineRevisionController
**Service:** TimelineRevisionRenderService

---

## Validation Status

| Check | Status |
|-------|--------|
| Endpoint exists | ✅ |
| Controller registered | ✅ |
| Service exists | ✅ |
| Tenant context issue | ⚠️ BLOCKING |

### Issue

Timeline Revision API requires tenant context from JWT, but dev token tenant context is not being properly set for revision endpoints. This is likely a security configuration issue, not a revision model issue.

---

## Existing Infrastructure

| Component | Status |
|-----------|--------|
| TimelineRevisionController | ✅ EXISTS |
| TimelineRevisionRenderService | ✅ EXISTS |
| TimelineRevisionService | ✅ EXISTS |
| TimelineRevisionRepository | ✅ EXISTS |
| Render revision endpoint | ✅ EXISTS |
| Artifact output path | ✅ VERIFIED (via inline timeline) |

---

## Inline Timeline Render (Verified)

The inline timeline render path works:

```
POST /api/v1/render/jobs/submit
  → TimelineScriptParser
  → FFmpeg provider
  → COMPLETED
  → Artifact metadata
  → Content download
```

This path uses the same FFmpeg provider that revision render would use.

---

## Remaining Work

1. Fix tenant context for revision API endpoints
2. Verify render-by-revision produces correct artifact
3. Verify provenance includes timelineRevisionId

---

## Follow-up

- TIMELINE-RENDER-BY-REVISION-FIX.0 (fix tenant context)
- TIMELINE-DIFF-MVP.0
- TIMELINE-REVISION-PROVENANCE.0
