# Timeline Merge Classification

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-MERGE-CLASSIFICATION.0

---

## Merge Endpoint

**Path:** `POST /api/v1/render/projects/{projectId}/timeline/revisions/merge`

**Request:**
- `baseRevisionId` — common ancestor
- `sourceRevisionId` — left branch
- `targetRevisionId` — right branch
- `message` — merge message
- `resolutions` — conflict resolutions (USE_SOURCE/USE_TARGET)

**Response:**
- `status` — MERGED/CONFLICTS/FAILED
- `mergedRevisionId` — new revision if merged
- `conflicts` — conflict list if CONFLICTS
- `mergeSummary` — auto-merge stats

## Implementation

**TimelineMergeService:** Full three-way merge with:
- `TimelineSemanticDiffService` for base→source and base→target diffs
- `TimelineConflictDetector` for conflict detection
- `TimelineConflictResolver` for conflict resolution
- Auto-merge of non-conflicting changes

## Validation Result

| Test | Result |
|------|--------|
| Three-way merge | ⚠️ DB error (varchar(64) too long) |
| Conflict detection | ✅ EXISTS |
| Resolution intents | ✅ EXISTS |
| History mutation | ✅ NO |

## Classification

**MERGE_EXPERIMENTAL**

The merge implementation is sophisticated (three-way merge with conflict detection) but has a database constraint bug. The architecture is sound but needs the varchar fix before it can be used.

---

## Status

- TIMELINE-MERGE-CLASSIFICATION.0: COMPLETE
- Merge classification: MERGE_EXPERIMENTAL
- Merge in Timeline Git MVP: NO (needs varchar fix)
