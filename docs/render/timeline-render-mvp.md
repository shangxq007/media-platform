# Timeline Render MVP

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** TIMELINE-RENDER-MVP.0

---

## Background

REAL-MEDIA-INPUT.0 is COMPLETE. The existing infrastructure already supports TimelineRevision-based rendering through the submit endpoint's `prompt` field.

---

## MVP Scope

### Included

- One TimelineRevision (inline JSON)
- One video track
- One real media clip
- Asset reference via storageUri
- FFmpeg preview/bootstrap provider
- Artifact output
- Artifact content access

### Excluded

- Multi-track editing
- Effects/transitions
- Captions
- Timeline Git
- Artifact DAG
- OpenCue
- ANTLR
- Complex trim (basic only)

---

## Timeline JSON Shape

```json
{
  "version": "1.0",
  "tracks": [
    {
      "id": "v1",
      "type": "video",
      "clips": [
        {
          "id": "clip_001",
          "assetRef": {
            "storageUri": "localFsStorageProvider://preview-media/media_xxx/input.mp4"
          },
          "assetInPoint": 0,
          "clipDuration": 3
        }
      ]
    }
  ]
}
```

---

## Render Flow

```
Upload media → POST /api/v1/preview/media → storageUri
Submit timeline → POST /api/v1/render/jobs/submit → jobId
Poll status → GET /api/v1/render/jobs/{jobId} → COMPLETED
Get artifacts → GET /api/v1/render/jobs/{jobId}/artifacts → artifactId
Download → GET /api/v1/render/jobs/{jobId}/artifacts/{artifactId}/content → MP4
```

---

## Test Results

| Test | Result |
|------|--------|
| Upload real media (640x360, 3s) | ✅ 38775 bytes |
| Timeline render (1 video clip) | ✅ COMPLETED |
| Output artifact | ✅ 9545 bytes, MP4 |
| Content download | ✅ HTTP 200 |
| Synthetic render regression | ✅ COMPLETED |

### Example

```
Job: rj_26a21fb01a21469cb19e6d556d364466
Artifact: art_ca1607a64eb249b195c6cb928314b31b
Output: 9545 bytes, MP4
```

---

## Existing Models Used

| Model | Location | Description |
|-------|----------|-------------|
| TimelineSpec | render-module domain | Canonical timeline representation |
| TimelineTrack | render-module domain | Track with type/layer/clips |
| TimelineClip | render-module domain | Clip with assetRef/timing |
| TimelineAssetRef | render-module domain | Asset reference (storageUri) |
| TimelineScriptParser | render-module domain | Parse timeline JSON |
| FFmpegRenderProvider | render-module infrastructure | FFmpeg execution |

---

## Limitations

- Single clip only
- Single video track only
- No effects/transitions
- No captions
- No Timeline Git
- No OpenCue
- No Artifact DAG
- FFmpeg runs in platform-api preview/bootstrap path only

---

## Follow-ups

- BASIC-EFFECTS-DSL.0
- FFMPEG-FILTERGRAPH-COMPILER.0
- SUBTITLE-DSL-ASS.0
- TIMELINE-GIT-PLANNING.0
- RENDER-WORKER-FFMPEG.0
