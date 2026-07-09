# Timeline Diff MVP

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-DIFF-MVP.0

---

## Diff Service Classification

**Mode: STRUCTURAL/JSON PARTIAL**

The existing `TimelineRevisionDiffService` detects:
- ✅ Track-level changes (add/remove)
- ✅ Clip-level changes (add/remove)
- ✅ Asset-level changes (add/remove/modify)
- ❌ Text overlay changes (NOT DETECTED)
- ❌ Effect changes (NOT DETECTED)
- ❌ Subtitle changes (NOT DETECTED)

---

## Validation Results

### Pair 1: Asset change (revision 1 vs 10)

```json
{
  "entityChanges": [{"kind":"asset","entityId":"c1","action":"modified"}],
  "patchOpCount": 0
}
```

**Result:** ✅ Asset change detected

### Pair 2: Text overlay change (revision 11 vs 12)

```json
{
  "entityChanges": [],
  "patchOpCount": 0
}
```

**Result:** ❌ Text overlay change NOT detected

**Input:**
- Rev A: text="Hello A", fontSize=48, color=#FFFFFF, positionY=bottom
- Rev B: text="Hello B Changed", fontSize=64, color=#FF0000, positionY=top, startTime=0.5

---

## Compare API Contract

**Endpoint:** `GET /api/v1/render/projects/{projectId}/timeline/revisions/compare?from={revId}&to={revId}`

**Response:**
- `fromRevision` — source revision metadata
- `toRevision` — target revision metadata
- `summary` — track/clip/asset change counts
- `entityChanges` — list of changed entities
- `patchPaths` — RFC6902 patch paths
- `patchOpCount` — number of patch operations

---

## MVP Readiness Decision

**DIFF_PARTIAL_NEEDS_HARDENING**

The diff service detects structural changes (tracks, clips, assets) but not semantic changes (text overlays, effects, subtitles). This is sufficient for basic version tracking but insufficient for full semantic diff.

---

## Follow-up

- TIMELINE-SEMANTIC-DIFF-HARDENING.0 — Add text overlay/effect/subtitle diff detection
- TIMELINE-RESTORE-VALIDATION.0 — Validate revision restore
- TIMELINE-REVISION-PROVENANCE.0 — Add provenance metadata
