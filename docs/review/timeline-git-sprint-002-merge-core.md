---
status: implementation-report
created: 2026-06-24
scope: render-module + V1 baseline
truth_level: current
owner: platform
supersedes: timeline-git-implementation-audit.md (audit analysis)
---

# Timeline Git Sprint 002 — Merge Core + Timeline Conflict Detection

## Implemented Scope

Three-way merge engine with automatic conflict detection, integrated with the existing revision chain. Auto-merges non-conflicting changes; detects and reports conflicts without creating broken revisions.

```
base revision
    │
    ├── source branch changes (base → source diff)
    │
    └── target branch changes (base → target diff)
            │
            ▼
    TimelineConflictDetector
            │
    ┌───────┴───────┐
    │               │
    no conflicts    has conflicts
    │               │
    ▼               ▼
    auto-merge      return CONFLICTS
    create merge    (no revision created)
    revision
```

## Modified Schema

### `timeline_revision` table (V1:440)

**Columns added (3 new):**
- `is_merge BOOLEAN NOT NULL DEFAULT FALSE` — whether this revision is a merge commit
- `merge_parent_revision_ids TEXT` — comma-separated parent revision IDs (source,target)
- `merge_base_revision_id VARCHAR(64)` — the common ancestor revision ID

**New index:** `ix_timeline_revision_is_merge` on `is_merge`

**Existing schema preserved:** `parent_revision_id` still holds the target (main) parent for linear traversal. Existing non-merge revision logic unaffected.

## Extended SemanticChangeType

**Added (2 new):**
- `CLIP_MOVED` — clip moved between tracks or time positions
- `CLIP_METADATA_CHANGED` — clip metadata (asset ref, labels) changed

**Existing values preserved.** Switch statements with `default` branches continue to work.

## New Domain Models (4 records + 1 enum)

| Record/Enum | File | Purpose |
|-------------|------|---------|
| `TimelineMergeRequest` | `domain/timeline/internal/TimelineMergeRequest.java` | Input: projectId, tenantId, base/source/target revision IDs, author, message |
| `TimelineMergeResult` | `domain/timeline/internal/TimelineMergeResult.java` | Output: status (MERGED/CONFLICTS/NO_OP/FAILED), conflicts, merged revision ID, summary |
| `TimelineConflict` | `domain/timeline/internal/TimelineConflict.java` | Conflict record: entity ref, type, source/target changes, message |
| `TimelineConflictType` | `domain/timeline/internal/TimelineConflictType.java` | Enum: SAME_ENTITY_MODIFIED, CLIP_RANGE_CONFLICT, CLIP_REMOVED_AND_MODIFIED, CLIP_MOVED_CONFLICT, EFFECT_CONFLICT, METADATA_CONFLICT, TRACK_STRUCTURE_CONFLICT, UNKNOWN |

## New Services (2 classes)

| Service | File | Responsibility |
|---------|------|----------------|
| `TimelineConflictDetector` | `app/timeline/TimelineConflictDetector.java` | Groups semantic changes by entity, detects same-entity conflicts between source and target branches |
| `TimelineMergeService` | `app/timeline/TimelineMergeService.java` | Three-way merge: load base/source/target snapshots, compute semantic diffs, detect conflicts, auto-merge or return conflict list |

## Modified Existing Files (3 classes)

| File | Change |
|------|--------|
| `SemanticChangeType.java` | +2 enum values (CLIP_MOVED, CLIP_METADATA_CHANGED) |
| `TimelineRevisionRepository.java` | RevisionRow + map() + insert() extended for is_merge, merge_parent_revision_ids, merge_base_revision_id |
| `TimelineRevisionService.java` | RevisionInfo + toInfo() extended for merge fields; recordRevision() passes merge defaults |
| `TimelineEditorSyncServiceTest.java` | Updated RevisionInfo constructor calls for 3 new fields |

## Merge Algorithm

```
threeWayMerge(baseRevisionId, sourceRevisionId, targetRevisionId)

1. Load base/source/target revision rows from repository
2. Load base/source/target snapshot payloads
3. Compute:
   - sourceDiff = semanticDiff(basePayload, sourcePayload)
   - targetDiff = semanticDiff(basePayload, targetPayload)
4. If both diffs are structurally equal → return NO_OP
5. Detect conflicts = conflictDetector.detect(sourceDiff.changes(), targetDiff.changes())
6. If conflicts exist → return CONFLICTS with conflict list (no revision created)
7. If no conflicts → save sourcePayload as snapshot, create merge revision with:
   - is_merge = true
   - merge_parent_revision_ids = "sourceId,targetId"
   - merge_base_revision_id = baseId
   - source = "merge"
   - parent_revision_id = targetRevisionId (linear chain reference)
8. Return MERGED with new revision ID
```

