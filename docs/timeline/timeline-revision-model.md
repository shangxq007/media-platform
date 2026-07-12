# Timeline Revision Model

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-REVISION-MODEL.0
**Implementation mode:** EXISTING_INFRASTRUCTURE

---

## Background

TIMELINE-GIT-PLANNING.0 defined the target model. Existing infrastructure already implements most of it.

---

## Existing Model

### RevisionInfo Record

| Field | Type | Description |
|-------|------|-------------|
| id | string | Revision ID |
| projectId | string | Project scope |
| tenantId | string | Tenant scope |
| parentRevisionId | string | Previous revision |
| revisionNumber | int | Sequential per project |
| snapshotId | string | Timeline snapshot reference |
| internalRevision | int | Optimistic lock |
| contentHash | string | Content hash |
| schemaVersion | string | Spec schema version |
| source | string | Creation source |
| authorUserId | string | Author identity |
| editSessionId | string | Edit session |
| message | string | Commit message |
| labels | List | Revision labels |
| changeSummaryJson | string | Change summary |
| patchOpsJson | string | Patch operations |
| isMerge | boolean | Merge commit flag |
| mergeParentRevisionIds | string | Merge parents |
| mergeBaseRevisionId | string | Merge base |
| createdAt | string | Creation time |

### TimelineRevisionRef

Validates revision references, forbids sensitive keywords.

### TimelineRevisionRepository

- findHeadByProject
- findById
- record revision
- revision_number sequence

### TimelineRevisionService

- recordRevision
- findHead
- findById
- loadTimelineJson

### TimelineRevisionDiffService

- diff between revisions

---

## Alignment with Planning

| Planned Field | Existing Field | Status |
|---------------|----------------|--------|
| revisionId | id | ✅ |
| timelineId | projectId (scope) | ✅ |
| tenantId | tenantId | ✅ |
| projectId | projectId | ✅ |
| parentRevisionId | parentRevisionId | ✅ |
| revisionNumber | revisionNumber | ✅ |
| message | message | ✅ |
| authorId | authorUserId | ✅ |
| source | source | ✅ |
| timelineSpecJson | snapshotId → payload | ✅ |
| timelineSpecHash | contentHash | ✅ |
| schemaVersion | schemaVersion | ✅ |
| createdAt | createdAt | ✅ |
| validationStatus | — | DEFERRED |
| renderable | — | DEFERRED |
| authorType | — | DEFERRED |

---

## Immutability

- Revisions are immutable after creation
- New edits create new revisions
- parentRevisionId links to previous
- revisionNumber is sequential

---

## Summary

The TimelineRevision model already exists with:
- ✅ Immutable snapshots
- ✅ Parent linkage
- ✅ Commit metadata
- ✅ Content hash
- ✅ Schema version
- ✅ Diff service
- ✅ Repository with sequence

No new code needed. Model is ready for Timeline Git MVP.
