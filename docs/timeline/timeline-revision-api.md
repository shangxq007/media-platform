# Timeline Revision API

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-REVISION-API.0
**Implementation mode:** EXISTING_API_VALIDATED

---

## Background

TIMELINE-REVISION-MODEL.0 confirmed the model exists. The API already exists.

---

## Existing API

**Base path:** `/api/v1/render/projects/{projectId}/timeline/revisions`

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | List revisions |
| GET | `/{revisionId}` | Get revision |
| GET | `/{revisionId}/snapshot` | Get revision snapshot |
| GET | `/compare` | Compare/diff revisions |
| GET | `/head` | Get head revision |
| GET | `/facets` | Get revision facets |
| GET | `/edit-sessions` | Get edit sessions |
| POST | `/{revisionId}/restore` | Create child revision |
| POST | `/merge` | Merge revisions |
| POST | `/{revisionId}/render` | Render revision |
| PATCH | `/{revisionId}/annotation` | Update annotation |
| GET | `/{revisionId}/patch-preview` | Preview patch |
| GET | `/{revisionId}/patch-steps` | Get patch steps |
| GET | `/{revisionId}/render-jobs/{jobId}` | Get render job |
| GET | `/{revisionId}/render-jobs/{jobId}/result` | Get render result |

### Controller

`TimelineRevisionController` in `platform-app/web/render`

### Services Used

- TimelineRevisionService
- TimelineRevisionDiffService
- TimelineRevisionRenderService
- TimelineMergeService

---

## Security

- ✅ No raw FFmpeg args exposed
- ✅ No generated ASS exposed
- ✅ No local filesystem paths exposed
- ✅ Tenant/project scoping enforced

---

## Summary

The Timeline Revision API already provides:
- ✅ Create revision (via restore)
- ✅ List revisions
- ✅ Get revision
- ✅ Diff/compare revisions
- ✅ Render revision
- ✅ Merge revisions
- ✅ Patch preview

No new code needed.
