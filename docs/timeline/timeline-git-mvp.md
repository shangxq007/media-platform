# Timeline Git MVP

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-GIT-MVP.0

---

## Timeline Git MVP Contract

### MVP Scope

| Capability | Status | Endpoint |
|------------|--------|----------|
| Revision Model | ✅ COMPLETE | — |
| Revision API | ✅ COMPLETE | GET /, /head, /{id}, /{id}/snapshot |
| Semantic Diff | ✅ SEMANTIC_MVP_READY | GET /compare?from=&to= |
| Render Revision | ✅ COMPLETE | POST /{id}/render |
| Restore | ✅ COMPLETE | POST /{id}/restore |
| Provenance | ✅ COMPLETE | — |

### Excluded from MVP

| Feature | Status | Reason |
|---------|--------|--------|
| Merge | MERGE_EXPERIMENTAL | DB varchar(64) blocker |
| Patch Application | NOT IMPLEMENTED | — |
| Branch System | NOT IMPLEMENTED | — |
| ANTLR | NOT INTRODUCED | — |
| Artifact DAG | POSTPONED | — |
| OpenCue | NOT STARTED | — |
| RAW_MEDIA Product | DEFERRED | — |

---

## Stable API Contract

**Base Path:** `/api/v1/render/projects/{projectId}/timeline/revisions`

### GET /
List revisions for a project.

### GET /head
Get current head revision.

### GET /{revisionId}
Get revision metadata.

### GET /{revisionId}/snapshot
Get revision snapshot (internal timeline JSON).

### GET /compare?from={revId}&to={revId}
Compare two revisions with semantic diff.

**Response:**
- `fromRevision` / `toRevision` — revision metadata
- `summary` — track/clip/asset change counts
- `entityChanges` — structural changes
- `semanticDiff` — semantic changes with renderAffecting classification

**Supported semantic change types:**
- LAYER_CONTENT_CHANGED
- SUBTITLE_CUE_CHANGED
- CLIP_ADDED / CLIP_REMOVED
- ASSET_URI_CHANGED
- TRACK_ADDED / TRACK_REMOVED

### POST /{revisionId}/restore
Restore historical revision as new head.

**Semantics (Mode A — Revert-to-old-content):**
- Creates new revision
- parentRevisionId = current head
- contentHash = source revision
- Does not mutate history
- New revision becomes head

### POST /{revisionId}/render
Render selected revision.

**Response:**
- `renderJobId` / `timelineRevisionId` / `snapshotId`
- `outputProductId` / `productStatus` / `storageReferenceId`
- `mimeType` / `outputFormat` / `width` / `height` / `fps` / `durationSeconds`
- `hasSubtitles` / `baselineRenderer` / `renderMode`

### POST /merge — EXPERIMENTAL
Three-way merge. **NOT part of MVP.**

---

## Data Model Contract

| Object | Role |
|--------|------|
| TimelineRevision | Canonical timeline snapshot (immutable) |
| RenderJob | One execution attempt |
| Product/Artifact | Render output |
| contentHash | Canonical content identity |

**Key Rules:**
- TimelineRevision ≠ RenderJob ≠ Product
- Same contentHash can exist across different revisionIds (after restore)
- Generated FFmpeg/ASS are not canonical content
- Product is the canonical communication object

---

## Invariants

1. Historical revisions are immutable
2. Restore creates new revision, does not mutate history
3. Render selected revision uses requested revision, not implicit head
4. Semantic diff compares selected revisions
5. Provenance records revision identity
6. Merge is not part of MVP
7. Artifact DAG is not required
8. OpenCue is not required
9. Tenant/project safety is enforced

---

## Acceptance Checklist

### Revision API
- [x] List revisions works
- [x] Get revision works
- [x] Get head works
- [x] Get snapshot works

### Semantic Diff
- [x] Compare endpoint works
- [x] LAYER_CONTENT_CHANGED detected
- [x] SUBTITLE_CUE_CHANGED detected
- [x] CLIP_ADDED/REMOVED detected
- [x] ASSET_URI_CHANGED detected
- [x] TRACK_ADDED/REMOVED detected
- [x] renderAffecting classification exists

### Render
- [x] Selected revision render works
- [x] Output Product READY
- [x] mimeType video/mp4
- [x] resolution/fps/duration present
- [x] hasSubtitles true for subtitle fixture
- [x] renderer ffmpeg-libass
- [x] renderMode timeline-revision-render

### Restore
- [x] Restore old revision creates new revision
- [x] parentRevisionId = current head
- [x] contentHash = source revision
- [x] Restored revision becomes head
- [x] History not mutated
- [x] A→C diff no content changes
- [x] B→C diff shows semantic changes

### Provenance
- [x] timelineRevisionId present
- [x] renderJobId present
- [x] outputProductId present
- [x] renderMode present
- [x] baselineRenderer present
- [x] productStatus present

### Safety
- [x] Tenant context enforced
- [x] No raw local paths exposed
- [x] No generated FFmpeg exposed
- [x] No generated ASS exposed
- [x] No secrets exposed

---

## Frontend/Dev Console Handoff

### May Use
- List revisions
- Get head
- Get snapshot
- Compare revisions
- Render selected revision
- Restore old revision

### Must Not Rely On
- Merge as stable behavior
- Patch application
- Branch
- Artifact DAG
- OpenCue
- RAW_MEDIA Product-backed materialization

### Recommended UX
- Revision history list
- Selected revision preview/render button
- Diff view using semantic changes
- Restore button with confirmation
- Provenance panel for rendered output

---

## Evidence

| Task | Commit | Doc |
|------|--------|-----|
| REVISION-MODEL | 54ab044 | timeline-revision-model.md |
| REVISION-API | 942f7e2 | timeline-revision-api.md |
| RENDER-BY-REVISION | 7cbce09 | timeline-render-by-revision-media-ref-fix.md |
| DIFF-MVP | dffd721 | timeline-diff-mvp.md |
| SEMANTIC-DIFF | 43d2350 | timeline-semantic-diff-hardening.md |
| RESTORE | 0f1ec5a | timeline-restore-validation.md |
| MERGE | 8faec10 | timeline-merge-classification.md |
| PROVENANCE | e634c6e | timeline-revision-provenance.md |
