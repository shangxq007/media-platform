# Timeline Semantic Diff Hardening

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-SEMANTIC-DIFF-HARDENING.0

---

## Summary

Compare API now returns semantic diff with effect/text/subtitle change detection.

### Diff Classification

**Previous:** STRUCTURAL_JSON_PARTIAL
**New:** SEMANTIC_MVP_READY

### Detected Change Types

| Change Type | Entity Kind | Render Affecting |
|-------------|-------------|------------------|
| LAYER_CONTENT_CHANGED | LAYER | ✅ |
| SUBTITLE_CUE_CHANGED | SUBTITLE_TRACK | ✅ |
| CLIP_ADDED | CLIP | ✅ |
| CLIP_REMOVED | CLIP | ✅ |
| ASSET_URI_CHANGED | ASSET | ✅ |
| TRACK_ADDED | TRACK | ✅ |
| TRACK_REMOVED | TRACK | ✅ |

### API Response

```json
{
  "semanticDiff": {
    "supported": true,
    "structurallyEqual": false,
    "changeCount": 2,
    "changes": [
      {
        "changeType": "LAYER_CONTENT_CHANGED",
        "entityKind": "LAYER",
        "entityId": "layer_sub_imported",
        "description": "layer changed",
        "renderAffecting": true
      }
    ]
  }
}
```

### Files Changed

- `TimelineRevisionService.java` — Added SemanticDiffService
- `TimelineRevisionController.java` — Exposed SemanticDiffDto

---

## Status

- TIMELINE-SEMANTIC-DIFF-HARDENING.0: COMPLETE
- Diff mode: SEMANTIC_MVP_READY
- MVP readiness: DIFF_MVP_READY