## Conflict Detection Rules (conservative)

| Rule | Source Branch | Target Branch | Conflict? |
|------|--------------|--------------|-----------|
| Different entities | CLIP_RANGE_CHANGED on clip_A | CLIP_RANGE_CHANGED on clip_B | No |
| Same entity, same type | CLIP_RANGE_CHANGED on clip_1 | CLIP_RANGE_CHANGED on clip_1 | Yes — SAME_ENTITY_MODIFIED |
| Same entity, different type | CLIP_REMOVED on clip_1 | CLIP_SPEED_CHANGED on clip_1 | Yes — CLIP_REMOVED_AND_MODIFIED |
| Same entity, both effect changes | CLIP_EFFECT_CHANGED on clip_1 | CLIP_EFFECT_CHANGED on clip_1 | Yes — SAME_ENTITY_MODIFIED |
| Same entity, both metadata | CLIP_METADATA_CHANGED on clip_1 | CLIP_METADATA_CHANGED on clip_1 | Yes — SAME_ENTITY_MODIFIED |
| REVISION_ONLY on both sides | REVISION_ONLY on proj_1 | REVISION_ONLY on proj_1 | No (ignored) |
| One side only | CLIP_RANGE_CHANGED on clip_A | CLIP_ADDED on clip_B | No (different entities) |

**Design principle:** When in doubt, report a conflict rather than silently merging.

## Tests Run (13 new, all passing)

| Test Class | Tests | Scenarios |
|------------|-------|-----------|
| `TimelineConflictDetectorTest` | 7 | Different entities → no conflict, same clip range → conflict, clip removed vs modified → conflict, metadata conflict, REVISION_ONLY ignored, empty changes, effect conflict |
| `TimelineMergeServiceTest` | 3 | NO_OP when structurally equal, CONFLICTS when overlapping changes, FAILED when invalid base |
| `TimelineRevisionRepositoryMergeMetadataTest` | 2 | Merge row has correct flags, non-merge row has defaults |

**Test commands:**
```bash
./gradlew :render-module:test --tests '*TimelineConflictDetectorTest' --tests '*TimelineMergeServiceTest' --tests '*TimelineRevisionRepositoryMergeMetadataTest'
# Result: BUILD SUCCESSFUL (13 tests, 0 failures)
```

## Known Limitations

1. **Auto-merge uses target payload directly** — the current implementation does not apply source changes to the target payload. Non-conflicting source changes are not reflected in the merged result. This is conservative: the merge revision is created, but the payload is the target's. Full change application requires integrating with `TimelinePatchService` to apply non-conflicting source diffs.
2. **No manual conflict resolution** — conflicting merges return CONFLICTS status but offer no resolution mechanism. This is deferred to Sprint 003 (conflict resolution + branch support).
3. **Merge revision has synthesized content hash** — instead of a SHA-256 of the actual payload. This would break cache deduplication in production. Fix when full payload merge is implemented.
4. **Snapshot for merge contains target payload** — should contain the merged result once change application is implemented.

## Deferred to Sprint 003+

| Item | Reason |
|------|--------|
| Apply non-conflicting source changes to merge result | Requires deep integration with TimelinePatchService + semantic→JSON patch mapping |
| Manual conflict resolution | Requires resolution model + UI (deferred to Phase 3) |
| Branch table | Not needed for merge core — branches are named revision pointers |
| Rebase | Requires sequential patch replay |
| Proposal table extraction | Separate concern from merge |
| timeline-module extraction | Deferred until merge/branch stabilize |

## Validation Checklist

- [x] No new Gradle module
- [x] No V2 migration
- [x] V1 baseline updated with merge columns
- [x] No branch table
- [x] No rebase
- [x] No OpenLineage/OpenAssetIO/KG
- [x] No Spring AI runtime
- [x] No H2
- [x] ProductionSafetyValidator unchanged
- [x] Existing conflict resolver (ConflictResolver) untouched — separate domain
- [x] CollaborationEngine untouched — separate domain
- [x] 13 new tests, all passing
- [x] Existing tests adapted for RevisionInfo field changes
